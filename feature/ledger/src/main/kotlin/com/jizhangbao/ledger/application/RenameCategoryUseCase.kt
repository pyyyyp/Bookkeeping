package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.CategoryName
import com.jizhangbao.ledger.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * 给分类改名（`REQ-004/AC-2`）。
 *
 * 改名**不会**碰任何条目：条目存的是分类 id，而这里只换名字。
 * 所以"历史条目跟着显示新名字"是免费的 —— 它读的就是同一个分类（`BR-6`）。
 *
 * 唯一性检查要把**自己排除掉**，否则"把 A 改成 A"会被判成重名。
 */
class RenameCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {

    suspend operator fun invoke(id: CategoryId, rawName: String): Outcome<Unit> {
        val found = repository.byId(id)
        if (found is Outcome.Err) return Outcome.Err(found.error)

        return resolveAndStore((found as Outcome.Ok).value, CategoryName.ofOrNull(rawName))
    }

    /**
     * 名字非法与"撞上别人的名字"是两条不同的失败，
     * 拆出来是为了让 [invoke] 只有两个 `return`（detekt 的 `ReturnCount` 上限是 2）——
     * 顺手也让"解析名字"与"校验唯一性"这两件事各占一处。
     */
    private suspend fun resolveAndStore(existing: Category, name: CategoryName?): Outcome<Unit> =
        if (name == null) {
            Outcome.Err(LedgerError.CategoryNameInvalid)
        } else {
            when (val available = repository.ensureNameAvailable(name, excluding = existing.id)) {
                is Outcome.Err -> available
                is Outcome.Ok -> repository.update(existing.rename(name))
            }
        }
}
