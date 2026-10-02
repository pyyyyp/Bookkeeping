package com.jizhangbao.core.domain

/**
 * 某个月的**支出按分类占比**（`REQ-005`）：若干行 + 支出合计。
 *
 * ## 它是读模型，不是聚合
 *
 * 与 `MonthlyTotals` 同一条理由（见 `docs/20-domain/insight-model.md`）：
 * 合计没有不变量可守，它的正确性来自源数据。这里只有一个值对象 + 一个派生计算。
 *
 * ## 三条规则住在这里，而不是 SQL 或界面里
 *
 * | 规则 | 内容 | 为什么在这一层 |
 * |---|---|---|
 * | `BR-4` | 金额**降序**，金额相同按分类名升序 | 换数据源也必须成立；同一份数据两次渲染必须一致 |
 * | `BR-6` | 合计为 0 → **空清单** | "没有支出"是正常状态，不是每类 0%，更不是错误 |
 * | `BR-3` | 占比各自四舍五入（见 [Percentage]） | 派生量不存，避免与金额/合计矛盾 |
 *
 * 刻意**不是 `data class`**：`copy()` 会绕过 [of] 里的排序，
 * 得到一个"看起来一样但顺序不同"的清单 —— 那正是 `BR-4` 要防的。
 * 相等性按值（与 `LedgerEntry` 同一个教训：不可变对象若只按标识相等，
 * 状态差分就会丢掉变化）。
 */
class CategoryBreakdown private constructor(
    val rows: List<CategoryAmount>,
    val total: Money,
) {

    val isEmpty: Boolean get() = rows.isEmpty()

    /**
     * 某一行占总支出的比例。
     *
     * 空清单时没有行可问；合计为 0 而仍有行是不可能的（那说明数据自相矛盾），
     * 所以这里直接交给 [Percentage.of] 的 `require` 去暴露。
     */
    fun shareOf(row: CategoryAmount): Percentage = Percentage.of(row.amount.cents, total.cents)

    override fun equals(other: Any?): Boolean =
        this === other || (other is CategoryBreakdown && other.rows == rows && other.total == total)

    override fun hashCode(): Int = 31 * rows.hashCode() + total.hashCode()

    override fun toString(): String = "CategoryBreakdown($rows, total=$total)"

    companion object {

        /** 空清单：该月没有任何支出（`BR-6`）。 */
        val EMPTY: CategoryBreakdown = CategoryBreakdown(rows = emptyList(), total = Money.ZERO)

        /**
         * 构造并**排序**（`BR-4`）。
         *
         * 排序放在这里而不是 SQL：这样"金额降序、同额按名字"是模型的规则，
         * 换掉数据库也成立，而且能在纯 JVM 上测。
         * 合计为 0 时返回 [EMPTY] —— 不构造"每类 0%"那种自相矛盾的东西。
         */
        fun of(rows: List<CategoryAmount>, total: Money): CategoryBreakdown {
            require(total.cents >= 0) { "支出合计不可为负：${total.cents}" }
            if (total.cents == 0L || rows.isEmpty()) return EMPTY

            val sorted = rows
                // 金额降序：-amount 比较在 Long 上不会溢出（个人记账的量级远不到边界）
                .sortedWith(compareByDescending<CategoryAmount> { it.amount.cents }.thenBy { it.categoryName })

            return CategoryBreakdown(rows = sorted, total = total)
        }
    }
}
