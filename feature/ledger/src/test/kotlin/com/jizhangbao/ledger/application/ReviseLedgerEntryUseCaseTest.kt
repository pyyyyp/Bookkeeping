package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.LedgerEntryId
import com.jizhangbao.ledger.domain.model.Note
import com.jizhangbao.ledger.testing.FakeLedgerEntryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * `ReviseLedgerEntryUseCase` 的测试（`REQ-003`）。
 *
 * 最要紧的两条断言：
 * - **校验失败时仓储一次都没被调用**（`AC-3` 的"原条目不变"就是这么成立的——
 *   不是靠回滚，而是根本没走到写库那一步）；
 * - **身份与录入时间原样带过去**（`AC-4`）。
 */
class ReviseLedgerEntryUseCaseTest {

    private val repository = FakeLedgerEntryRepository()
    private val useCase = ReviseLedgerEntryUseCase(repository)

    private val bookedAt = Instant.parse("2026-10-02T12:00:00Z")
    private val occurredAt = Instant.parse("2026-10-02T09:00:00Z")

    private suspend fun stored(amountCents: Long = 1_250): LedgerEntry {
        val entry = (LedgerEntry.record(
            direction = EntryDirection.Expense,
            amount = Money.ofCents(amountCents),
            categoryId = CategoryId("food"),
            occurredAt = occurredAt,
            bookedAt = bookedAt,
        ) as Outcome.Ok).value
        repository.add(entry)
        return entry
    }

    @Test
    fun `修改成功后仓储里是同一条记录的新内容`() = runBlocking {
        val target = stored()

        val result = useCase(
            target = target,
            direction = EntryDirection.Expense,
            amount = Money.ofCents(2_000),
            categoryId = CategoryId("transport"),
            occurredAt = occurredAt,
            note = Note("打车"),
        )

        assertTrue(result is Outcome.Ok)
        val kept = repository.stored().single() // 仍然只有一条：替换，不是新增
        assertEquals(target.id, kept.id)
        assertEquals(Money.ofCents(2_000), kept.amount)
        assertEquals(CategoryId("transport"), kept.categoryId)
        assertEquals("打车", kept.note?.text)
    }

    @Test
    fun `修改不改变身份与录入时间`() = runBlocking {
        val target = stored()

        useCase(target, EntryDirection.Expense, Money.ofCents(9_900), CategoryId("food"), occurredAt, null)

        val kept = repository.stored().single()
        assertEquals(target.id, kept.id)
        assertEquals(bookedAt, kept.bookedAt)
    }

    @Test
    fun `可以修改发生时间`() = runBlocking {
        val target = stored()
        val corrected = Instant.parse("2026-09-15T09:00:00Z")

        useCase(target, EntryDirection.Expense, Money.ofCents(1_250), CategoryId("food"), corrected, null)

        assertEquals(corrected, repository.stored().single().occurredAt)
    }

    @Test
    fun `金额非法时仓储一次都不会被调用`() = runBlocking {
        val target = stored()

        val result = useCase(target, EntryDirection.Expense, Money.ofCents(0), CategoryId("food"), occurredAt, null)

        assertEquals(LedgerError.AmountNotPositive, (result as Outcome.Err).error)
        // AC-3：原条目不变 —— 因为压根没写库
        assertEquals(0, repository.updateCallCount)
        assertEquals(Money.ofCents(1_250), repository.stored().single().amount)
    }

    @Test
    fun `没选分类时仓储一次都不会被调用`() = runBlocking {
        val target = stored()

        val result = useCase(target, EntryDirection.Expense, Money.ofCents(2_000), null, occurredAt, null)

        assertEquals(LedgerError.CategoryRequired, (result as Outcome.Err).error)
        assertEquals(0, repository.updateCallCount)
    }

    @Test
    fun `条目已经不存在时返回未找到而不是新建`() = runBlocking {
        // 造一条"不在账本里"的条目：直接 record，但不 add 进仓储
        val ghost = (LedgerEntry.record(
            direction = EntryDirection.Expense,
            amount = Money.ofCents(1_250),
            categoryId = CategoryId("food"),
            occurredAt = occurredAt,
            bookedAt = bookedAt,
            id = LedgerEntryId.new(),
        ) as Outcome.Ok).value

        val result = useCase(ghost, EntryDirection.Expense, Money.ofCents(2_000), CategoryId("food"), occurredAt, null)

        assertEquals(LedgerError.EntryNotFound, (result as Outcome.Err).error)
        // BR-5：不能悄悄插入一条新的
        assertTrue(repository.stored().isEmpty())
    }

    @Test
    fun `存储失败时返回存储错误`() = runBlocking {
        val target = stored()
        repository.updateOutcome = Outcome.Err(com.jizhangbao.core.domain.DomainError.Technical.Storage)

        val result = useCase(target, EntryDirection.Expense, Money.ofCents(2_000), CategoryId("food"), occurredAt, null)

        assertEquals(
            com.jizhangbao.core.domain.DomainError.Technical.Storage,
            (result as Outcome.Err).error,
        )
        assertEquals(Money.ofCents(1_250), repository.stored().single().amount)
    }
}
