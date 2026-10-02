package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.Note
import com.jizhangbao.ledger.domain.repository.LedgerEntryRepository
import java.time.Instant
import javax.inject.Inject

/**
 * 修改一条已有的账目条目（`REQ-003`）。
 *
 * ## 它只做两件事，顺序很重要
 *
 * 1. **先让聚合校验**（`target.revise(...)`）——金额、分类这些规则住在聚合里，
 *    用例不重复判断（`BR-6`）。校验不过就**不碰仓储**：所以"校验失败时原条目不变"
 *    是天然成立的，而不是靠回滚。
 * 2. **再落库**（`repository.update`）——它按标识替换那一行；影响行数为 0 时返回
 *    `EntryNotFound`（`BR-5`：这条已经不在账本里了，**不要**退化成插入）。
 *
 * 注意参数里的 `target` 是**被编辑的那条**：它的 `id` 与录入时间不会被改动
 * （`BR-3` `BR-4`），因此这里不需要、也不应该接受 id/bookedAt 作为参数。
 */
class ReviseLedgerEntryUseCase @Inject constructor(
    private val repository: LedgerEntryRepository,
) {

    @Suppress("LongParameterList") // 参数就是表单字段，拆成对象只会多一层没有含义的包装
    suspend operator fun invoke(
        target: LedgerEntry,
        direction: EntryDirection,
        amount: Money,
        categoryId: CategoryId?,
        occurredAt: Instant,
        note: Note?,
    ): Outcome<LedgerEntry> = when (val revised = target.revise(
        direction = direction,
        amount = amount,
        categoryId = categoryId,
        occurredAt = occurredAt,
        note = note,
    )) {
        // 校验没过：原条目没被碰过
        is Outcome.Err -> Outcome.Err(revised.error)
        is Outcome.Ok -> when (val stored = repository.update(revised.value)) {
            is Outcome.Err -> Outcome.Err(stored.error)
            // 回传改好之后的那条，界面据此刷新列表（而不是自己拼一份"应该是这样"的数据）
            is Outcome.Ok -> Outcome.Ok(revised.value)
        }
    }
}
