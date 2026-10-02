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

    /**
     * 内置清单与自定义分类合并（`REQ-004`）：**预置在前**，自定义在后。
     *
     * 两个消费者都要这份合并结果：界面用的分类清单（`LoadCategoriesUseCase`）
     * 与占比里的**名字解析**（`LedgerTotalsReaderImpl`）。
     * 合并放在这里，「预置从哪来、顺序如何」就只有一处说法 —— 分开写迟早漂移。
     */
    fun mergedWith(custom: List<Category>): List<Category> = categories + custom

    companion object {
        /**
         * 随应用内置的分类。`other` 两个方向都可用。
         *
         * 用 [Category.restore] 而不是 `create`：这些是**随代码编译进来的可信常量**
         * （与"从存储恢复"同一种性质：值已经是对的，不需要再走用户输入的校验路径）。
         * 写错了会在构造时立刻抛出来 —— 那正是编译期常量该有的失败方式。
         */
        val PRESET: CategoryCatalog = CategoryCatalog(
            listOf(
                preset("food", "餐饮", EntryDirection.Expense),
                preset("transport", "交通", EntryDirection.Expense),
                preset("shopping", "购物", EntryDirection.Expense),
                preset("housing", "居住", EntryDirection.Expense),
                preset("medical", "医疗", EntryDirection.Expense),
                preset("entertainment", "娱乐", EntryDirection.Expense),
                preset("salary", "工资", EntryDirection.Income),
                preset("other", "其他", *EntryDirection.entries.toTypedArray()),
            ),
        )

        private fun preset(id: String, name: String, vararg directions: EntryDirection): Category =
            Category.restore(
                id = CategoryId(id),
                name = name,
                directions = directions.toSet(),
                archived = false,
            )
    }
}
