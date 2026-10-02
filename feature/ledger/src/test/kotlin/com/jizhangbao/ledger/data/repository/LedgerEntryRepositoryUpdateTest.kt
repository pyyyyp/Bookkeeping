package com.jizhangbao.ledger.data.repository

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.testing.FakeLedgerEntryDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * 仓储的 `update`（`REQ-003`）：**0 行 → EntryNotFound** 是这里最要紧的分支。
 *
 * 用它来验证 `REQ-003/AC-8`（编辑一条已经不存在的条目时给明确结果）。
 * 退化成 insert 的后果很具体：一条已被删除的条目会**复活**，
 * 而用户看到的是"修改成功"。
 *
 * ⚠️ 走的是 fake DAO，所以 `@Update` 生成的 SQL 本身不在这里验证 ——
 * 那由 `androidTest` 的 `LedgerEntryDaoTest` 在真 SQLite 上验证。
 */
class LedgerEntryRepositoryUpdateTest {

    private val dao = FakeLedgerEntryDao()
    private val repository = LedgerEntryRepositoryImpl(dao)

    private val occurredAt = Instant.parse("2026-10-02T09:00:00Z")

    private fun entry(amountCents: Long = 1_250): LedgerEntry = (LedgerEntry.record(
        direction = EntryDirection.Expense,
        amount = Money.ofCents(amountCents),
        categoryId = CategoryId("food"),
        occurredAt = occurredAt,
        bookedAt = Instant.parse("2026-10-02T12:00:00Z"),
    ) as Outcome.Ok).value

    @Test
    fun `替换已存在的条目返回成功`() = runBlocking {
        val original = entry(amountCents = 1_250)
        dao.insert(com.jizhangbao.ledger.data.local.LedgerEntryMapper.toEntity(original))

        val changed = (original.revise(
            EntryDirection.Expense,
            Money.ofCents(2_000),
            CategoryId("transport"),
            occurredAt,
            null,
        ) as Outcome.Ok).value

        val result = repository.update(changed)

        assertEquals(Outcome.Ok(Unit), result)
        val storedRow = dao.inserted.single()
        assertEquals(2_000L, storedRow.amountCents)
        assertEquals("transport", storedRow.categoryId)
    }

    @Test
    fun `替换一条不存在的条目返回未找到而不是插入`() = runBlocking {
        val result = repository.update(entry())

        assertEquals(LedgerError.EntryNotFound, (result as Outcome.Err).error)
        // 关键：没有凭空多出一行
        assertEquals(0, dao.inserted.size)
    }

    @Test
    fun `存储失败时返回存储错误`() = runBlocking {
        dao.failure = IllegalStateException("disk full")

        val result = repository.update(entry())

        assertEquals(DomainError.Technical.Storage, (result as Outcome.Err).error)
    }
}
