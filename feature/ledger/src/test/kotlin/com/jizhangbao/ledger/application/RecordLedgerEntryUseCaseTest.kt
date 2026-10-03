package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.Note
import com.jizhangbao.ledger.testing.FakeLedgerEntryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * 记录条目的用例 —— 覆盖 `REQ-001/AC-1` `AC-2` `AC-5` `AC-6`，以及「领域失败不落库」。
 *
 * 时钟是**固定的**：否则「录入时间」相关的断言会在午夜前后偶发失败，
 * 也会逼出 `Thread.sleep` 这种测试。
 */
class RecordLedgerEntryUseCaseTest {

    private val fixedNow: Instant = Instant.parse("2026-10-02T12:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val repository = FakeLedgerEntryRepository()
    private val useCase = RecordLedgerEntryUseCase(repository, clock)

    private val foodId = CategoryId("food")

    @Test
    fun `记录支出时账本里出现一条支出条目且录入时间取当前时刻`() = runBlocking {
        val result = useCase(
            direction = EntryDirection.Expense,
            amount = Money.ofCents(1250),
            categoryId = foodId,
            occurredAt = fixedNow,
        )

        assertTrue(result is Outcome.Ok)
        val stored = repository.stored().single()
        assertEquals(EntryDirection.Expense, stored.direction)
        assertEquals(Money.ofCents(1250), stored.amount)
        assertEquals(foodId, stored.categoryId)
        assertEquals(fixedNow, stored.bookedAt)
        assertEquals(stored.id, (result as Outcome.Ok).value)
    }

    @Test
    fun `记录收入时方向被如实保存`() = runBlocking {
        useCase(
            direction = EntryDirection.Income,
            amount = Money.ofCents(800_000),
            categoryId = CategoryId("salary"),
            occurredAt = fixedNow,
        )

        assertEquals(EntryDirection.Income, repository.stored().single().direction)
    }

    @Test
    fun `补记过去的日期时发生时间保持过去而录入时间是现在`() = runBlocking {
        val lastWeek = Instant.parse("2026-09-25T02:30:00Z")

        useCase(
            direction = EntryDirection.Expense,
            amount = Money.ofCents(500),
            categoryId = foodId,
            occurredAt = lastWeek,
        )

        val stored = repository.stored().single()
        assertEquals(lastWeek, stored.occurredAt)
        assertEquals(fixedNow, stored.bookedAt)
    }

    @Test
    fun `不填备注时条目仍然有效`() = runBlocking {
        useCase(
            direction = EntryDirection.Expense,
            amount = Money.ofCents(500),
            categoryId = foodId,
            occurredAt = fixedNow,
            note = null,
        )

        assertEquals(null, repository.stored().single().note)
    }

    @Test
    fun `填写备注时备注被保存`() = runBlocking {
        useCase(
            direction = EntryDirection.Expense,
            amount = Money.ofCents(500),
            categoryId = foodId,
            occurredAt = fixedNow,
            note = Note("楼下便利店"),
        )

        assertEquals("楼下便利店", repository.stored().single().note?.text)
    }

    @Test
    fun `未选分类时返回分类必填且账本一件都没写`() = runBlocking {
        val result = useCase(
            direction = EntryDirection.Expense,
            amount = Money.ofCents(500),
            categoryId = null,
            occurredAt = fixedNow,
        )

        assertEquals(LedgerError.CategoryRequired, (result as Outcome.Err).error)
        assertEquals(0, repository.addCallCount)
        assertTrue(repository.stored().isEmpty())
    }

    @Test
    fun `金额为 0 时返回金额必须为正且账本一件都没写`() = runBlocking {
        val result = useCase(
            direction = EntryDirection.Expense,
            amount = Money.ZERO,
            categoryId = foodId,
            occurredAt = fixedNow,
        )

        assertEquals(LedgerError.AmountNotPositive, (result as Outcome.Err).error)
        assertEquals(0, repository.addCallCount)
    }

    @Test
    fun `存储失败时返回存储错误而不是崩溃`() = runBlocking {
        repository.addOutcome = Outcome.Err(DomainError.Technical.Storage)

        val result = useCase(
            direction = EntryDirection.Expense,
            amount = Money.ofCents(500),
            categoryId = foodId,
            occurredAt = fixedNow,
        )

        assertEquals(DomainError.Technical.Storage, (result as Outcome.Err).error)
        assertTrue(repository.stored().isEmpty())
    }
}
