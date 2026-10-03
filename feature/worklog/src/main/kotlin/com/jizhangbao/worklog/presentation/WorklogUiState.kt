package com.jizhangbao.worklog.presentation

import com.jizhangbao.worklog.domain.SessionState
import com.jizhangbao.worklog.domain.WorkSession
import com.jizhangbao.worklog.domain.Workplace
import java.time.LocalDate
import java.time.ZoneId

/**
 * 工时的界面状态（`REQ-016/AC-7`~`AC-9`）。
 *
 * ⚠️ 这里**没有**用户可见文案：只有"发生了什么"的结构化事实（[notice]）。
 * 文案在 Compose 层由 `stringResource` 决定（`R12`：代码里不硬编码用户可见字符串）。
 */
data class WorklogUiState(
    val workplaces: List<Workplace> = emptyList(),
    /** 今天的时段，从新到旧。 */
    val sessions: List<WorkSession> = emptyList(),
    val hasLocationPermission: Boolean = false,
    val isBusy: Boolean = false,
    val today: LocalDate? = null,
    /**
     * 显示时刻用的时区。**放进状态而不是在界面里取 `systemDefault()`**：
     * 它与写入时用的时区必须是同一个，否则"归属日"与界面上看到的那一天会在午夜附近对不上。
     */
    val zone: ZoneId? = null,
    val notice: WorklogNotice? = null,
) {
    /** `RUNNING`/`FINISHED` 的时段 —— 它们**还不是事实**，等着人确认（`ADR-0013` 决策 4）。 */
    val pendingSessions: List<WorkSession>
        get() = sessions.filter { it.state != SessionState.CONFIRMED && it.state != SessionState.DISCARDED }
}

/**
 * 一次性提示。写成密封类型而不是一个 `String`：
 * 文案属于界面（`R12`），而**界面需要知道是哪一类事**才能说得准
 * （"缺少权限"与"存储出错"要给用户完全不同的下一步）。
 */
sealed interface WorklogNotice {

    /** 缺少位置权限 —— 自动记录没开，但**手填仍然可用**。 */
    data object LocationPermissionMissing : WorklogNotice

    /** 没拿到当前位置（室内、刚开定位）—— 不是错误，是可以重试的一件事。 */
    data object LocationUnavailable : WorklogNotice

    /** 存储失败（保存地点 / 读写时段）。 */
    data object StorageFailed : WorklogNotice
}
