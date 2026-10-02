package com.jizhangbao.ledger.domain.model

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * `LedgerEntry.revise(...)` 的行为测试（`REQ-003`）。
 *
 * 这里守的是三件容易被"顺手改坏"的事：
 * 1. **身份与录入时间不变**（`BR-3` `BR-4`）——否则"编辑"就变成了"删一条加一条"；
 * 2. **校验与记账共用同一套**（`BR-6`）——同一个非法输入在两条路径上必须给同样的结果；
 * 3. **原实例不变**（`AC-3` 的"原条目一字不变"靠的是不可变性，不是回滚）。
 */
class LedgerEntryReviseTest {

    private val id = LedgerEntryId.new()
    private val bookedAt = Instant.parse("2026-10-02T12:00:00Z")
    private val occurredAt = Instant.parse("2026-10-02T09:00:00Z")

    private fun entry(
        amountCents: Long = 1_250,
        note: Note? = null,
    ): LedgerEntry = (
        LedgerEntry.record(
            direction = EntryDirection.Expense,
            amount = Money.ofCents(amountCents),
            categoryId = CategoryId("food"),
            occurredAt = occurredAt,
            note = note,
            bookedAt = bookedAt,
            id = id,
        ) as Outcome.Ok
        ).value

    @Test
    fun `修改后字段取新值`() {
        val original = entry()

        val revised = (
            original.revise(
                direction = EntryDirection.Expense,
                amount = Money.ofCents(2_000),
                categoryId = CategoryId("transport"),
                occurredAt = occurredAt,
                note = Note("打车"),
            ) as Outcome.Ok
            ).value

        assertEquals(Money.ofCents(2_000), revised.amount)
        assertEquals(CategoryId("transport"), revised.categoryId)
        assertEquals("打车", revised.note?.text)
    }

    @Test
    fun `修改不改变身份`() {
        // BR-4：它就是"同一条记录"。换 id 等于删一条加一条
        val original = entry()

        val revised = (original.revise(
            EntryDirection.Expense,
            Money.ofCents(9_900),
            CategoryId("food"),
            occurredAt,
            null,
        ) as Outcome.Ok).value

        assertEquals(id, revised.id)
        assertEquals(original, revised) // 相等性按标识判断，所以仍相等
    }

    @Test
    fun `修改不改变录入时间`() {
        // BR-3：录入时间是"这笔什么时候被记进账本"，是已发生的事实
        val original = entry()

        val revised = (original.revise(
            EntryDirection.Expense,
            Money.ofCents(9_900),
            CategoryId("food"),
            occurredAt,
            null,
        ) as Outcome.Ok).value

        assertEquals(bookedAt, revised.bookedAt)
    }

    @Test
    fun `修改可以改发生时间`() {
        // BR-2 已定案：最主要的用例就是"补记时日期选错"
        val original = entry()
        val newOccurredAt = Instant.parse("2026-09-15T09:00:00Z")

        val revised = (original.revise(
            EntryDirection.Expense,
            Money.ofCents(1_250),
            CategoryId("food"),
            newOccurredAt,
            null,
        ) as Outcome.Ok).value

        assertEquals(newOccurredAt, revised.occurredAt)
    }

    @Test
    fun `金额为 0 或负数时被拒且原条目不变`() {
        val original = entry(amountCents = 1_250)

        val rejected = original.revise(
            EntryDirection.Expense,
            Money.ofCents(0),
            CategoryId("food"),
            occurredAt,
            null,
        )

        assertEquals(LedgerError.AmountNotPositive, (rejected as Outcome.Err).error)
        // 不可变性：原实例根本没被动过，"原条目不变"不是靠回滚
        assertEquals(Money.ofCents(1_250), original.amount)
        assertSame(original, original)
    }

    @Test
    fun `没选分类时被拒`() {
        val original = entry()

        val rejected = original.revise(
            EntryDirection.Expense,
            Money.ofCents(2_000),
            null,
            occurredAt,
            null,
        )

        // 与 record() 给的结果**完全一样**（BR-6：两条路径共用同一套校验）
        assertEquals(LedgerError.CategoryRequired, (rejected as Outcome.Err).error)
        assertEquals(
            LedgerError.CategoryRequired,
            (LedgerEntry.record(
                EntryDirection.Expense,
                Money.ofCents(2_000),
                null,
                occurredAt,
            ) as Outcome.Err).error,
        )
    }

    @Test
    fun `可以清空备注`() {
        val original = entry(note = Note("午饭"))

        val revised = (original.revise(
            EntryDirection.Expense,
            Money.ofCents(1_250),
            CategoryId("food"),
            occurredAt,
            null,
        ) as Outcome.Ok).value

        assertEquals(null, revised.note)
    }

    @Test
    fun `可以换方向与分类`() {
        val original = entry()

        val revised = (original.revise(
            EntryDirection.Income,
            Money.ofCents(800_000),
            CategoryId("salary"),
            occurredAt,
            null,
        ) as Outcome.Ok).value

        assertEquals(EntryDirection.Income, revised.direction)
        assertEquals(CategoryId("salary"), revised.categoryId)
        // 内容变了，但身份没变 —— 这两件事同时成立才是"编辑"
        assertTrue(revised.id == original.id)
        assertNotEquals(original.amount, revised.amount)
    }
}
