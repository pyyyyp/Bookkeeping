package com.jizhangbao.ledger.data.local

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.LedgerEntryId
import com.jizhangbao.ledger.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * 防腐层的转换测试（R6 的落点）。
 *
 * 重点是**往返一致**：领域 → 实体 → 领域，除时间被截到毫秒外必须完全相同。
 * 这条通过，就说明「数据库换了存储方式但没漏字段」这件事是有据可查的。
 */
class LedgerEntryMapperTest {

    private val occurredAt: Instant = Instant.parse("2026-10-01T08:30:00.123Z")
    private val bookedAt: Instant = Instant.parse("2026-10-02T12:00:00.456Z")

    private fun entry(note: Note?): LedgerEntry = LedgerEntry.restore(
        id = LedgerEntryId("11111111-2222-3333-4444-555555555555"),
        direction = EntryDirection.Expense,
        amount = Money.ofCents(1250),
        categoryId = CategoryId("food"),
        occurredAt = occurredAt,
        bookedAt = bookedAt,
        note = note,
    )

    @Test
    fun `实体里存的是原始类型而不是值对象`() {
        val entity = LedgerEntryMapper.toEntity(entry(Note("午饭")))

        assertEquals("11111111-2222-3333-4444-555555555555", entity.id)
        assertEquals("Expense", entity.direction)
        assertEquals(1250L, entity.amountCents)
        assertEquals("food", entity.categoryId)
        assertEquals(occurredAt.toEpochMilli(), entity.occurredAtEpochMilli)
        assertEquals(bookedAt.toEpochMilli(), entity.bookedAtEpochMilli)
        assertEquals("午饭", entity.note)
    }

    @Test
    fun `往返转换后各字段与原来一致`() {
        val original = entry(Note("午饭"))

        val roundTripped = LedgerEntryMapper.toDomain(LedgerEntryMapper.toEntity(original))

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.direction, roundTripped.direction)
        assertEquals(original.amount, roundTripped.amount)
        assertEquals(original.categoryId, roundTripped.categoryId)
        assertEquals(original.occurredAt.toEpochMilli(), roundTripped.occurredAt.toEpochMilli())
        assertEquals(original.bookedAt.toEpochMilli(), roundTripped.bookedAt.toEpochMilli())
        assertEquals(original.note, roundTripped.note)
    }

    @Test
    fun `没有备注的条目往返后仍然没有备注`() {
        val roundTripped = LedgerEntryMapper.toDomain(LedgerEntryMapper.toEntity(entry(null)))

        assertEquals(null, roundTripped.note)
    }

    @Test
    fun `收入方向的往返不被写成支出`() {
        val income = LedgerEntry.restore(
            id = LedgerEntryId.new(),
            direction = EntryDirection.Income,
            amount = Money.ofCents(800_000),
            categoryId = CategoryId("salary"),
            occurredAt = occurredAt,
            bookedAt = bookedAt,
            note = null,
        )

        assertEquals(EntryDirection.Income, LedgerEntryMapper.toDomain(LedgerEntryMapper.toEntity(income)).direction)
    }

    @Test
    fun `持久化保留到毫秒，这是存储粒度不是精度损失`() {
        // 领域层可以有纳秒级精度，但界面能给到的最细粒度就是毫秒
        val nano = LedgerEntry.restore(
            id = LedgerEntryId.new(),
            direction = EntryDirection.Expense,
            amount = Money.ofCents(100),
            categoryId = CategoryId("food"),
            occurredAt = Instant.parse("2026-10-01T08:30:00.123456789Z"),
            bookedAt = bookedAt,
            note = null,
        )

        val restored = LedgerEntryMapper.toDomain(LedgerEntryMapper.toEntity(nano))

        assertEquals(Instant.parse("2026-10-01T08:30:00.123Z"), restored.occurredAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `存储里的方向名被改坏时立刻暴露而不是静默当成支出`() {
        val broken = LedgerEntryEntity(
            id = LedgerEntryId.new().value,
            direction = "NotADirection",
            amountCents = 100,
            categoryId = "food",
            occurredAtEpochMilli = occurredAt.toEpochMilli(),
            bookedAtEpochMilli = bookedAt.toEpochMilli(),
            note = null,
        )

        LedgerEntryMapper.toDomain(broken)
    }

    @Test
    fun `记录一笔新条目也能被转换（走的是 record 而非 restore）`() {
        val recorded = LedgerEntry.record(
            direction = EntryDirection.Expense,
            amount = Money.ofCents(500),
            categoryId = CategoryId("transport"),
            occurredAt = occurredAt,
            bookedAt = bookedAt,
        )

        val entity = LedgerEntryMapper.toEntity((recorded as Outcome.Ok).value)

        assertEquals("transport", entity.categoryId)
    }
}
