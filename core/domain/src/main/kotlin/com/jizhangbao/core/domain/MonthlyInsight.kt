package com.jizhangbao.core.domain

/**
 * 一次刷新要的**全部**读模型（`T-022`）。
 *
 * ## 为什么把它们装在一个类型里
 *
 * 合计、环比、趋势、占比会**并排显示在同一屏上**，所以它们必须来自同一个月份、
 * 同一次刷新（`REQ-005/AC-7` 就是这条规则）。以前这一层没有名字：
 * 四个用例各自去问端口，于是"最近六个月"里的十月被问了**三次**。
 *
 * 装在一起之后，"同一批查询"这件事在**类型上**就成立了 —— 要就一起要，
 * 于是重复无从产生（不是"记得别重复问"，而是没有地方可以重复问）。
 *
 * ## 它不是聚合
 *
 * 没有不变量可守（与 [MonthlyTotals] 同一个理由）：它只是一个**读模型的口袋**。
 * 正确性来自源数据与口径，规则在聚合与用例里。
 */
data class MonthlyInsight(
    /** 当前查看那个月的合计（`REQ-002`）。 */
    val totals: MonthlyTotals,
    /** 与上月的环比（`REQ-009`）。 */
    val comparison: MonthlyComparison,
    /** 最近几个月的趋势（`REQ-010`）。 */
    val trend: MonthlyTrend,
    /** 该月支出按分类的构成（`REQ-005`）。 */
    val breakdown: CategoryBreakdown,
)
