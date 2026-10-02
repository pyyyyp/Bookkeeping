package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.LedgerEntryId
import com.jizhangbao.ledger.domain.model.Note
import com.jizhangbao.ledger.domain.repository.LedgerEntryRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * 记录一笔账目条目（`REQ-001/AC-1` `AC-2` `AC-5` `AC-6`）。
 *
 * **只编排，不含业务规则**：金额能不能为 0、要不要选分类，全部由
 * [LedgerEntry.record] 判定。本类负责的是「取时间 → 组装聚合 → 落库 → 映射结果」。
 *
 * `Clock` 由外部注入（默认系统时钟）——否则「补记」与「录入时间」的用例根本没法
 * 稳定测试：测试可以给一个固定时刻，不需要 sleep，也不会在午夜前后偶发失败。
 */
class RecordLedgerEntryUseCase @Inject constructor(
    private val repository: LedgerEntryRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend operator fun invoke(
        direction: EntryDirection,
        amount: Money,
        categoryId: CategoryId?,
        occurredAt: Instant,
        note: Note? = null,
    ): Outcome<LedgerEntryId> {
        val recorded = LedgerEntry.record(
            direction = direction,
            amount = amount,
            categoryId = categoryId,
            occurredAt = occurredAt,
            note = note,
            bookedAt = clock.instant(),
        )

        return when (recorded) {
            is Outcome.Err -> recorded
            is Outcome.Ok -> when (val saved = repository.add(recorded.value)) {
                is Outcome.Err -> saved
                is Outcome.Ok -> Outcome.Ok(recorded.value.id)
            }
        }
    }
}
