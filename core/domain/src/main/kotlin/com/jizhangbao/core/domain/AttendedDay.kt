package com.jizhangbao.core.domain

import java.time.LocalDate

/**
 * Worklog 交给外部的**事实**（`REQ-014`）：某一天工作了，以及该天**已确认**多少分钟。
 *
 * ## 为什么带上分钟数，而不只是"这天出勤了"
 *
 * 「算不算加班」有一个**时长门槛**（满 8 小时算 1 天，`Q-017`），而门槛是**钱**的规则 ——
 * 归 Payroll（`ADR-0012` 决策 2）。所以 Worklog 给的是**事实**（多少分钟），
 * 由 Payroll 拿门槛去判。这样 Worklog 不需要认识 Calendar，少一条跨上下文边。
 *
 * ## 为什么"没有已确认时段的日期"根本不出现在结果里
 *
 * 因为"这天没记录"与"这天出勤 0 分钟"是**两件不同的事** ——
 * 后者会诱导调用方按缺勤处理，而缺勤意味着 `0` 元（`Q-015`），
 * 即**悄悄扣掉用户的钱**（`Q-026` 的推荐默认值就是冲着这一点定的）。
 * 所以结果里只有**有事实的那些天**。
 */
data class AttendedDay(
    val date: LocalDate,
    /** 该日**已确认**时段之和，单位分钟。 */
    val confirmedMinutes: Int,
) {
    init {
        require(confirmedMinutes > 0) {
            "AttendedDay 只表示「有出勤事实」的天，分钟数必须为正：$date 传入了 $confirmedMinutes"
        }
    }
}
