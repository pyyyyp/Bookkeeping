package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryCatalog
import com.jizhangbao.ledger.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * 取"界面上要用到的全部分类"（`REQ-004`）。
 *
 * = **预置** ∪ **自定义（含已归档）**，预置在前。
 *
 * ## 为什么一次取全部，而不是分"可选的"和"全部的"两次
 *
 * 两个用途只差一个过滤条件：
 * - 记账选择器要"**可选**的"（预置里该方向的 + 未归档的自定义）；
 * - 列表/编辑要显示**任意**条目上的分类名（含已归档的）。
 *
 * 分开两个用例会有两份几乎一样的合并逻辑，而"预置从哪来、顺序怎么排"这类细节
 * 会漂移。这里只取一次，过滤交给界面状态（它的 `selectableCategories` 已经是派生属性）。
 *
 * 不按 `archived` 过滤是**刻意**的：归档过的分类仍然要能把历史条目的名字显示出来，
 * 否则那些条目会显示成 id（`REQ-004/AC-3`）。
 */
class LoadCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {

    suspend operator fun invoke(): Outcome<List<Category>> =
        when (val custom = repository.all()) {
            is Outcome.Err -> custom
            is Outcome.Ok -> Outcome.Ok(CategoryCatalog.PRESET.all() + custom.value)
        }
}
