package com.jizhangbao.core.domain

import java.time.LocalDate

/**
 * 某一天在**计酬口径**下的性质（`REQ-012`）。
 *
 * ## 为什么它不是"星期几"
 *
 * 调休会把某个周六变成工作日。那时**星期几与日期类型不一致**，
 * 而工资看的是后者 —— 判错的代价是钱。
 *
 * ## 为什么它住共享内核（`ADR-0011` 决策 4 显式裁决过一处冲突）
 *
 * 上下文地图原写"`DayType` 不放共享内核"，但消费方是 Payroll、实现在 Calendar，
 * 而 `R2` 不许 feature 之间互相依赖 —— 按字面做就是 `feature:payroll → feature:calendar`。
 * 裁决沿用 `ADR-0008` 的判例（与 `MonthlyTotals` 同构）：
 * **语义归 Calendar（只有它能判定），类型放内核（因为要跨上下文传递）。**
 */
enum class DayType {
    /** 按计酬口径应当上班的日子。**含调休**（被调成工作日的周六周日）。 */
    WORKDAY,

    /** 不上班的日子（通常的周六周日）。**法定节假日不算在这里** —— 加班倍数不同（2× vs 3×）。 */
    REST_DAY,

    /** 国务院公布的法定节假日。加班 3×。 */
    STATUTORY_HOLIDAY,
}

/**
 * 判定结果。它不是"一个枚举值"，而是**一个值 + 一句关于数据的话**。
 *
 * [missingYear] 是这条规则的载体：某一年还没有节假日数据时，判定会退化成周末规则 ——
 * 那时**必须让调用方知道**。否则用户会以为"今年没有节假日"，
 * 而真相是"今年的数据还没填"（`ADR-0011` 决策 2）。**静默退化最糟。**
 */
data class DayTypeResult(
    val date: LocalDate,
    val type: DayType,
    /** `true` = 这一年的节假日数据还没填，[type] 是按周末规则推的。 */
    val missingYear: Boolean = false,
)
