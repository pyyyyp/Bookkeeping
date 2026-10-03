package com.jizhangbao.ledger.data.totals

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.TimeRange
import com.jizhangbao.ledger.data.local.LedgerEntryEntity
import com.jizhangbao.ledger.testing.FakeCategoryRepository
import com.jizhangbao.ledger.testing.FakeLedgerEntryDao
import com.jizhangbao.ledger.testing.RecordingLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 跨上下文读端口实现的测试（`REQ-002`）。
 *
 * ## 它测什么、不测什么
 *
 * - **测**：两个方向的金额被分别取回并组装成 [com.jizhangbao.core.domain.MonthlyTotals]；
 *   区间是半开的；空区间是零而不是错误；存储异常被翻译成领域错误。
 * - **不测**：SQL 本身。用 fake DAO 时那两行 SQL 根本没执行 ——
 *   它由 `androidTest/LedgerEntryDaoTest` 在真 SQLite 上验证（7+ 条）。
 *   这条分工写在 `T-010` 卡里。
 */
class LedgerTotalsReaderImplTest {

    private val dao = FakeLedgerEntryDao()
    private val reader = LedgerTotalsReaderImpl(dao, FakeCategoryRepository(), RecordingLogger())

    private val october = TimeRange(
        start = Instant.parse("2026-10-01T00:00:00Z"),
        end = Instant.parse("2026-11-01T00:00:00Z"),
    )

    private fun row(
        id: String,
        direction: String,
        amountCents: Long,
        occurredAt: Instant,
    ) = LedgerEntryEntity(
        id = id,
        direction = direction,
        amountCents = amountCents,
        categoryId = if (direction == "Income") "salary" else "food",
        occurredAtEpochMilli = occurredAt.toEpochMilli(),
        bookedAtEpochMilli = occurredAt.toEpochMilli(),
        note = null,
    )

    @Test
    fun `分别汇总收入与支出`() = runBlocking {
        dao.insert(row("a", "Expense", 1_250, Instant.parse("2026-10-02T12:00:00Z")))
        dao.insert(row("b", "Expense", 2_500, Instant.parse("2026-10-03T12:00:00Z")))
        dao.insert(row("c", "Income", 800_000, Instant.parse("2026-10-05T12:00:00Z")))

        val totals = (reader.totalsIn(october) as Outcome.Ok).value

        assertEquals(Money.ofCents(800_000), totals.income)
        assertEquals(Money.ofCents(3_750), totals.expense)
        assertEquals("¥7962.50", totals.net.toString())
    }

    @Test
    fun `区间内没有任何条目时是零而不是错误`() = runBlocking {
        val result = reader.totalsIn(october)

        assertTrue(result is Outcome.Ok)
        assertEquals(Money.ZERO, (result as Outcome.Ok).value.income)
        assertEquals(Money.ZERO, result.value.expense)
    }

    @Test
    fun `区间是半开的_月初算进来_下月初不算`() = runBlocking {
        dao.insert(row("inRange", "Expense", 100, Instant.parse("2026-10-01T00:00:00Z")))
        // 下个月的第一毫秒：不该被算进 10 月
        dao.insert(row("nextMonth", "Expense", 9_999, Instant.parse("2026-11-01T00:00:00Z")))
        // 上个月最后一毫秒：也不该
        dao.insert(row("prevMonth", "Expense", 9_999, Instant.parse("2026-09-30T23:59:59.999Z")))

        val totals = (reader.totalsIn(october) as Outcome.Ok).value

        assertEquals(Money.ofCents(100), totals.expense)
    }

    @Test
    fun `按发生时间归属_与录入时间无关`() = runBlocking {
        // REQ-002/AC-7 / BR-2：10-02 录入、但发生在 09-15 的支出，不该算进 10 月
        val backdated = row("backdated", "Expense", 6_000, Instant.parse("2026-09-15T12:00:00Z"))
            .copy(bookedAtEpochMilli = Instant.parse("2026-10-02T12:00:00Z").toEpochMilli())
        dao.insert(backdated)

        val totals = (reader.totalsIn(october) as Outcome.Ok).value

        assertEquals(Money.ZERO, totals.expense)
    }

    @Test
    fun `存储失败时返回存储错误而不是假装零`() = runBlocking {
        dao.failure = IllegalStateException("disk full")

        val result = reader.totalsIn(october)

        assertEquals(DomainError.Technical.Storage, (result as Outcome.Err).error)
    }
}
