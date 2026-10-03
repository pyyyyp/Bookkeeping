package com.jizhangbao.core.domain

import java.time.YearMonth

/**
 * 最近几个月的支出趋势（`REQ-010`）。
 *
 * ## 它回答的是什么问题
 *
 * 环比（`REQ-009`）回答"跟上个月比"，而**"最近半年怎么样"**才是月度复盘真正要看的。
 * 两者共用同一个端口方法（`totalsIn(range)`），所以趋势同样**不需要新端口** ——
 * 只是同一个问题问六次。
 *
 * ## 为什么是"固定长度的最近几个月"，而不是任意区间
 *
 * 一旦允许任意起止，它就从"一小块界面"变成"一个查询接口"，而这个需求并不需要那个自由度。
 * 要自定义区间是另一个需求（那时再谈分页、缓存、图表）。
 *
 * ## 为什么每个点存 [MonthlyTotals] 而不是只存支出
 *
 * "只比支出"是**展示**的选择；数据本身是同一份合计。存 [MonthlyTotals] 意味着
 * 将来要做"收入的趋势"时**不用改读模型** —— 而多存一个字段的成本是零
 * （它本来就从同一个查询里出来）。
 */
data class MonthlyTrend(
    /**
     * 趋势里的各个月，**从新到旧**。
     *
     * 为什么是倒序：与账本列表一致（越靠上越近）。界面把这一块放在合计区下面，
     * 最新的那个月因此紧挨着合计 —— 而它显示的就是同一个数字（`AC-2`）。
     */
    val points: List<TrendPoint>,
) {

    val isEmpty: Boolean get() = points.isEmpty()

    /**
     * 最新那个月（也就是用户当前在看的那个月）的合计。
     *
     * 界面**不用它渲染** —— 合计区已经显示了。它存在的意义是让 `AC-2` 可以被断言：
     * 趋势最新一行必须与合计区的支出**相等**，而不是各算各的。
     */
    val latest: MonthlyTotals? get() = points.firstOrNull()?.totals

    companion object {
        val EMPTY: MonthlyTrend = MonthlyTrend(emptyList())
    }
}

/**
 * 趋势里的一个点：某个月的合计。
 *
 * @param month 该自然月。
 * @param totals 该月合计；没有记账的月份是 [MonthlyTotals.ZERO]（如实显示 ¥0.00，不留空）。
 */
data class TrendPoint(
    val month: YearMonth,
    val totals: MonthlyTotals,
)
