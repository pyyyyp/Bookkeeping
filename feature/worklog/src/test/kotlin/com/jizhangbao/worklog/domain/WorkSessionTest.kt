package com.jizhangbao.worklog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 工时时段的状态机与不变量（`REQ-014/AC-1`、`AC-4`）。
 *
 * 这一层的价值几乎全在**拒绝了什么**：`WorkSession` 不用 `data class` 就是为了
 * 让 `copy(state = CONFIRMED)` 这种绕过不变量的事写不出来（项目规则里的实体禁令）。
 */
class WorkSessionTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val start: Instant = Instant.parse("2026-10-14T01:00:00Z") // 北京时间 09:00
    private val end: Instant = Instant.parse("2026-10-14T10:00:00Z") // 北京时间 18:00

    private fun recorded() = WorkSession.recorded(WorkSessionId.random(), start, end, zone)

    @Test
    fun `AC-1 手工录入就是已确认_且分钟数正确`() {
        val session = recorded()

        assertEquals(SessionState.CONFIRMED, session.state)
        // 09:00–18:00 共 9 小时
        assertEquals(9 * 60, session.confirmedMinutes)
    }

    @Test
    fun `AC-4 归属日是它开始的那一天`() {
        // 北京时间 22:00（UTC 14:00）开始，次日 02:00 结束 → 归属**开始**那天
        val nightShift = WorkSession.recorded(
            WorkSessionId.random(),
            Instant.parse("2026-10-14T14:00:00Z"),
            Instant.parse("2026-10-14T18:00:00Z"),
            zone,
        )

        assertEquals(LocalDate.of(2026, 10, 14), nightShift.day)
        // ⚠️ 这是明确写下的局限：夜班跨天时"哪天算加班"v1 不拍板
    }

    @Test
    fun `未确认的时段分钟数恒为 0_不能变成工资`() {
        val running = WorkSession.restore(WorkSessionId.random(), start, null, SessionState.RUNNING, zone)
        val finished = WorkSession.restore(WorkSessionId.random(), start, end, SessionState.FINISHED, zone)
        val discarded = WorkSession.restore(WorkSessionId.random(), start, end, SessionState.DISCARDED, zone)

        assertEquals(0, running.confirmedMinutes)
        assertEquals(0, finished.confirmedMinutes)
        assertEquals(0, discarded.confirmedMinutes)
    }

    @Test
    fun `状态与结束时刻必须匹配`() {
        // 进行中**不许**有结束时刻（"还在跑"与"已经结束"是矛盾的）
        assertThrows(IllegalArgumentException::class.java) {
            WorkSession.restore(WorkSessionId.random(), start, end, SessionState.RUNNING, zone)
        }
        // 已确认**必须**有
        assertThrows(IllegalArgumentException::class.java) {
            WorkSession.restore(WorkSessionId.random(), start, null, SessionState.CONFIRMED, zone)
        }
        // 作废两可 —— 这条最初被我的不变量挡住了：跑到一半发现记错了直接作废，
        // 它从来没结束过。是下面那条状态转换用例把它逮出来的。
        assertEquals(
            SessionState.DISCARDED,
            WorkSession.restore(WorkSessionId.random(), start, null, SessionState.DISCARDED, zone).state,
        )
        assertEquals(
            SessionState.DISCARDED,
            WorkSession.restore(WorkSessionId.random(), start, end, SessionState.DISCARDED, zone).state,
        )
    }

    @Test
    fun `结束必须晚于开始`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkSession.recorded(WorkSessionId.random(), end, start, zone)
        }
        // 零长度也不行
        assertThrows(IllegalArgumentException::class.java) {
            WorkSession.recorded(WorkSessionId.random(), start, start, zone)
        }
    }

    @Test
    fun `状态转换只走允许的那几条路`() {
        val running = WorkSession.restore(WorkSessionId.random(), start, null, SessionState.RUNNING, zone)

        val finished = running.finish(end)
        assertEquals(SessionState.FINISHED, finished.state)

        val confirmed = finished.confirm()
        assertEquals(SessionState.CONFIRMED, confirmed.state)

        // 还在跑 / 已结束未确认 → 可以作废
        assertEquals(SessionState.DISCARDED, running.discard().state)
        assertEquals(SessionState.DISCARDED, finished.discard().state)

        // 已确认的**不能**再作废：它已经进过工资单的视野
        assertThrows(IllegalArgumentException::class.java) { confirmed.discard() }
        // 进行中的不能直接确认：先结束（否则没有 endedAt 可确认）
        assertThrows(IllegalArgumentException::class.java) { running.confirm() }
        // 已确认的不能再结束
        assertThrows(IllegalArgumentException::class.java) { confirmed.finish(end) }
    }

    @Test
    fun `身份必须是合法 UUID`() {
        assertThrows(IllegalArgumentException::class.java) { WorkSessionId("not-a-uuid") }
    }
}
