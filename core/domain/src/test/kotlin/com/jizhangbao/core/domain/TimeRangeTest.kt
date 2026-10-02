package com.jizhangbao.core.domain

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeRangeTest {

    private val t0: Instant = Instant.parse("2026-01-01T09:00:00Z")

    private fun at(secondsFromStart: Long): Instant = t0.plusSeconds(secondsFromStart)

    @Test
    fun `结束早于开始时被拒绝`() {
        assertFailsWith<IllegalArgumentException> { TimeRange(at(3600), at(0)) }
    }

    @Test
    fun `零长度区间被拒绝`() {
        assertFailsWith<IllegalArgumentException> { TimeRange(t0, t0) }
    }

    @Test
    fun `时长计算正确`() {
        val session = TimeRange(at(0), at(5400))

        assertEquals(Duration.ofMinutes(90), session.duration)
    }

    @Test
    fun `重叠判定为半开区间`() {
        val morning = TimeRange(at(0), at(3 * 3600))      // 09:00 - 12:00

        // 首尾相接不算重叠：12:00 开始的下午班与上午班不重叠
        val afternoon = TimeRange(at(3 * 3600), at(4 * 3600))
        assertFalse(morning.overlaps(afternoon))
        assertFalse(afternoon.overlaps(morning))

        // 真正重叠
        val overlapping = TimeRange(at(2 * 3600), at(5 * 3600))
        assertTrue(morning.overlaps(overlapping))
        assertTrue(overlapping.overlaps(morning))

        // 完全包含也算重叠
        val contained = TimeRange(at(3600), at(2 * 3600))
        assertTrue(morning.overlaps(contained))
    }

    @Test
    fun `包含判定含起点不含终点`() {
        val session = TimeRange(at(0), at(3600))

        assertTrue(session.contains(at(0)))
        assertTrue(session.contains(at(1800)))
        assertFalse(session.contains(at(3600)))
        assertFalse(session.contains(at(-1)))
    }

    @Test
    fun `完整包含另一区间`() {
        val whole = TimeRange(at(0), at(8 * 3600))
        val part = TimeRange(at(3600), at(2 * 3600))

        assertTrue(whole.contains(part))
        assertFalse(part.contains(whole))
    }
}
