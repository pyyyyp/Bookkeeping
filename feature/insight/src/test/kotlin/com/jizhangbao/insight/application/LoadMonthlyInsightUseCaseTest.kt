package com.jizhangbao.insight.application

import com.jizhangbao.core.domain.CategoryAmount
import com.jizhangbao.core.domain.CategoryBreakdown
import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.MonthlyTotals
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.toTimeRange
import com.jizhangbao.insight.testing.FakeLedgerTotalsReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * 一次快照用例（`T-022`）—— 它取代了四个用例，所以这里要同时守住两件事：
 *
 * 1. **本卡的目的**：每段区间只问一次（原来九次里有三次重复）；
 * 2. **原四个用例的断言一条都不能丢**：口径同源、顺序、空月、失败行为。
 *
 * 第 2 条更重要 —— 重构最容易出的错不是"没优化"，而是**悄悄丢了某条规则**。
 */
class LoadMonthlyInsightUseCaseTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), zone)
    private val reader = FakeLedgerTotalsReader()
    private val useCase = LoadMonthlyInsightUseCase(reader, clock)

    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)
    private val august = YearMonth.of(2026, 8)
    private val july = YearMonth.of(2026, 7)
    private val june = YearMonth.of(2026, 6)
    private val may = YearMonth.of(2026, 5)

    private fun totalsOf(expenseCents: Long) =
        MonthlyTotals(income = Money.ZERO, expense = Money.ofCents(expenseCents))

    private fun rangeOf(month: YearMonth) = month.toTimeRange(zone)

    // ---------- 1. 本卡的目的：每段区间只问一次 ----------

    @Test
    fun `六段区间各问一次_没有重复`() = runBlocking {
        useCase(october)

        // 这正是重构的目的：以前是九次（十月三次、九月两次），现在六段各一次
        assertEquals(
            listOf(rangeOf(october), rangeOf(september), rangeOf(august), rangeOf(july), rangeOf(june), rangeOf(may)),
            reader.requestedRanges,
        )
        assertEquals(6, reader.requestedRanges.distinct().size)
        // 占比是另一条查询，同样只问一次
        assertEquals(listOf(rangeOf(october)), reader.requestedBreakdownRanges)
    }

    @Test
    fun `六段区间全部走内核的月份口径`() = runBlocking {
        useCase(october)

        // BR-1：与合计/下钻/环比/趋势同一个函数算出来的区间
        val expected = (0 until 6).map { back -> rangeOf(october.minusMonths(back.toLong())) }
        assertEquals(expected, reader.requestedRanges)
    }

    // ---------- 2. 原四个用例的断言 ----------

    @Test
    fun `合计就是当前月的合计`() = runBlocking {
        reader.outcomeByRange = { range ->
            if (range == rangeOf(october)) Outcome.Ok(totalsOf(40_045)) else Outcome.Ok(MonthlyTotals.ZERO)
        }

        val insight = (useCase(october) as Outcome.Ok).value

        assertEquals(Money.ofCents(40_045), insight.totals.expense)
        // 占比与合计来自**同一个月**（REQ-005/AC-7：同屏两个数字不能一个月一个数）
        assertEquals(rangeOf(october), reader.requestedBreakdownRanges.single())
    }

    @Test
    fun `环比用同一个本月的合计_不重新算一遍`() = runBlocking {
        reader.outcomeByRange = { range ->
            when (range) {
                rangeOf(october) -> Outcome.Ok(totalsOf(40_045))
                rangeOf(september) -> Outcome.Ok(totalsOf(1_000))
                else -> Outcome.Ok(MonthlyTotals.ZERO)
            }
        }

        val insight = (useCase(october) as Outcome.Ok).value

        // 39_045 = 40_045 − 1_000
        assertEquals(39_045L, insight.comparison.expenseDelta.cents)
        assertEquals(insight.totals, insight.comparison.current)
    }

    @Test
    fun `趋势六行从新到旧_最新一行就是合计`() = runBlocking {
        reader.outcomeByRange = { range ->
            if (range == rangeOf(october)) Outcome.Ok(totalsOf(40_045)) else Outcome.Ok(MonthlyTotals.ZERO)
        }

        val insight = (useCase(october) as Outcome.Ok).value

        assertEquals(listOf(october, september, august, july, june, may), insight.trend.points.map { it.month })
        assertEquals(insight.totals, insight.trend.latest)
    }

    @Test
    fun `没有记账的月份是零_不是缺项`() = runBlocking {
        reader.outcomeByRange = { range ->
            if (range == rangeOf(august)) Outcome.Ok(totalsOf(12_345)) else Outcome.Ok(MonthlyTotals.ZERO)
        }

        val insight = (useCase(october) as Outcome.Ok).value

        assertEquals(6, insight.trend.points.size)
        assertEquals(
            Money.ofCents(12_345),
            insight.trend.points.first { it.month == august }.totals.expense,
        )
        assertEquals(Money.ZERO, insight.trend.points.first().totals.expense)
    }

    @Test
    fun `占比原样带出来`() = runBlocking {
        val breakdown = CategoryBreakdown.of(
            rows = listOf(CategoryAmount(categoryKey = "food", categoryName = "餐饮", amount = Money.ofCents(1_000))),
            total = Money.ofCents(1_000),
        )
        reader.breakdownOutcome = Outcome.Ok(breakdown)

        val insight = (useCase(october) as Outcome.Ok).value

        assertEquals(breakdown, insight.breakdown)
    }

    @Test
    fun `传月份时用时钟当下所在的月`() = runBlocking {
        useCase()

        assertEquals(rangeOf(october), reader.requestedRanges.first())
    }

    @Test
    fun `可以指定趋势月数`() = runBlocking {
        val insight = (useCase(month = october, trendMonths = 2) as Outcome.Ok).value

        assertEquals(2, insight.trend.points.size)
        assertEquals(listOf(october, september), insight.trend.points.map { it.month })
        // 两段就够：本月 + 上月（环比仍拿得到基线）
        assertEquals(2, reader.requestedRanges.size)
    }

    @Test
    fun `任一段合计读不出来就整体失败_不拿零冒充没花钱`() = runBlocking {
        reader.outcome = Outcome.Err(DomainError.Technical.Storage)

        assertTrue(useCase(october) is Outcome.Err)
    }

    @Test
    fun `占比读不出来也整体失败`() = runBlocking {
        reader.breakdownOutcome = Outcome.Err(DomainError.Technical.Storage)

        assertTrue(useCase(october) is Outcome.Err)
    }
}
