package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * 撤销归档（`REQ-004/AC-4`）。
 *
 * 与 [ArchiveCategoryUseCase] 分开而不是做成"设置归档状态"的用例：
 * 它们是两个**用户意图**（"收起来"与"再拿出来"），
 * 而单个 `setArchived(boolean)` 会让调用方去想"我该传 true 还是 false"，也会让
 * 界面上的两个按钮共用一个入口 —— 分开更贴近用户实际做的事。
 */
class RestoreCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {

    suspend operator fun invoke(id: CategoryId): Outcome<Unit> {
        val found = repository.byId(id)

        return when (found) {
            is Outcome.Err -> found
            is Outcome.Ok -> repository.update(found.value.restoreFromArchive())
        }
    }
}
