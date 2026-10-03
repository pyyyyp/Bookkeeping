package com.jizhangbao.worklog.domain

/**
 * 一段工时的状态（glossary）。
 *
 * ## 只有 [CONFIRMED] 是事实
 *
 * [RUNNING]（还在跑）与 [FINISHED]（跑完但未确认）是**过程中的状态**，
 * [DISCARDED] 是作废。**一个还在跑的时段不能变成工资** ——
 * 所以"取出勤事实"时只认 [CONFIRMED]，这条规则由测试守。
 *
 * v1 是手工录入，所以正常路径是**直接建立 [CONFIRMED]**（`BR-3`：用户的录入就是确认）；
 * [RUNNING]/[FINISHED] 是给将来的地理围栏流程留的（`ADR-0012` 决策 3）。
 */
enum class SessionState {
    RUNNING,
    FINISHED,
    CONFIRMED,
    DISCARDED,
}
