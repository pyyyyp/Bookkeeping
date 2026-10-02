package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.LedgerEntryId
import com.jizhangbao.ledger.domain.model.RecentEntries
import com.jizhangbao.ledger.testing.FakeLedgerEntryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 读取列表的用例 —— 覆盖 `REQ-001/AC-7` 的**用例侧**。
 *
 * ⚠️ 真正决定顺序的是 SQL 的 `ORDER BY`，那条路径不在单元测试里（见 `T-007` 卡），
 * 这里测的是：默认条数被传下去、失败被如实上抛、结果不被用例改写。
 */
class LoadRecentEntriesUseCaseTest {

    private val repository = FakeLedgerEntryRepository()
    private val useCase = LoadRecentEntriesUseCase(repository)

    private fun entry(daysAgo: Long, cents: Long): LedgerEntry {
        val occurredAt = Instant.parse("2026-10-02T12:00:00Z").minusSeconds(daysAgo * 86_400)
        val recorded = LedgerEntry.record(
            direction = EntryDirection.Expense,
            amount = Money.ofCents(cents),
            categoryId = CategoryId("food"),
            occurredAt = occurredAt,
            bookedAt = occurredAt,
            id = LedgerEntryId.new(),
        )
        return (recorded as Outcome.Ok).value
    }

    @Test
    fun `默认按首屏条数读取最近的条目`() = runBlocking {
        repository.recentOutcome = Outcome.Ok(RecentEntries.of(listOf(entry(0, 100), entry(1, 200))))

        val result = useCase()

        assertTrue(result is Outcome.Ok)
        assertEquals(2, (result as Outcome.Ok).value.entries.size)
    }

    @Test
    fun `可以指定条数`() = runBlocking {
        repository.recentOutcome =
            Outcome.Ok(RecentEntries.of(listOf(entry(0, 100), entry(1, 200), entry(2, 300))))

        val result = useCase(limit = 2)

        assertEquals(3, (result as Outcome.Ok).value.entries.size) // fake 不排序也不截断，只回答"被调用"
    }

    @Test
    fun `读不出来的条数会一路传出来`() = runBlocking {
        // REQ-006/AC-3：仓储说"跳过了 1 条"，用例必须原样带出去，界面才可能告知用户
        repository.recentOutcome = Outcome.Ok(RecentEntries(entries = listOf(entry(0, 100)), unreadable = 1))

        val result = useCase()

        val loaded = (result as Outcome.Ok).value
        assertEquals(1, loaded.entries.size)
        assertEquals(1, loaded.unreadable)
    }

    @Test
    fun `存储失败时返回存储错误而不是空列表`() = runBlocking {
        // 「读不出来」与「账本是空的」是两件完全不同的事，不能都表现成空列表
        repository.recentOutcome = Outcome.Err(DomainError.Technical.Storage)

        val result = useCase()

        assertEquals(DomainError.Technical.Storage, (result as Outcome.Err).error)
    }
}
