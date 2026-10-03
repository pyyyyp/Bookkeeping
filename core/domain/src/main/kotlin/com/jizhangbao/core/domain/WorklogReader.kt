package com.jizhangbao.core.domain

import java.time.LocalDate

/**
 * 工时事实的**只读端口**（`REQ-014`）：某段日期里，哪些天有已确认的工时、各多少分钟。
 *
 * ## 为什么是端口而不是事件（`ADR-0012` 决策 1）
 *
 * 上下文地图原写"订阅 `WorkSessionConfirmed` 事件 + 本地快照"，但按 §0，
 * **已接受的 ADR 优先**：`ADR-0008` 确立的是"端口住内核、实现住上下文、装配在 `:app`"。
 *
 * 而且理由不只是优先级：工资单若依赖**最终一致**的快照，
 * 一次尚未传播的时段就是**少算钱** —— 同步查询没有这个窗口。
 *
 * ## 为什么收日期区间而不是 `TimeRange`
 *
 * 问的是"哪几天"，不是"哪些时刻"。用 `LocalDate` 就不必回答"用谁的时区把瞬间变成日期"——
 * 那是调用方才知道的事（`T-025` 的同一条推理）。
 */
interface WorklogReader {

    /**
     * @param from 起始日（含）。
     * @param toInclusive 结束日（含）。
     * @return **只含有已确认工时的那些天**，按日期升序。
     *   没有记录的日子**不会**以 `0` 出现（见 [AttendedDay] 的 KDoc）。
     */
    fun attendedDays(from: LocalDate, toInclusive: LocalDate): List<AttendedDay>
}
