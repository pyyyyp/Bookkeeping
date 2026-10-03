package com.jizhangbao.worklog.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 取出勤事实（`REQ-014/AC-2`、`AC-3`、`AC-5`）。
 *
 * 两条最有价值的断言：**还在跑的时段不算**，以及**两段相加而不是取最外层跨度**。
 */
class AttendedDaysTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 10, 14)
    private val from = LocalDate.of(2026, 10, 1)
    private val to = LocalDate.of(2026, 10, 31)

    /** 北京时间某天的 `hour:00`。 */
    private fun at(hour: Int, date: LocalDate = day): Instant =
        date.atTime(hour, 0).atZone(zone).toInstant()

    private fun confirmed(startHour: Int, endHour: Int, date: LocalDate = day) =
        WorkSession.recorded(WorkSessionId.random(), at(startHour, date), at(endHour, date), zone)

    @Test
    fun `AC-2 只有已确认的时段计入`() {
        val confirmedOne = confirmed(9, 12)
        val running = WorkSession.restore(WorkSessionId.random(), at(13), null, SessionState.RUNNING, zone)
        val finished = WorkSession.restore(WorkSessionId.random(), at(13), at(18), SessionState.FINISHED, zone)
        val discarded = WorkSession.restore(WorkSessionId.random(), at(13), at(18), SessionState.DISCARDED, zone)

        val result = AttendedDays.from(listOf(confirmedOne, running, finished, discarded), from, to)

        // 只有那一段已确认的算 —— 一个还在跑的时段不能变成工资
        assertEquals(1, result.size)
        assertEquals(3 * 60, result.single().confirmedMinutes)
    }

    @Test
    fun `AC-3 同一天多段相加_不是取最外层跨度`() {
        val morning = confirmed(9, 12)
        val afternoon = confirmed(13, 18)

        val result = AttendedDays.from(listOf(morning, afternoon), from, to)

        // 8 小时（3 + 5），**不是** 9 小时（9:00–18:00 的外层跨度，会把午饭算成工时）
        assertEquals(8 * 60, result.single().confirmedMinutes)
    }

    @Test
    fun `AC-5 没有已确认时段的日期不出现在结果里`() {
        val result = AttendedDays.from(
            listOf(
                WorkSession.restore(WorkSessionId.random(), at(9), null, SessionState.RUNNING, zone),
                WorkSession.restore(
                    WorkSessionId.random(),
                    at(9, day.plusDays(1)),
                    at(18, day.plusDays(1)),
                    SessionState.DISCARDED,
                    zone,
                ),
            ),
            from,
            to,
        )

        // 空，而不是"两条 0 分钟的记录" —— "这天没记录"与"这天出勤 0 分钟"不是一回事
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `按日期升序_并只取区间内的那些天`() {
        val earlier = confirmed(9, 18, day.minusDays(2))
        val later = confirmed(9, 18, day.plusDays(1))
        val outside = confirmed(9, 18, to.plusDays(1))

        val result = AttendedDays.from(listOf(later, earlier, outside), from, to)

        assertEquals(listOf(day.minusDays(2), day.plusDays(1)), result.map { it.date })
    }

    @Test
    fun `不足一分钟的时段只是噪声_不算一天出勤`() {
        val tooShort = WorkSession.recorded(
            WorkSessionId.random(),
            at(9),
            at(9).plusSeconds(30),
            zone,
        )

        assertEquals(emptyList<Any>(), AttendedDays.from(listOf(tooShort), from, to))
    }
}
