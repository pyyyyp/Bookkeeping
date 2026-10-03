package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.repository.LedgerEntryRepository
import javax.inject.Inject

/**
 * 删掉一条**读不出来**的数据（`REQ-008`）。
 *
 * 为什么是一个用例而不是让 ViewModel 直接碰仓储：这一层要承担"**这是什么操作**"的表达 ——
 * 它删的是"一条进不了域的数据"，与 [RemoveLedgerEntryUseCase]（删一条正常条目）是两件事，
 * 尽管底下走的是同一条 SQL。
 *
 * 收的是**原始主键字符串**（不是 `LedgerEntryId`）：坏数据之所以坏，
 * 往往正是因为它没有合法的标识（`REQ-008/BR-1`）。
 */
class DiscardUnreadableRowUseCase @Inject constructor(private val repository: LedgerEntryRepository) {

    suspend operator fun invoke(rawId: String): Outcome<Unit> = repository.discardUnreadableRow(rawId)
}
