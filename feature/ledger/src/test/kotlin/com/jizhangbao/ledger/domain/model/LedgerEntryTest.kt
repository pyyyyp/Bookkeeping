package com.jizhangbao.ledger.domain.model

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 聚合不变式的正反用例 —— 测试名是业务语言，不是方法名。
 *
 * 覆盖 `docs/20-domain/ledger-model.md` 的 INV-1 / INV-2，
 * 以及 `REQ-001/AC-1` `AC-2` `AC-5` `AC-6`。
 */
class LedgerEntryTest {

    private val categoryId = CategoryId("food")
    private val now: Instant = Instant.parse("2026-10-02T12:00:00Z")

    private fun record(
        direction: EntryDirection = EntryDirection.Expense,
        amount: Money = Money.ofCents(1250),
        categoryId: CategoryId? = this.categoryId,
        occurredAt: Instant = now,
        note: Note? = null,
        bookedAt: Instant = now,
    ): Outcome<LedgerEntry> = LedgerEntry.record(
        direction = direction,
        amount = amount,
        categoryId = categoryId,
        occurredAt = occurredAt,
        note = note,
        bookedAt = bookedAt,
    )

    @Test
    fun `金额为 0 时条目不被记录`() {
        val result = record(amount = Money.ZERO)

        assertTrue(result is Outcome.Err)
        assertEquals(LedgerError.AmountNotPositive, (result as Outcome.Err).error)
    }

    @Test
    fun `未选分类时条目不被记录`() {
        val result = record(categoryId = null)

        assertTrue(result is Outcome.Err)
        assertEquals(LedgerError.CategoryRequired, (result as Outcome.Err).error)
    }

    @Test
    fun `记录一笔支出时金额分类与方向被如实保存`() {
        val result = record(
            direction = EntryDirection.Expense,
            amount = Money.ofCents(1250),
            categoryId = CategoryId("food"),
        )

        assertTrue(result is Outcome.Ok)
        val entry = (result as Outcome.Ok).value
        assertEquals(EntryDirection.Expense, entry.direction)
        assertEquals(Money.ofCents(1250), entry.amount)
        assertEquals(CategoryId("food"), entry.categoryId)
    }

    @Test
    fun `记录一笔收入时方向被如实保存`() {
        val result = record(
            direction = EntryDirection.Income,
            amount = Money.ofCents(800_000),
            categoryId = CategoryId("salary"),
        )

        assertTrue(result is Outcome.Ok)
        assertEquals(EntryDirection.Income, (result as Outcome.Ok).value.direction)
    }

    @Test
    fun `补记过去的日期时发生时间不被改成现在`() {
        val twoDaysAgo = Instant.parse("2026-09-30T03:00:00Z")

        val result = record(occurredAt = twoDaysAgo, bookedAt = now)

        assertTrue(result is Outcome.Ok)
        val entry = (result as Outcome.Ok).value
        assertEquals(twoDaysAgo, entry.occurredAt)
        assertEquals(now, entry.bookedAt)
        assertNotEquals(entry.occurredAt, entry.bookedAt)
    }

    @Test
    fun `不填备注时条目仍然有效`() {
        val result = record(note = null)

        assertTrue(result is Outcome.Ok)
        assertEquals(null, (result as Outcome.Ok).value.note)
    }

    @Test
    fun `填写备注时备注被如实保存`() {
        val result = record(note = Note("和同事聚餐"))

        assertTrue(result is Outcome.Ok)
        assertEquals("和同事聚餐", (result as Outcome.Ok).value.note?.text)
    }

    @Test
    fun `同一条目的两次读取相等，不同条目不等`() {
        val first = record().let { (it as Outcome.Ok).value }
        val restored = LedgerEntry.restore(
            id = first.id,
            direction = first.direction,
            amount = first.amount,
            categoryId = first.categoryId,
            occurredAt = first.occurredAt,
            bookedAt = first.bookedAt,
            note = first.note,
        )
        val another = record().let { (it as Outcome.Ok).value }

        // 实体的相等性按标识：同一个标识就是同一条记录
        assertEquals(first, restored)
        assertEquals(first.hashCode(), restored.hashCode())
        assertNotEquals(first, another)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `从存储恢复一条金额为 0 的损坏数据时立刻暴露`() {
        // 数据在那个时刻是合法的；若现在不合法，说明被外部改坏了 ——
        // 这种情况必须是异常，而不是一条「您没提交成功」的提示
        LedgerEntry.restore(
            id = LedgerEntryId.new(),
            direction = EntryDirection.Expense,
            amount = Money.ZERO,
            categoryId = categoryId,
            occurredAt = now,
            bookedAt = now,
            note = null,
        )
    }

    @Test
    fun `标识是合法 UUID 且每次新建都不同`() {
        val first = LedgerEntryId.new()
        val second = LedgerEntryId.new()

        assertNotEquals(first, second)
        assertEquals(36, first.value.length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `标识不是 UUID 时被拒绝`() {
        LedgerEntryId("not-a-uuid")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `分类标识含大写字母时被拒绝`() {
        CategoryId("Food")
    }
}
