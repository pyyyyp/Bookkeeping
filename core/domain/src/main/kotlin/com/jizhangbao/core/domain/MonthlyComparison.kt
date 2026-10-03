package com.jizhangbao.core.domain

/**
 * 本月与上月的对比（`REQ-009`）。
 *
 * ## 为什么差额住在模型里，而不是界面里做减法
 *
 * 界面做减法意味着**第二处口径**：谁先减谁、空月算 0 还是算"不可比"、
 * 负号怎么处理 —— 这些规则一旦散在 Compose 里，就没有地方能测它们。
 * 放在这里，四条分支（多花 / 少花 / 持平 / 上月为空）都是纯函数，能直接断言。
 *
 * ## 为什么只比支出
 *
 * 「我花得多了吗」才是用户问的问题。把收入与结余也摆上来会让这一块变成三行数字，
 * 而它们各自的口径问题（收入波动是好事还是坏事？）没有答案。
 * 范围写在 `REQ-009` 的"不做"里。
 *
 * ## 上月为空不是错误（`REQ-009/AC-2`）
 *
 * 上月一条记录都没有时，基线是 **0**，差额就是本月的全额 —— 如实表达，
 * 不显示成"无法比较"。这是 [previousIsEmpty] 存在的唯一理由：界面据此知道
 * 那个 0 是"真的没花钱"而不是"没读到数据"。
 */
data class MonthlyComparison(
    /** 本月合计。 */
    val current: MonthlyTotals,
    /** 上月合计。 */
    val previous: MonthlyTotals,
) {

    /**
     * 支出的差额：本月 − 上月，**可以为负**（少花）。
     *
     * 用 [SignedMoney] 而不是 [Money]：`Money` 的非负性是刻意的
     * （一笔交易的金额永远不是负数，方向由 `EntryDirection` 表达），
     * 不能为了一个派生量把它放开 —— 与 `net`（结余）同一个推理。
     */
    val expenseDelta: SignedMoney
        get() = SignedMoney.ofCents(current.expense.cents - previous.expense.cents)

    /** 是否与上月持平（差额为 0）。 */
    val isUnchanged: Boolean get() = expenseDelta.cents == 0L

    /**
     * 上月是不是"一条记录都没有"。
     *
     * 注意它与 `previous == MonthlyTotals.ZERO` 等价 —— 但还是单独给个名字：
     * 调用方（界面）要表达的是"这个 0 是事实，不是缺失"，用名字说比用等号说清楚。
     */
    val previousIsEmpty: Boolean get() = previous == MonthlyTotals.ZERO
}
