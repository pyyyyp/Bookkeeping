package com.jizhangbao.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 外部数据文件的解析（`REQ-012/AC-5`）。
 *
 * 这里的断言几乎都是"**坏输入不毁掉好数据**"：
 * 这份文件一年只被人手改一次，而改它的人（包括将来的我）一定会写错几行。
 */
class HolidayDataMapperTest {

    @Test
    fun `两类日期都认_并按年份分组`() {
        val result = HolidayDataMapper.parse(
            """
            2026-10-01 holiday
            2026-10-10 workday
            2027-01-01 holiday
            """.trimIndent(),
        )

        assertEquals(setOf(2026, 2027), result.years.keys)
        assertEquals(setOf(LocalDate.of(2026, 10, 1)), result.years.getValue(2026).holidays)
        assertEquals(setOf(LocalDate.of(2026, 10, 10)), result.years.getValue(2026).workdays)
        assertEquals(setOf(LocalDate.of(2027, 1, 1)), result.years.getValue(2027).holidays)
        assertEquals(0, result.skippedLines)
    }

    @Test
    fun `注释与空行被忽略`() {
        val result = HolidayDataMapper.parse(
            """
            # 来源：国务院办公厅《关于 2026 年部分节假日安排的通知》

            2026-10-01 holiday   # 国庆
            """.trimIndent(),
        )

        assertEquals(1, result.years.getValue(2026).holidays.size)
        assertEquals(0, result.skippedLines)
    }

    @Test
    fun `坏行被跳过并计数_好行照常生效`() {
        val result = HolidayDataMapper.parse(
            """
            2026-10-01 holiday
            这不是一个日期 holiday
            2026-10-02
            2026-10-03 说不清的词
            2026-10-04 workday
            """.trimIndent(),
        )

        // 三个坏行：坏日期 / 只有一个词 / 认不出的类型
        assertEquals(3, result.skippedLines)
        // 两行好的照常生效 —— **一行坏不毁掉一整年**
        assertEquals(setOf(LocalDate.of(2026, 10, 1)), result.years.getValue(2026).holidays)
        assertEquals(setOf(LocalDate.of(2026, 10, 4)), result.years.getValue(2026).workdays)
    }

    @Test
    fun `空文件没有任何年份_于是所有年份都是missingYear`() {
        val result = HolidayDataMapper.parse("")

        assertTrue(result.years.isEmpty())
        assertEquals(0, result.skippedLines)
    }

    @Test
    fun `只有注释的文件同样没有任何年份`() {
        val result = HolidayDataMapper.parse(
            """
            # 今年还没填
            """.trimIndent(),
        )

        assertTrue(result.years.isEmpty())
    }

    @Test
    fun `一个年份可能只有调休没有节假日_也算有数据`() {
        val result = HolidayDataMapper.parse("2026-10-10 workday")

        assertEquals(2026, result.years.getValue(2026).year)
        assertTrue(result.years.getValue(2026).holidays.isEmpty())
    }
}
