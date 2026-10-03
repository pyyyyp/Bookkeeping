package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.TimeRange
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.RecentEntries
import com.jizhangbao.ledger.domain.repository.LedgerEntryRepository
import javax.inject.Inject

/**
 * 读取**某个分类**在某段区间内的条目（`REQ-007`，占比下钻）。
 *
 * ## 为什么是新用例，而不是新端口（`BR-5`）
 *
 * 这份数据住在 Ledger **内部**，消费方与实现方是同一个上下文 ——
 * 加一个"端口"只会凭空多一层。端口的价值在于跨上下文（`ADR-0008`），
 * 而这里没有跨。
 *
 * ## 方向为什么由调用方给
 *
 * 占比只统计支出（`REQ-005/AC-8`），所以从占比下钻进来时传 `Expense`。
 * **不在这里写死** `Expense`：下钻这个动作本身与方向无关，
 * 写死了将来做"收入构成"就得回来改这个用例。
 */
class LoadCategoryEntriesUseCase @Inject constructor(private val repository: LedgerEntryRepository) {

    suspend operator fun invoke(
        categoryId: CategoryId,
        range: TimeRange,
        direction: EntryDirection = EntryDirection.Expense,
        limit: Int = LoadRecentEntriesUseCase.DEFAULT_LIMIT,
    ): Outcome<RecentEntries> = repository.inCategory(categoryId, direction, range, limit)
}
