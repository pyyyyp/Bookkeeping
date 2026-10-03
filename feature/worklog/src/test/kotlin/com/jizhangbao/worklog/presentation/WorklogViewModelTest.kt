package com.jizhangbao.worklog.presentation

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.worklog.domain.GeoPoint
import com.jizhangbao.worklog.domain.LocationSource
import com.jizhangbao.worklog.domain.SessionState
import com.jizhangbao.worklog.domain.WorkSession
import com.jizhangbao.worklog.domain.WorkSessionId
import com.jizhangbao.worklog.domain.Workplace
import com.jizhangbao.worklog.domain.WorkplaceId
import com.jizhangbao.worklog.domain.repository.WorkSessionRepository
import com.jizhangbao.worklog.domain.repository.WorkplaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 工时界面的逻辑（`REQ-016/AC-7`~`AC-9`）。
 *
 * ## 这个测试最想守住的一条
 *
 * **确认一段还在跑的时段，不该存下任何东西** —— `WorkSession.confirm()` 会拒绝
 * （只有 `FINISHED` 能确认），而界面这边的职责是**不要把异常变成一次写入**。
 * 这条链路的终点是工资：一个被"顺手确认"的、还没结束的时段，就是一段凭空的工时。
 *
 * ## 两个提示不能混
 *
 * "缺权限"与"暂时拿不到位置"是**完全不同的下一步**（去设置里给权限 vs 到窗边再试一次）。
 * 所以它们各有一条断言，而不是"都为 null 就行"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorklogViewModelTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Instant = Instant.parse("2026-10-14T03:00:00Z") // 北京时间 11:00
    private val today: LocalDate = LocalDate.of(2026, 10, 14)
    private val aPoint = GeoPoint(latitude = 31.2304, longitude = 121.4737)

    private val clock: Clock = Clock.fixed(now, zone)

    @Before
    fun setUp() {
        // viewModelScope 默认跑在 Dispatchers.Main 上，而单元测试里没有主线程
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- 测试替身（嵌套类：detekt 的 MatchingDeclarationName 只管顶层声明）----

    private class FakeWorkplaces(
        private val stored: MutableList<Workplace> = mutableListOf(),
        private val failOnSave: Boolean = false,
    ) : WorkplaceRepository {
        val saved = mutableListOf<Workplace>()
        val removed = mutableListOf<WorkplaceId>()

        override suspend fun save(workplace: Workplace): Outcome<Unit> {
            if (failOnSave) return Outcome.Err(DomainError.Technical.Storage)
            saved += workplace
            stored.removeAll { it.id == workplace.id }
            stored += workplace
            return Outcome.Ok(Unit)
        }

        override suspend fun all(): Outcome<List<Workplace>> = Outcome.Ok(stored.toList())

        override suspend fun remove(id: WorkplaceId): Outcome<Unit> {
            removed += id
            stored.removeAll { it.id == id }
            return Outcome.Ok(Unit)
        }
    }

    private class FakeSessions(
        private var sessions: List<WorkSession> = emptyList(),
        private val failOnSave: Boolean = false,
    ) : WorkSessionRepository {
        val saved = mutableListOf<WorkSession>()
        val requestedRanges = mutableListOf<Pair<LocalDate, LocalDate>>()

        override suspend fun record(startedAt: Instant, endedAt: Instant): Outcome<WorkSessionId> =
            Outcome.Ok(WorkSessionId.random())

        override suspend fun sessionsIn(
            from: LocalDate,
            toInclusive: LocalDate,
        ): Outcome<List<WorkSession>> {
            requestedRanges += from to toInclusive
            return Outcome.Ok(sessions)
        }

        override suspend fun save(session: WorkSession): Outcome<Unit> {
            if (failOnSave) return Outcome.Err(DomainError.Technical.Storage)
            saved += session
            sessions = sessions.filterNot { it.id == session.id } + session
            return Outcome.Ok(Unit)
        }
    }

    private class FakeLocation(
        private val granted: Boolean,
        private val point: GeoPoint?,
    ) : LocationSource {
        override fun hasPermission(): Boolean = granted

        override fun locations(): Flow<GeoPoint> =
            if (point == null) emptyFlow() else flowOf(point)
    }

    private fun viewModel(
        workplaces: FakeWorkplaces = FakeWorkplaces(),
        sessions: FakeSessions = FakeSessions(),
        location: FakeLocation = FakeLocation(granted = true, point = aPoint),
    ) = WorklogViewModel(workplaces, sessions, location, clock)

    private fun session(state: SessionState) = WorkSession.restore(
        id = WorkSessionId.random(),
        startedAt = now.minusSeconds(3600),
        endedAt = if (state == SessionState.RUNNING) null else now,
        state = state,
        zone = zone,
    )

    // ---- AC-7：配置工作地点 ----

    @Test
    fun `AC-7 拿得到位置时_存下来的圆心就是那个点`() = runTest {
        val workplaces = FakeWorkplaces()
        val viewModel = viewModel(workplaces = workplaces, location = FakeLocation(true, aPoint))

        viewModel.addWorkplaceHere("公司", 200.0)

        val saved = workplaces.saved.single()
        assertEquals(aPoint, saved.center)
        assertEquals("公司", saved.name)
        assertEquals(200.0, saved.radiusMeters, 0.001)
        assertNull(viewModel.state.value.notice)
    }

    @Test
    fun `AC-7 名称空或半径非正时_什么都不存`() = runTest {
        val workplaces = FakeWorkplaces()
        val viewModel = viewModel(workplaces = workplaces)

        viewModel.addWorkplaceHere("   ", 200.0)
        viewModel.addWorkplaceHere("公司", 0.0)

        assertTrue(workplaces.saved.isEmpty())
    }

    // ---- AC-8：权限缺失要说得清 ----

    @Test
    fun `AC-8 没有权限时_状态里就是没有权限`() = runTest {
        val viewModel = viewModel(location = FakeLocation(granted = false, point = null))

        assertFalse(viewModel.state.value.hasLocationPermission)
    }

    @Test
    fun `AC-8 有权限但拿不到位置时_提示是暂时拿不到而不是缺权限`() = runTest {
        val viewModel = viewModel(location = FakeLocation(granted = true, point = null))

        viewModel.addWorkplaceHere("公司", 200.0)

        // 两者的下一步完全不同：去设置里给权限 vs 到窗边再试
        assertEquals(WorklogNotice.LocationUnavailable, viewModel.state.value.notice)
    }

    @Test
    fun `AC-8 没有权限时新增地点_提示是缺权限`() = runTest {
        val viewModel = viewModel(location = FakeLocation(granted = false, point = null))

        viewModel.addWorkplaceHere("公司", 200.0)

        assertEquals(WorklogNotice.LocationPermissionMissing, viewModel.state.value.notice)
    }

    // ---- AC-9：系统只记录、人确认 ----

    @Test
    fun `AC-9 确认已经结束的时段_存回去的是已确认`() = runTest {
        val finished = session(SessionState.FINISHED)
        val sessions = FakeSessions(sessions = listOf(finished))
        val viewModel = viewModel(sessions = sessions)

        viewModel.confirm(finished)

        assertEquals(SessionState.CONFIRMED, sessions.saved.single().state)
    }

    @Test
    fun `AC-9 确认一段还在跑的时段_不存任何东西（钱的安全阀）`() = runTest {
        val running = session(SessionState.RUNNING)
        val sessions = FakeSessions(sessions = listOf(running))
        val viewModel = viewModel(sessions = sessions)

        viewModel.confirm(running)

        // WorkSession.confirm() 会拒绝（只有 FINISHED 能确认）。
        // 界面这边的职责是**不要把那个拒绝变成一次写入** —— 否则就是凭空的工时。
        assertTrue(sessions.saved.isEmpty())
    }

    @Test
    fun `AC-9 作废一段进行中的时段_存回去的是已作废`() = runTest {
        val running = session(SessionState.RUNNING)
        val sessions = FakeSessions(sessions = listOf(running))
        val viewModel = viewModel(sessions = sessions)

        viewModel.discard(running)

        assertEquals(SessionState.DISCARDED, sessions.saved.single().state)
    }

    @Test
    fun `存储失败时_状态里给出存储失败提示`() = runTest {
        val finished = session(SessionState.FINISHED)
        val viewModel = viewModel(sessions = FakeSessions(sessions = listOf(finished), failOnSave = true))

        viewModel.confirm(finished)

        assertEquals(WorklogNotice.StorageFailed, viewModel.state.value.notice)
    }

    @Test
    fun `刷新时只问今天这一天的时段`() = runTest {
        val sessions = FakeSessions()
        viewModel(sessions = sessions)

        assertEquals(listOf(today to today), sessions.requestedRanges)
    }
}
