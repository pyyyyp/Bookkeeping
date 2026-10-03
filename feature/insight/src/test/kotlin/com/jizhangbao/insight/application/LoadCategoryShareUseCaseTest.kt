package com.jizhangbao.insight.application

import com.jizhangbao.core.domain.CategoryBreakdown
import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.insight.testing.FakeLedgerTotalsReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * 分类占比用例的测试（`REQ-005`）。
 *
 * ## 最要紧的一条：口径必须与合计**逐字相同**
 *
 * `AC-7` 要求"翻月时清单与合计一起变"，而它的实现机制是两者**共用同一个换算**
 * （`YearMonth.toTimeRange`）。这个测试把机制钉住：同一月份下，
 * 端口在两次调用里收到的 `TimeRange` 必须**相等**。
 *
 * ⚠️ 只靠"翻月时界面看起来一起变了"是不够的：那种观察在区间差一毫秒时也可能通过
 * （月末那一笔的归属要跨月才看得出来），而这里是精确比较。
 */
class LoadCategoryShareUseCaseTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), zone)
    private val reader = FakeLedgerTotalsReader()
    private val useCase = LoadCategoryShareUseCase(reader, clock)

    @Test
    fun `月份换算成东八区的半开区间`() = runBlocking {
        useCase(YearMonth.of(2026, 10))

        val range = reader.requestedBreakdownRanges.single()
        // 10-01 00:00 (+08:00) = 09-30 16:00 UTC；次月初同理
        assertEquals(Instant.parse("2026-09-30T16:00:00Z"), range.start)
        assertEquals(Instant.parse("2026-10-31T16:00:00Z"), range.end)
    }

    @Test
    fun `不传月份时用时钟当下所在的月`() = runBlocking {
        useCase()

        // 固定时钟是 2026-10-15，所以就是 10 月
        assertEquals(
            Instant.parse("2026-09-30T16:00:00Z"),
            reader.requestedBreakdownRanges.single().start,
        )
    }

    @Test
    fun `与合计拿到完全相同的区间`() = runBlocking {
        val totals = LoadMonthlyTotalsUseCase(reader, clock)
        val month = YearMonth.of(2026, 10)

        useCase(month)
        totals(month)

        // AC-7 的机制：两个数字并排出现在同一屏上，区间必须一模一样 ——
        // 差一毫秒就可能在跨月边界上互相矛盾
        assertEquals(reader.requestedRanges.single(), reader.requestedBreakdownRanges.single())
    }

    @Test
    fun `没有支出时是空清单而不是错误`() = runBlocking {
        assertEquals(CategoryBreakdown.EMPTY, (useCase(YearMonth.of(2026, 10)) as Outcome.Ok).value)
    }

    @Test
    fun `端口失败原样传出`() = runBlocking {
        reader.breakdownOutcome = Outcome.Err(DomainError.Technical.Storage)

        assertEquals(
            DomainError.Technical.Storage,
            (useCase(YearMonth.of(2026, 10)) as Outcome.Err).error,
        )
    }
}
