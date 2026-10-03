package com.jizhangbao.ledger.data.local

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.LedgerEntryId
import com.jizhangbao.ledger.domain.model.Note
import java.time.Instant

/**
 * 外部模型 ↔ 领域模型 的转换（防腐层 ACL）。
 *
 * 这是 R6 的落点：外部模型（Room 实体）绝不进入 `domain`，
 * 双向转换只在这一个文件里发生。数据库改列名、改存储方式，影响面被限制在这里。
 */
internal object LedgerEntryMapper {

    fun toEntity(entry: LedgerEntry): LedgerEntryEntity = LedgerEntryEntity(
        id = entry.id.value,
        direction = entry.direction.name,
        amountCents = entry.amount.cents,
        categoryId = entry.categoryId.value,
        occurredAtEpochMilli = entry.occurredAt.toEpochMilli(),
        bookedAtEpochMilli = entry.bookedAt.toEpochMilli(),
        note = entry.note?.text,
    )

    /**
     * 从存储恢复。
     *
     * 走的是 [LedgerEntry.restore]——它**不做** `Outcome` 包装：
     * 存进去时是合法的，若读出来不合法，说明数据被外部改坏了，
     * 那种情况必须立刻抛错（由仓储翻译成存储类错误），
     * 而不是变成一句「您没提交成功」。
     */
    fun toDomain(entity: LedgerEntryEntity): LedgerEntry = LedgerEntry.restore(
        id = LedgerEntryId(entity.id),
        direction = EntryDirection.valueOf(entity.direction),
        amount = Money.ofCents(entity.amountCents),
        categoryId = CategoryId(entity.categoryId),
        occurredAt = Instant.ofEpochMilli(entity.occurredAtEpochMilli),
        bookedAt = Instant.ofEpochMilli(entity.bookedAtEpochMilli),
        note = entity.note?.let { Note(it) },
    )
}
