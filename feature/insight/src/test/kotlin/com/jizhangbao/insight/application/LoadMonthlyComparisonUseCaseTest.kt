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
 * 月度环比用例的测试（`REQ-009`）。
 *
 * 最要紧的一条是 `两段区间都走内核的月份口径`：它断言端点收到的两段区间
 * **正是**内核 `YearMonth.toTimeRange` 的结果（本月 + 上月）。
 * 这条断言会在有人"顺手在这儿算一下上月区间"时失败 —— 那正是
 * "环比和合计对不上"的开始（与 `REQ-007` 的手法一致）。
 */
class LoadMonthlyComparisonUseCaseTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), zone)
    private val reader = FakeLedgerTotalsReader()
    private val useCase = LoadMonthlyComparisonUseCase(reader, clock)

    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)

    private fun totalsOf(expenseCents: Long) =
        MonthlyTotals(income = Money.ZERO, expense = Money.ofCents(expenseCents))

    @Test
    fun `两段区间都走内核的月份口径_本月加上月`() = runBlocking {
        useCase(october)

        assertEquals(
            listOf(october.toTimeRange(zone), september.toTimeRange(zone)),
            reader.requestedRanges,
        )
    }

    @Test
    fun `本月与上月各自取合计_差额是多花`() = runBlocking {
        reader.outcomeByRange = { range ->
            if (range == october.toTimeRange(zone)) Outcome.Ok(totalsOf(40_045)) else Outcome.Ok(totalsOf(1_000))
        }

        val result = useCase(october)

        assertEquals(39_045L, (result as Outcome.Ok).value.expenseDelta.cents)
    }

    @Test
    fun `少花时差额为负_且展示带负号`() = runBlocking {
        reader.outcomeByRange = { range ->
            if (range == october.toTimeRange(zone)) Outcome.Ok(totalsOf(1_000)) else Outcome.Ok(totalsOf(40_045))
        }

        val comparison = (useCase(october) as Outcome.Ok).value

        assertTrue(comparison.expenseDelta.isNegative)
        assertEquals("-¥390.45", comparison.expenseDelta.toString())
        // 说"少花 ¥390.45"时要的是**绝对值**：句子里已经有"少"了
        assertEquals("¥390.45", comparison.expenseDelta.magnitude.toString())
    }

    @Test
    fun `上月为空时基线是零且不是错误`() = runBlocking {
        reader.outcomeByRange = { range ->
            if (range == october.toTimeRange(zone)) Outcome.Ok(totalsOf(40_045)) else Outcome.Ok(MonthlyTotals.ZERO)
        }

        val comparison = (useCase(october) as Outcome.Ok).value

        assertEquals(40_045L, comparison.expenseDelta.cents)
        assertTrue(comparison.previousIsEmpty)
    }

    @Test
    fun `不传月份时用时钟当下所在的月`() = runBlocking {
        useCase()

        assertEquals(october.toTimeRange(zone), reader.requestedRanges.first())
    }

    @Test
    fun `任一段读不出来就整体失败_不拿零冒充上月没花钱`() = runBlocking {
        // BR-4：把"读失败"显示成"与上月持平"是这一层最坏的失败形态
        reader.outcome = Outcome.Err(DomainError.Technical.Storage)

        assertTrue(useCase(october) is Outcome.Err)
    }
}
