package com.jizhangbao.insight.application

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
 * 月度趋势用例（`REQ-010`）。
 *
 * 两条最要紧的断言：
 * 1. **六段区间全部走内核 `toTimeRange`**（`BR-1`）—— 与合计、环比、占比同一个函数，
 *    否则趋势里的当月会与合计区差一毫秒或一个月；
 * 2. **顺序是从新到旧**，且第一段就是 `endMonth`（`AC-1`）。
 */
class LoadMonthlyTrendUseCaseTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), zone)
    private val reader = FakeLedgerTotalsReader()
    private val useCase = LoadMonthlyTrendUseCase(reader, clock)

    private val october = YearMonth.of(2026, 10)

    private fun totalsOf(expenseCents: Long) =
        MonthlyTotals(income = Money.ZERO, expense = Money.ofCents(expenseCents))

    @Test
    fun `默认取六个月_区间全部走内核的月份口径且从新到旧`() = runBlocking {
        useCase(october)

        val expected = (0 until 6).map { back -> october.minusMonths(back.toLong()).toTimeRange(zone) }
        assertEquals(expected, reader.requestedRanges)
    }

    @Test
    fun `月份序列是从新到旧_第一个是当前查看的月`() = runBlocking {
        val trend = (useCase(october) as Outcome.Ok).value

        assertEquals(october, trend.points.first().month)
        assertEquals(YearMonth.of(2026, 5), trend.points.last().month)
    }

    @Test
    fun `没有记账的月份是零_不是缺项`() = runBlocking {
        // 只给 8 月一个非零值，其余让 fake 返回 ZERO
        reader.outcomeByRange = { range ->
            if (range == YearMonth.of(2026, 8).toTimeRange(zone)) {
                Outcome.Ok(totalsOf(12_345))
            } else {
                Outcome.Ok(MonthlyTotals.ZERO)
            }
        }

        val trend = (useCase(october) as Outcome.Ok).value

        assertEquals(6, trend.points.size)
        assertEquals(
            Money.ofCents(12_345),
            trend.points.first { it.month == YearMonth.of(2026, 8) }.totals.expense,
        )
        assertEquals(Money.ZERO, trend.points.first().totals.expense)
    }

    @Test
    fun `当月那一行与合计区的数字同源`() = runBlocking {
        reader.outcomeByRange = { range ->
            if (range == october.toTimeRange(zone)) Outcome.Ok(totalsOf(40_045)) else Outcome.Ok(MonthlyTotals.ZERO)
        }

        val trend = (useCase(october) as Outcome.Ok).value

        // AC-2：趋势最新一行 == 合计区的支出（同一个值对象，不是两处各算一遍）
        assertEquals(Money.ofCents(40_045), trend.latest?.expense)
    }

    @Test
    fun `可以指定月数与结束月`() = runBlocking {
        val trend = (useCase(endMonth = october, months = 3) as Outcome.Ok).value

        assertEquals(3, trend.points.size)
        assertEquals(october, trend.points.first().month)
        assertEquals(YearMonth.of(2026, 8), trend.points.last().month)
    }

    @Test
    fun `任一个月读不出来就整体失败_不把失败当成零`() = runBlocking {
        // BR-4：一条假的 ¥0.00 会让整条趋势撒谎（用户会以为那个月没花钱）
        reader.outcome = Outcome.Err(DomainError.Technical.Storage)

        assertTrue(useCase(october) is Outcome.Err)
    }
}
