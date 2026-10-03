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
 *
 * ## 为什么是 `suspend`（`T-029` 改的）
 *
 * 真实实现要**读 Room**，而 Room 的查询是挂起函数 —— 阻塞主线程是被禁止的。
 * 判断规则：**端口要不要 `suspend`，取决于实现是否碰 IO。**
 * `WorkCalendar`（读 assets、内存里查）保持同步；这个端口必须 `suspend`。
 *
 * ⚠️ `T-027` 里"同步查询没有窗口"那条理由说的是**最终一致性**（否决事件快照），
 * 与"能不能阻塞线程"是两件事 —— 改成 `suspend` 不影响那条理由。
 */
interface WorklogReader {

    /**
     * @param from 起始日（含）。
     * @param toInclusive 结束日（含）。
     * @return **只含有已确认工时的那些天**，按日期升序。
     *   没有记录的日子**不会**以 `0` 出现（见 [AttendedDay] 的 KDoc）。
     *
     * ⚠️ 读失败与"这几天没记工时"在返回值上**不可区分**（都是空表）。`T-029` 记录了这个取舍：
     * 原因已被数据层记进日志，且工作日缺省是 `1×`，所以不会因此少算工作日的钱。
     * 将来要给端口加一个"读成功了吗"的标志，那时调用方会跟着改成返回 `Outcome`。
     *
     * 是 `suspend` 的原因见接口 KDoc：**端口的挂起与否取决于实现是否碰 IO**。
     */
    suspend fun attendedDays(from: LocalDate, toInclusive: LocalDate): List<AttendedDay>
}
