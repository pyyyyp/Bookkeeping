package com.jizhangbao.ledger.data.repository

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.data.local.LedgerEntryEntity
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.LedgerEntryId
import com.jizhangbao.ledger.domain.model.Note
import com.jizhangbao.ledger.testing.FakeLedgerEntryDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/**
 * 仓储实现 —— 覆盖 `REQ-001/AC-9`（删除）与异常翻译。
 *
 * ⚠️ 用的 DAO 是内存 fake，所以这组测试**不覆盖 SQL 本身**
 * （排序、LIMIT、DELETE 条件由 Room 编译期校验 + 模拟器冒烟确认）。
 */
class LedgerEntryRepositoryImplTest {

    private val dao = FakeLedgerEntryDao()
    private val repository = LedgerEntryRepositoryImpl(dao)

    private val occurredAt: Instant = Instant.parse("2026-10-01T08:30:00Z")
    private val bookedAt: Instant = Instant.parse("2026-10-02T12:00:00Z")

    private fun entry(note: Note? = null): LedgerEntry = LedgerEntry.restore(
        id = LedgerEntryId("11111111-2222-3333-4444-555555555555"),
        direction = EntryDirection.Expense,
        amount = Money.ofCents(1250),
        categoryId = CategoryId("food"),
        occurredAt = occurredAt,
        bookedAt = bookedAt,
        note = note,
    )

    @Test
    fun `保存条目时写进去的是拆好的原始字段`() = runBlocking {
        val result = repository.add(entry(Note("午饭")))

        assertTrue(result is Outcome.Ok)
        val stored = dao.inserted.single()
        assertEquals("11111111-2222-3333-4444-555555555555", stored.id)
        assertEquals(1250L, stored.amountCents)
        assertEquals("Expense", stored.direction)
        assertEquals("午饭", stored.note)
    }

    @Test
    fun `保存失败时返回存储错误而不是把异常抛给上层`() = runBlocking {
        dao.failure = IllegalStateException("disk full")

        val result = repository.add(entry())

        assertEquals(DomainError.Technical.Storage, (result as Outcome.Err).error)
    }

    @Test
    fun `读取失败时返回存储错误而不是空列表`() = runBlocking {
        dao.failure = IllegalStateException("corrupted")

        val result = repository.recent(limit = 10)

        assertEquals(DomainError.Technical.Storage, (result as Outcome.Err).error)
    }

    @Test
    fun `读取到的行被还原成领域条目`() = runBlocking {
        dao.recentRows = listOf(
            LedgerEntryEntity(
                id = "11111111-2222-3333-4444-555555555555",
                direction = "Income",
                amountCents = 800_000,
                categoryId = "salary",
                occurredAtEpochMilli = occurredAt.toEpochMilli(),
                bookedAtEpochMilli = bookedAt.toEpochMilli(),
                note = null,
            ),
        )

        val result = repository.recent(limit = 10)

        val restored = (result as Outcome.Ok).value.single()
        assertEquals(EntryDirection.Income, restored.direction)
        assertEquals(Money.ofCents(800_000), restored.amount)
        assertEquals(CategoryId("salary"), restored.categoryId)
        assertEquals(null, restored.note)
    }

    @Test
    fun `删除存在的条目时成功`() = runBlocking {
        dao.deleteAffectedRows = 1

        val result = repository.remove(entry().id)

        assertTrue(result is Outcome.Ok)
    }

    @Test
    fun `删除本来就不存在的条目时返回条目不存在而不是成功`() = runBlocking {
        dao.deleteAffectedRows = 0

        val result = repository.remove(entry().id)

        assertEquals(LedgerError.EntryNotFound, (result as Outcome.Err).error)
    }

    @Test
    fun `删除失败时返回存储错误`() = runBlocking {
        dao.failure = IllegalStateException("database locked")

        val result = repository.remove(entry().id)

        assertEquals(DomainError.Technical.Storage, (result as Outcome.Err).error)
    }

    @Test
    fun `协程取消必须原样抛出而不是被当成存储失败`() = runBlocking {
        // 用户离开界面触发取消，绝不能被翻译成「保存失败」——那会误导用户以为数据没保存
        dao.failure = CancellationException("页面已离开")

        val thrown = runCatching { repository.add(entry()) }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }
}
