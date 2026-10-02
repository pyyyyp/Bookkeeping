package com.jizhangbao.ledger.domain.model

import com.jizhangbao.core.domain.EntryDirection

/**
 * 预置分类清单（`REQ-001` 范围：只读）。
 *
 * **这份清单是业务内容，不是技术细节**，所以它在领域层而不是 UI 层的下拉数据里：
 * 「有哪些分类」影响条目的合法性与后续的统计口径。
 *
 * 用户可以随时要求调整——改这里只需要改一处。
 * 自定义分类落地时，本类会被仓储取代（见 `Category` 的说明）。
 *
 * 见 `docs/20-domain/ledger-model.md` 的「预置分类」。
 */
/**
 * `internal` 构造器：外部只能通过 [PRESET] 取得清单（本卡分类是只读内置数据），
 * 但**同一模块内的测试**可以构造出非法清单来验证不变式 —— 否则这条校验永远测不到。
 */
class CategoryCatalog internal constructor(private val categories: List<Category>) {

    init {
        val distinctIds = categories.map { it.id }.toSet()
        require(distinctIds.size == categories.size) {
            "预置分类的标识不可重复：${categories.map { it.id.value }}"
        }
    }

    /** 全部分类，顺序即展示顺序。 */
    fun all(): List<Category> = categories

    /** 某个收支方向可用的分类（记账界面按方向过滤）。 */
    fun forDirection(direction: EntryDirection): List<Category> =
        categories.filter { it.supports(direction) }

    fun byId(id: CategoryId): Category? = categories.firstOrNull { it.id == id }

    companion object {
        /** 随应用内置的分类。`other` 两个方向都可用。 */
        val PRESET: CategoryCatalog = CategoryCatalog(
            listOf(
                Category(CategoryId("food"), "餐饮", setOf(EntryDirection.Expense)),
                Category(CategoryId("transport"), "交通", setOf(EntryDirection.Expense)),
                Category(CategoryId("shopping"), "购物", setOf(EntryDirection.Expense)),
                Category(CategoryId("housing"), "居住", setOf(EntryDirection.Expense)),
                Category(CategoryId("medical"), "医疗", setOf(EntryDirection.Expense)),
                Category(CategoryId("entertainment"), "娱乐", setOf(EntryDirection.Expense)),
                Category(CategoryId("salary"), "工资", setOf(EntryDirection.Income)),
                Category(CategoryId("other"), "其他", EntryDirection.entries.toSet()),
            ),
        )
    }
}
