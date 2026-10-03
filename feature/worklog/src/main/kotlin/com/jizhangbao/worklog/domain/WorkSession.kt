package com.jizhangbao.worklog.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 一段工时（`REQ-014`）。
 *
 * ## 为什么不是 `data class`
 *
 * 它有**状态机**与**不变量**：只有 `RUNNING` 允许没有结束时刻；
 * 结束时刻必须在开始之后；确认过就不能再作废回"还在跑"。
 * `data class` 的 `copy()` 会**绕过全部这些**（项目规则里写着这条禁令，`REQ-003` 也踩过一次）。
 * 所以：私有构造 + 明确的转换方法，**改状态只能走这几个方法**。
 *
 * ## 「归属日」由构造推导，不是存进来的
 *
 * `day = startedAt 在那个时区的日期` —— 换句话说，**`REQ-014/AC-4`（跨午夜归属开始那天）
 * 是一条不可能漂移的规则**：它没有第二个地方可以写错。
 * 数据层因此也不必存这一列。
 *
 * ## v1 的明确局限
 *
 * 夜班跨天时"哪天算加班"本来就需要人拍板（v1 不拍），见 `REQ-014/AC-4`。
 */
class WorkSession private constructor(
    val id: WorkSessionId,
    /** **归属日**：它**开始**的那一天（见类 KDoc）。 */
    val day: LocalDate,
    val startedAt: Instant,
    val endedAt: Instant?,
    val state: SessionState,
) {

    init {
        // 不变量一：状态与结束时刻必须相容。三分而不是二分 ——
        //   RUNNING            **不许**有结束时刻（"还在跑"与"已经结束"是矛盾的）
        //   FINISHED/CONFIRMED **必须**有（没有结束时刻就没有时长可言）
        //   DISCARDED          两可：可能是跑到一半发现记错了直接作废，也可能是结束后才作废
        // ⚠️ 这条最初被我写成"只有 RUNNING 允许没有结束"，结果 `running.discard()` 直接抛异常 ——
        // 是测试把它逮出来的（见 WorkSessionTest 的状态转换用例）。
        val endMatchesState = when (state) {
            SessionState.RUNNING -> endedAt == null
            SessionState.FINISHED, SessionState.CONFIRMED -> endedAt != null
            SessionState.DISCARDED -> true
        }
        require(endMatchesState) {
            "时段状态与结束时刻不匹配：state=$state, endedAt=$endedAt"
        }
        // 不变量二：只要给了结束时刻，它就必须晚于开始（零长度与倒流的时段都是坏数据）
        require(endedAt == null || endedAt.isAfter(startedAt)) {
            "结束时刻必须晚于开始时刻：$startedAt → $endedAt"
        }
    }

    /**
     * 已确认的分钟数；不是 `CONFIRMED` 时恒为 `0`。
     *
     * 这条"不是确认就为 0"是**取出事实时唯一要守的规则** —— 它写在这里，
     * 而不是散在调用方（`ADR-0012` 决策 2：事实与判定分开）。
     */
    val confirmedMinutes: Int
        get() = if (state == SessionState.CONFIRMED && endedAt != null) {
            Duration.between(startedAt, endedAt).toMinutes().toInt()
        } else {
            0
        }

    /** 结束计时（`RUNNING` → `FINISHED`）。 */
    fun finish(at: Instant): WorkSession {
        require(state == SessionState.RUNNING) { "只有进行中的时段能结束：$state" }
        return WorkSession(id, day, startedAt, at, SessionState.FINISHED)
    }

    /** 确认（`FINISHED` → `CONFIRMED`）。 */
    fun confirm(): WorkSession {
        require(state == SessionState.FINISHED) { "只有已结束但未确认的时段能确认：$state" }
        return WorkSession(id, day, startedAt, endedAt, SessionState.CONFIRMED)
    }

    /** 作废。已确认的时段**不能**再作废（它已经进过工资单的视野）。 */
    fun discard(): WorkSession {
        require(state == SessionState.RUNNING || state == SessionState.FINISHED) {
            "只有未确认的时段能作废：$state"
        }
        return WorkSession(id, day, startedAt, endedAt, SessionState.DISCARDED)
    }

    companion object {

        /**
         * 手工录入一段**已确认**的工时（v1 的正常路径，`BR-3`）。
         *
         * 手工录入没有围栏可校验，用户的录入**就是**断言 —— 所以直接是 `CONFIRMED`。
         */
        fun recorded(
            id: WorkSessionId,
            startedAt: Instant,
            endedAt: Instant,
            zone: ZoneId,
        ): WorkSession = WorkSession(
            id = id,
            day = startedAt.atZone(zone).toLocalDate(),
            startedAt = startedAt,
            endedAt = endedAt,
            state = SessionState.CONFIRMED,
        )

        /** 从存储还原（数据层用）。状态与时刻都来自库里，因此照样过一遍不变量。 */
        fun restore(
            id: WorkSessionId,
            startedAt: Instant,
            endedAt: Instant?,
            state: SessionState,
            zone: ZoneId,
        ): WorkSession = WorkSession(
            id = id,
            day = startedAt.atZone(zone).toLocalDate(),
            startedAt = startedAt,
            endedAt = endedAt,
            state = state,
        )
    }
}
