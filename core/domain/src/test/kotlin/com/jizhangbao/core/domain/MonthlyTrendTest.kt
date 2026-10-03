package com.jizhangbao.core.domain

import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 月度趋势的读模型（`REQ-010`）。
 *
 * 这一层几乎是纯数据，但 [MonthlyTrend.latest] 值得钉住：`AC-2` 要求
 * "趋势里当月的支出 == 合计区的支出"，而它靠的就是**同一个值对象**，
 * 不是两处各自算一遍。
 */
class MonthlyTrendTest {

    private val august = YearMonth.of(2026, 8)
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)

    private fun totalsOf(expenseCents: Long) =
        MonthlyTotals(income = Money.ZERO, expense = Money.ofCents(expenseCents))

    @Test
    fun `从新到旧_最新那个月在最前`() {
        val trend = MonthlyTrend(
            listOf(
                TrendPoint(october, totalsOf(40_045)),
                TrendPoint(september, totalsOf(1_000)),
                TrendPoint(august, totalsOf(2_000)),
            ),
        )

        assertEquals(october, trend.points.first().month)
        assertEquals(august, trend.points.last().month)
    }

    @Test
    fun `latest 就是最新那个月的合计`() {
        val trend = MonthlyTrend(
            listOf(TrendPoint(october, totalsOf(40_045)), TrendPoint(september, totalsOf(1_000))),
        )

        // AC-2 靠它：界面上的趋势最新一行与合计区是同一个数字
        assertEquals(Money.ofCents(40_045), trend.latest?.expense)
    }

    @Test
    fun `空趋势没有 latest_也不会崩`() {
        assertTrue(MonthlyTrend.EMPTY.isEmpty)
        assertNull(MonthlyTrend.EMPTY.latest)
    }

    @Test
    fun `没有记账的月份是零而不是缺项`() {
        val trend = MonthlyTrend(
            listOf(TrendPoint(october, totalsOf(40_045)), TrendPoint(september, MonthlyTotals.ZERO)),
        )

        // AC-3：¥0.00 要如实显示 —— 那一行在，只是值是零
        assertEquals(2, trend.points.size)
        assertEquals(Money.ZERO, trend.points[1].totals.expense)
    }
}
