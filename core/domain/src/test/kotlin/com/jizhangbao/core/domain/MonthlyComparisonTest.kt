package com.jizhangbao.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 月度环比（`REQ-009`）。
 *
 * 四条分支各自一条用例：多花 / 少花 / 持平 / 上月为空。
 * 这些是**纯函数**，所以能直接钉死 —— 如果把它们散在 Compose 里做减法，
 * 就没有地方能这样测。
 */
class MonthlyComparisonTest {

    private fun totals(expenseCents: Long, incomeCents: Long = 0) =
        MonthlyTotals(income = Money.ofCents(incomeCents), expense = Money.ofCents(expenseCents))

    @Test
    fun `本月花得更多时差额为正`() {
        val comparison = MonthlyComparison(current = totals(40_045), previous = totals(1_000))

        assertEquals(39_045L, comparison.expenseDelta.cents)
        assertFalse(comparison.expenseDelta.isNegative)
        assertFalse(comparison.isUnchanged)
    }

    @Test
    fun `本月花得更少时差额为负_展示上必须带负号`() {
        val comparison = MonthlyComparison(current = totals(1_000), previous = totals(40_045))

        assertEquals(-39_045L, comparison.expenseDelta.cents)
        assertTrue(comparison.expenseDelta.isNegative)
        // 少一个负号，用户会把"少花"看成"多花" —— 与结余那条同理（REQ-002/AC-2）
        assertEquals("-¥390.45", comparison.expenseDelta.toString())
    }

    @Test
    fun `两个月持平`() {
        val comparison = MonthlyComparison(current = totals(1_000, incomeCents = 5_000), previous = totals(1_000))

        assertEquals(0L, comparison.expenseDelta.cents)
        assertTrue(comparison.isUnchanged)
    }

    @Test
    fun `上月没有记账时基线是零_差额就是本月全额`() {
        val comparison = MonthlyComparison(current = totals(40_045), previous = MonthlyTotals.ZERO)

        // AC-2：这**不是错误**，是事实 —— 上个月真的一分没花
        assertEquals(40_045L, comparison.expenseDelta.cents)
        assertTrue(comparison.previousIsEmpty)
    }

    @Test
    fun `只比支出_上月有收入但没支出时仍算基线为空`() {
        // 这条钉住"环比只回答'我花得多了吗'"：收入不影响它
        val comparison = MonthlyComparison(
            current = totals(1_000),
            previous = totals(expenseCents = 0, incomeCents = 800_000),
        )

        assertEquals(0L, comparison.expenseDelta.cents)
        assertTrue(comparison.isUnchanged)
        // 上月有收入 → 不是"没有记账"，所以 previousIsEmpty 为假
        assertFalse(comparison.previousIsEmpty)
    }
}
