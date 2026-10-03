package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * 归档一个分类（`REQ-004/AC-3`）。
 *
 * 归档后它不再出现在记账选择器里，但**历史条目照旧显示它的名字** ——
 * 这也是数据层不过滤 `archived` 的原因（`ADR-0009`）。
 *
 * 注意这里没有"删除"这个动作，而且是刻意的：历史条目引用了它，
 * 物理删除要么留悬空引用、要么得改写历史。少一个用例，等于让那件事无处发生。
 */
class ArchiveCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {

    suspend operator fun invoke(id: CategoryId): Outcome<Unit> {
        val found = repository.byId(id)

        return when (found) {
            is Outcome.Err -> found
            // archive() 自身幂等：已归档再归档不会产生无意义的新实例
            is Outcome.Ok -> repository.update(found.value.archive())
        }
    }
}
