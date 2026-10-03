package com.jizhangbao.worklog.domain

import java.time.Duration
import java.time.Instant

/** 围栏事件：位置在圈内还是圈外，以及**什么时候**。 */
sealed interface GeofenceEvent {

    /** 进入围栏。 */
    data class Entered(val at: Instant) : GeofenceEvent

    /** 一次"当前在圈外"的观测（每次定位更新都会产生一条）。 */
    data class Exited(val at: Instant) : GeofenceEvent
}

/** 规则给出的动作。领域**不知道谁去执行**它（仓储？界面？），只说要做什么。 */
sealed interface GeofenceAction {

    /** 开始一段新工时（`RUNNING`）。 */
    data class StartSession(val at: Instant) : GeofenceAction

    /** 结束这一段。⚠️ [endedAt] 是**第一次离开**的时刻，不是"确认不回来"的时刻。 */
    data class FinishSession(val sessionId: WorkSessionId, val endedAt: Instant) : GeofenceAction

    /** 什么都不做（同一段里、或已经开着一段）。 */
    data object Ignore : GeofenceAction
}

/**
 * 判定需要的全部状态。**纯数据**，所以每一条规则都能用"给定这个状态、这个事件，期望那个动作"来测。
 *
 * [openSessionId] 为 `null` 表示当前没有开着的工时。
 * [exitedAt] 是**第一次**观测到离开的时刻 —— 还在抖动窗口里时它非空，
 * 回到圈内会被清空（那就是"并没有真的离开"）。
 */
data class GeofenceState(
    val openSessionId: WorkSessionId? = null,
    val exitedAt: Instant? = null,
)

/**
 * 围栏的状态迁移（`REQ-016`、`ADR-0013` 决策 4/5）。**纯函数，无平台依赖。**
 *
 * ```
 * 进入     → 没开着  → StartSession
 * 进入     → 开着    → Ignore（这就是"抖动窗口内回来了，还是同一段"）
 * 离开     → 没开着  → Ignore
 * 离开     → 第一次  → Ignore（进窗口，**不结束**）
 * 离开     → 超过窗口 → FinishSession(在**第一次离开那一刻**结束)
 * ```
 *
 * ## ⚠️ 身份不由规则生成
 *
 * `StartSession` 不带 id：新时段的**身份**由执行者（仓储）生成。
 * 规则只回答"该开始了"。于是 `reduce` 的返回值里 `openSessionId` 仍是 `null` ——
 * **执行者拿到 id 后要把它写回状态**。这是刻意的分工：
 * 身份是基础设施的事，规则只关心"有没有开着一段"。
 *
 * ## ⚠️ 已知缺口
 *
 * 若 App 在"还在外面"时被关掉，那段工时会**停在 `RUNNING`**（没有后续更新来结束它）。
 * 那需要一次"启动时对账"（把过久的 `RUNNING` 收尾）—— 记在 `T-030` 的未决里，
 * 本轮不做（做了但没有界面去核对，反而更危险）。
 */
object GeofenceRules {

    /**
     * 离开不足这么多分钟**不算离开**（`Q-027`）。
     *
     * 取值理由：拿快递、楼下买咖啡通常 5 分钟内；GPS 漂移通常几秒。
     * 10 分钟能盖住绝大多数抖动，又不会把真正的"出去一趟"（半小时以上）吞掉。
     * 它是个具名常量，改一处即可 —— 而且改它不会改变任何测试的结构（只有边界值变）。
     */
    const val EXIT_DEBOUNCE_MINUTES = 10

    fun reduce(
        state: GeofenceState,
        event: GeofenceEvent,
    ): Pair<GeofenceState, GeofenceAction> = when (event) {
        is GeofenceEvent.Entered -> onEntered(state, event.at)
        is GeofenceEvent.Exited -> onExited(state, event.at)
    }

    private fun onEntered(
        state: GeofenceState,
        at: Instant,
    ): Pair<GeofenceState, GeofenceAction> =
        if (state.openSessionId != null) {
            // 已经开着一段 → 要么是抖动窗口内回来了，要么是状态异常。两种都不该新建。
            state.copy(exitedAt = null) to GeofenceAction.Ignore
        } else {
            // ⚠️ openSessionId 仍为 null：身份的生成不归规则（见 KDoc）
            GeofenceState(openSessionId = null, exitedAt = null) to GeofenceAction.StartSession(at)
        }

    private fun onExited(
        state: GeofenceState,
        at: Instant,
    ): Pair<GeofenceState, GeofenceAction> {
        val openId = state.openSessionId
        val firstExit = state.exitedAt
        // 写成单个 when 表达式：既让"四种情形"一眼看全，也避免多重返回（detekt 的 ReturnCount）
        return when {
            // 没开着工时：没什么可结束
            openId == null -> state to GeofenceAction.Ignore

            // 第一次观测到离开：进抖动窗口，**先不结束**
            firstExit == null -> state.copy(exitedAt = at) to GeofenceAction.Ignore

            // 真的走了：在**第一次离开那一刻**结束（不是现在 —— 那会多算这 10 分钟）
            Duration.between(firstExit, at).toMinutes() > EXIT_DEBOUNCE_MINUTES ->
                GeofenceState(openSessionId = null, exitedAt = null) to
                    GeofenceAction.FinishSession(sessionId = openId, endedAt = firstExit)

            // 还在窗口里：什么都不做（等下一次更新或下一次进入）
            else -> state to GeofenceAction.Ignore
        }
    }
}
