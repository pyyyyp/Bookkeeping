package com.jizhangbao.worklog.data.local

import com.jizhangbao.worklog.domain.SessionState
import com.jizhangbao.worklog.domain.WorkSessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 实体 ↔ 领域（`REQ-014/AC-6` 的一半：**存得住**）。
 *
 * 这里最要紧的一条：**归属日不存储、靠推导**，所以往返之后
 * `day` 必须仍然等于"开始那天" —— 它在库里根本没有对应的列。
 */
class WorkSessionMapperTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val id = WorkSessionId.random()
    private val startedAt: Instant = Instant.parse("2026-10-14T01:00:00Z") // 北京时间 09:00
    private val endedAt: Instant = Instant.parse("2026-10-14T10:00:00Z") // 北京时间 18:00

    @Test
    fun `已确认的时段往返后状态与时刻都不变`() {
        val original = com.jizhangbao.worklog.domain.WorkSession
            .recorded(id, startedAt, endedAt, zone)

        val restored = WorkSessionMapper.toDomain(WorkSessionMapper.toEntity(original), zone)

        assertEquals(SessionState.CONFIRMED, restored.state)
        assertEquals(startedAt, restored.startedAt)
        assertEquals(endedAt, restored.endedAt)
        assertEquals(original.confirmedMinutes, restored.confirmedMinutes)
        // ⚠️ 归属日是推导出来的：库里没有这一列，往返后仍必须一致
        assertEquals(LocalDate.of(2026, 10, 14), restored.day)
        assertEquals(original.day, restored.day)
    }

    @Test
    fun `进行中的时段往返后仍然没有结束时刻`() {
        // "结束时刻为空"是状态机的一半不变量，必须原样保留（AC-6 的原话）
        val running = com.jizhangbao.worklog.domain.WorkSession
            .restore(id, startedAt, null, SessionState.RUNNING, zone)

        val entity = WorkSessionMapper.toEntity(running)
        assertNull(entity.endedAtEpochMilli)

        val restored = WorkSessionMapper.toDomain(entity, zone)
        assertEquals(SessionState.RUNNING, restored.state)
        assertNull(restored.endedAt)
        assertEquals(0, restored.confirmedMinutes)
    }

    @Test
    fun `状态存的是枚举名而不是序号`() {
        val session = com.jizhangbao.worklog.domain.WorkSession
            .recorded(id, startedAt, endedAt, zone)

        // 存序号的话，枚举顺序一变，历史数据的含义就会悄悄错位
        assertEquals("CONFIRMED", WorkSessionMapper.toEntity(session).state)
    }
}
