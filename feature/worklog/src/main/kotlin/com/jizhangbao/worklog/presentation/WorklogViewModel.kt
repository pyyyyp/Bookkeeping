package com.jizhangbao.worklog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.worklog.domain.GeoPoint
import com.jizhangbao.worklog.domain.LocationSource
import com.jizhangbao.worklog.domain.WorkSession
import com.jizhangbao.worklog.domain.Workplace
import com.jizhangbao.worklog.domain.WorkplaceId
import com.jizhangbao.worklog.domain.repository.WorkSessionRepository
import com.jizhangbao.worklog.domain.repository.WorkplaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * 工时的界面逻辑（`REQ-016/AC-7`~`AC-9`）。
 *
 * ## `AC-8` 的关键：权限缺失**要能说出来**
 *
 * [refresh] 每次都问 [LocationSource.hasPermission] 并把结论放进状态 ——
 * 静默失效最糟（用户会以为"今天没上班"，`REQ-006` 的教训）。
 *
 * ## `AC-7` 的关键：不做地图
 *
 * 用**当前位置**当圆心（[addWorkplaceHere]）。"我就站在干活的地方"是这个场景最自然的输入，
 * 而地图 SDK 是新依赖（须先问）。
 *
 * ## `AC-9` 的关键：确认是**唯一**让围栏时段变成事实的动作
 *
 * 围栏最多把时段推到 `FINISHED`（`T-030`）；只有 [confirm] 会把它变成 `CONFIRMED`，
 * 而出勤事实只认 `CONFIRMED`。**这一条不是界面逻辑，是钱的安全阀**。
 */
@HiltViewModel
class WorklogViewModel @Inject constructor(
    private val workplaces: WorkplaceRepository,
    private val sessions: WorkSessionRepository,
    private val location: LocationSource,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(WorklogUiState())
    val state: StateFlow<WorklogUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** 重新读地点、今天的时段、权限状态。 */
    fun refresh() {
        viewModelScope.launch {
            val today = clock.instant().atZone(clock.zone).toLocalDate()
            val loadedWorkplaces = workplaces.all()
            val loadedSessions = sessions.sessionsIn(today, today)
            _state.update { current ->
                current.copy(
                    today = today,
                    zone = clock.zone,
                    hasLocationPermission = location.hasPermission(),
                    workplaces = (loadedWorkplaces as? Outcome.Ok)?.value ?: current.workplaces,
                    sessions = (loadedSessions as? Outcome.Ok)?.value?.sortedByDescending { it.startedAt }
                        ?: current.sessions,
                    // ⚠️ 权限缺失是**持续状态**，不是一次性事件 —— 由「权限」那一区块直接显示，
                    // **不用对话框**。真机冒烟抓到的：对话框会盖住「开启自动记录」那个按钮，
                    // 于是用户看到"缺少权限"却点不到修好它的入口。
                    // 对话框只留给一次性的事（用户在某个动作上失败了）。
                    notice = when {
                        loadedWorkplaces is Outcome.Err || loadedSessions is Outcome.Err ->
                            WorklogNotice.StorageFailed
                        else -> current.notice
                    },
                )
            }
        }
    }

    /** 权限对话框回来之后调用（不管用户选了什么，都重新读一遍状态）。 */
    fun onPermissionResult() {
        refresh()
    }

    /**
     * 用**当前位置**新增一个工作地点（`AC-7`）。
     *
     * 拿不到位置时给出 [WorklogNotice.LocationUnavailable] —— 那**不是**错误，
     * 是"室内/刚开定位"这种可以重试的情况。
     */
    fun addWorkplaceHere(name: String, radiusMeters: Double) {
        if (name.isBlank() || radiusMeters <= 0.0) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, notice = null) }
            val point: GeoPoint? = takeCurrentLocation()
            if (point == null) {
                _state.update {
                    it.copy(
                        isBusy = false,
                        notice = if (location.hasPermission()) {
                            WorklogNotice.LocationUnavailable
                        } else {
                            WorklogNotice.LocationPermissionMissing
                        },
                    )
                }
                return@launch
            }
            val workplace = Workplace.of(
                id = WorkplaceId.random(),
                name = name,
                center = point,
                radiusMeters = radiusMeters,
            )
            val saved = workplaces.save(workplace)
            _state.update {
                it.copy(isBusy = false, notice = if (saved is Outcome.Err) WorklogNotice.StorageFailed else null)
            }
            refresh()
        }
    }

    fun removeWorkplace(id: WorkplaceId) {
        viewModelScope.launch {
            val removed = workplaces.remove(id)
            if (removed is Outcome.Err) {
                _state.update { it.copy(notice = WorklogNotice.StorageFailed) }
            }
            refresh()
        }
    }

    /** 确认一段围栏产生的工时（`AC-9`）—— **唯一**让它进入出勤事实的动作。 */
    fun confirm(session: WorkSession) {
        transition(session) { it.confirm() }
    }

    /** 作废一段（路过公司、记错了）。 */
    fun discard(session: WorkSession) {
        transition(session) { it.discard() }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun transition(session: WorkSession, change: (WorkSession) -> WorkSession) {
        viewModelScope.launch {
            // ⚠️ 转换在**领域**里做（状态机在 WorkSession 上），这里只负责把它存回去。
            // 界面永远不自己拼一个 CONFIRMED —— 那会绕过状态机的不变量。
            val changed = runCatching { change(session) }.getOrNull() ?: return@launch
            val saved = sessions.save(changed)
            if (saved is Outcome.Err) {
                _state.update { it.copy(notice = WorklogNotice.StorageFailed) }
            }
            refresh()
        }
    }

    /** 取一次当前位置（围栏判定不需要连续流，配置地点只要一个点）。 */
    private suspend fun takeCurrentLocation(): GeoPoint? =
        runCatching { location.locations().first() }.getOrNull()
}
