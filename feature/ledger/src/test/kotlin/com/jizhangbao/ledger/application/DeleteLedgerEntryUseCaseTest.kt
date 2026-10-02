package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.LedgerEntryId
import com.jizhangbao.ledger.testing.FakeLedgerEntryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 删除条目的用例 —— 覆盖 `REQ-001/AC-8`（取消则不删）与 `AC-9`（确认后不再存在）。
 *
 * ⚠️ 「取消」这一条**在用例层是测不到的**：取消发生在界面点击之前，仓储根本不会被调用。
 * 这里能证明的是它的反面 —— **未确认时不会走到本用例**。
 * 真正的「取消」验证在界面层（见 T-008 卡的冒烟记录），别把两者混为一谈。
 */
class DeleteLedgerEntryUseCaseTest {

    private val repository = FakeLedgerEntryRepository()
    private val useCase = DeleteLedgerEntryUseCase(repository)

    private suspend fun recordOne(cents: Long = 1250): LedgerEntry {
        val recorded = LedgerEntry.record(
            direction = EntryDirection.Expense,
            amount = Money.ofCents(cents),
            categoryId = CategoryId("food"),
            occurredAt = Instant.parse("2026-10-02T12:00:00Z"),
            bookedAt = Instant.parse("2026-10-02T12:00:00Z"),
            id = LedgerEntryId.new(),
        )
        val entry = (recorded as Outcome.Ok).value
        repository.add(entry)
        return entry
    }

    @Test
    fun `确认删除时账本中不再有该条目`() = runBlocking {
        val entry = recordOne()
        assertEquals(1, repository.stored().size)

        val result = useCase(entry.id)

        assertTrue(result is Outcome.Ok)
        assertTrue(repository.stored().isEmpty())
    }

    @Test
    fun `只删指定的那一条，其他条目不受影响`() = runBlocking {
        val keep = recordOne(cents = 800_000)
        val remove = recordOne(cents = 1250)

        useCase(remove.id)

        val remaining = repository.stored().single()
        assertEquals(keep.id, remaining.id)
    }

    @Test
    fun `删除不存在的条目时返回条目不存在而不是崩溃`() = runBlocking {
        val result = useCase(LedgerEntryId.new())

        assertEquals(LedgerError.EntryNotFound, (result as Outcome.Err).error)
    }

    @Test
    fun `存储失败时返回存储错误而不是假装删掉了`() = runBlocking {
        val entry = recordOne()
        repository.removeOutcome = Outcome.Err(DomainError.Technical.Storage)

        val result = useCase(entry.id)

        assertEquals(DomainError.Technical.Storage, (result as Outcome.Err).error)
        // 关键：条目**仍在**——不能因为提示了错误却已经把数据删了
        assertEquals(1, repository.stored().size)
    }
}
