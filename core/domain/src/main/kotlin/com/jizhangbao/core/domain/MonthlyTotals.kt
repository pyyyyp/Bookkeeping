package com.jizhangbao.core.domain

/**
 * 某一自然月的收支合计（`REQ-002`）。
 *
 * ## 为什么它没有聚合
 *
 * 合计**没有不变量可守**：没法让一笔"支出合计"违法。真正的规则（金额为正、必须有分类）
 * 在 `LedgerEntry` 聚合里已经守住了；这一层只负责**口径**（哪段时间、按哪个时间字段）。
 * 硬套聚合根只会把一个 SUM 查询包装成三层。
 *
 * ## 为什么 `net` 不作为一个构造参数存下来
 *
 * 存三个数就有"三者互相矛盾"的可能（例如 income=10、expense=3、net=1）。
 * 所以只存两个事实，[net] 由它们算出——单一事实源。
 *
 * ## 空月是零，不是 null
 *
 * "这个月没有记账"与"查不到数据"是两件事：前者是正常的零（[ZERO]），
 * 后者应该走 [Outcome.Err]。用 `null` 表达空月会让调用方分不清两者。
 *
 * 它住在共享内核，而不是 `:feature:insight`：生产方（Ledger）与消费方（Insight）
 * 都要引用它，而 R2 禁止 feature 互相依赖（见 `ADR-0008`）。
 */
data class MonthlyTotals(
    /** 该月收入合计，非负。 */
    val income: Money,
    /** 该月支出合计，非负。 */
    val expense: Money,
) {

    /**
     * 结余 = 收入 − 支出，**可以为负**（超支）。
     *
     * 因为可以是负的，它不能是 [Money]（非负），必须是 [SignedMoney]。
     */
    val net: SignedMoney
        get() = SignedMoney.ofCents(income.cents - expense.cents)

    companion object {
        /** 空月：三项都是零。 */
        val ZERO: MonthlyTotals = MonthlyTotals(Money.ZERO, Money.ZERO)
    }
}
