package com.jizhangbao.calendar.domain

import com.jizhangbao.core.domain.DayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 判定规则（`REQ-012`）。
 *
 * ⚠️ 夹具日期是**从星期几推出来的**，不是写死的具体日子：
 * 这条规则的核心恰恰是"星期几不等于日期类型"，若测试自己依赖"2026-10-01 是周四"，
 * 那它验证的就不是规则，而是我对某个具体日期的记忆。
 */
class DayTypeRulesTest {

    /** 任意一个周三（工作日）。 */
    private val aWednesday: LocalDate =
        LocalDate.of(2026, 10, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))

    /** 任意一个周六（休息日）。 */
    private val aSaturday: LocalDate =
        LocalDate.of(2026, 10, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))

    /** 被调休成工作日的那个周六。 */
    private val adjustedSaturday: LocalDate =
        aSaturday.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))

    private val holiday = aWednesday

    private val year = HolidayYear(
        year = 2026,
        holidays = setOf(holiday),
        workdays = setOf(adjustedSaturday),
    )

    @Test
    fun `AC-1 覆盖表里的日期是法定节假日`() {
        val result = typeOf(holiday, year)

        assertEquals(DayType.STATUTORY_HOLIDAY, result.type)
        assertFalse("有数据时不该标 missingYear", result.missingYear)
    }

    @Test
    fun `AC-2 被调休的周六是工作日`() {
        val result = typeOf(adjustedSaturday, year)

        // 这条断言的要点：**星期几与日期类型不一致时，以日期类型为准**。
        // 按星期几判会得到 REST_DAY，而工资的倍数因此差一倍。
        assertEquals(DayOfWeek.SATURDAY, adjustedSaturday.dayOfWeek)
        assertEquals(DayType.WORKDAY, result.type)
    }

    @Test
    fun `AC-3 没有标注的周末是休息日_没有标注的工作日是工作日`() {
        assertEquals(DayType.REST_DAY, typeOf(aSaturday, year).type)
        assertEquals(DayType.WORKDAY, typeOf(aWednesday.plusWeeks(1), year).type)
    }

    @Test
    fun `AC-4 没有这一年的数据时退化为周末规则_并且说出来`() {
        val weekend = typeOf(aSaturday, year = null)
        val weekday = typeOf(aWednesday, year = null)

        // 类型仍按周末规则给出……
        assertEquals(DayType.REST_DAY, weekend.type)
        assertEquals(DayType.WORKDAY, weekday.type)
        // ……但必须**说清楚它是推的**，否则用户会以为"今年没有节假日"
        assertTrue(weekend.missingYear)
        assertTrue(weekday.missingYear)
    }

    @Test
    fun `同一天同时被标成两类时_法定节假日优先`() {
        // 数据填错时不该静默取一个：这里的顺序是**明写**的规则（ADR-0011 决策 3），
        // 而这条测试就是那条规则的守门人
        val both = HolidayYear(year = 2026, holidays = setOf(aSaturday), workdays = setOf(aSaturday))

        assertEquals(DayType.STATUTORY_HOLIDAY, typeOf(aSaturday, both).type)
    }
}
