package com.jizhangbao.insight.application

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.MonthlyTotals
import com.jizhangbao.core.domain.Outcome
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
 * `LoadMonthlyTotalsUseCase` 的测试（`REQ-002/AC-6` `AC-7`）。
 *
 * 重点全在**边界**上：本机时区、半开区间、跨年、闰年 2 月。
 * 这些是"换算错了会整体偏一个月"的地方，靠界面冒烟几乎发现不了。
 */
class LoadMonthlyTotalsUseCaseTest {

    private val reader = FakeLedgerTotalsReader()

    /** 东八区，没有夏令时 —— 换算结果是确定的，便于断言 */
    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun useCase(clock: Clock) = LoadMonthlyTotalsUseCase(reader, clock)

    private fun fixedClock(instant: String) =
        Clock.fixed(Instant.parse(instant), shanghai)

    @Test
    fun `月份被换算成本机时区的半开区间`() = runBlocking {
        // 东八区的 10-01 00:00 == UTC 09-30 16:00；11-01 00:00 == UTC 10-31 16:00
        useCase(fixedClock("2026-10-15T00:00:00Z")).invoke(YearMonth.of(2026, 10))

        val range = reader.requestedRanges.single()
        assertEquals(Instant.parse("2026-09-30T16:00:00Z"), range.start)
        assertEquals(Instant.parse("2026-10-31T16:00:00Z"), range.end)
    }

    @Test
    fun `不传月份时用时钟所在的那个月`() = runBlocking {
        // 固定时钟的"当下"是 10 月，所以默认参数应当取 2026-10
        useCase(fixedClock("2026-10-15T00:00:00Z")).invoke()

        val range = reader.requestedRanges.single()
        assertEquals(Instant.parse("2026-09-30T16:00:00Z"), range.start)
        assertEquals(Instant.parse("2026-10-31T16:00:00Z"), range.end)
    }

    @Test
    fun `十二月的下一个月是次年一月`() = runBlocking {
        useCase(fixedClock("2025-12-15T00:00:00Z")).invoke(YearMonth.of(2025, 12))

        val range = reader.requestedRanges.single()
        assertEquals(Instant.parse("2025-11-30T16:00:00Z"), range.start)
        assertEquals(Instant.parse("2025-12-31T16:00:00Z"), range.end)
    }

    @Test
    fun `闰年二月的区间是二十九天`() = runBlocking {
        useCase(fixedClock("2028-02-10T00:00:00Z")).invoke(YearMonth.of(2028, 2))

        val range = reader.requestedRanges.single()
        assertEquals(Instant.parse("2028-01-31T16:00:00Z"), range.start)
        assertEquals(Instant.parse("2028-02-29T16:00:00Z"), range.end)
    }

    @Test
    fun `空月返回零而不是错误`() = runBlocking {
        // 端口在"没有记账"时返回 ZERO（AC-3），用例不该把它变成失败
        val result = useCase(fixedClock("2026-10-15T00:00:00Z")).invoke(YearMonth.of(2026, 10))

        assertTrue(result is Outcome.Ok)
        assertEquals(MonthlyTotals.ZERO, (result as Outcome.Ok).value)
    }

    @Test
    fun `端口失败时原样上抛而不是变成零`() = runBlocking {
        reader.outcome = Outcome.Err(DomainError.Technical.Storage)

        val result = useCase(fixedClock("2026-10-15T00:00:00Z")).invoke(YearMonth.of(2026, 10))

        // 「查不到」与「这个月没花钱」必须区分开：后者是零，前者是错误
        assertEquals(DomainError.Technical.Storage, (result as Outcome.Err).error)
    }

    @Test
    fun `合计原样返回给调用方`() = runBlocking {
        reader.outcome = Outcome.Ok(
            MonthlyTotals(income = Money.ofCents(800_000), expense = Money.ofCents(3_750)),
        )

        val totals = (useCase(fixedClock("2026-10-15T00:00:00Z")).invoke(YearMonth.of(2026, 10))
            as Outcome.Ok).value

        assertEquals("¥7962.50", totals.net.toString())
    }
}
