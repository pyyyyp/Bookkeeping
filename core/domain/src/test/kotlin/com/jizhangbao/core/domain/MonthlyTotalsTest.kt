package com.jizhangbao.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [MonthlyTotals] 的行为测试（`REQ-002/AC-1` `AC-2` `AC-3`）。
 *
 * 这里守的是**算术与单一事实源**：`net` 必须由 `income`/`expense` 算出，
 * 不能是一个可以被单独写错的字段。
 */
class MonthlyTotalsTest {

    private fun totals(incomeCents: Long, expenseCents: Long) =
        MonthlyTotals(Money.ofCents(incomeCents), Money.ofCents(expenseCents))

    @Test
    fun `结余等于收入减支出`() {
        // REQ-002/AC-1：8000.00 收入、37.50 支出 → 7962.50
        assertEquals(SignedMoney.ofCents(796_250), totals(800_000, 3_750).net)
    }

    @Test
    fun `支出大于收入时结余为负且负号可见`() {
        // REQ-002/AC-2：100.00 支出、30.00 收入 → -70.00
        val net = totals(3_000, 10_000).net

        assertTrue(net.isNegative)
        assertEquals("-¥70.00", net.toString())
    }

    @Test
    fun `空月的三项都是零`() {
        // REQ-002/AC-3：没有记账是正常的零，不是"没有数据"
        assertEquals(Money.ZERO, MonthlyTotals.ZERO.income)
        assertEquals(Money.ZERO, MonthlyTotals.ZERO.expense)
        assertEquals(SignedMoney.ZERO, MonthlyTotals.ZERO.net)
        assertFalse(MonthlyTotals.ZERO.net.isNegative)
    }

    @Test
    fun `收支相等时结余为零且不算超支`() {
        val net = totals(1_000, 1_000).net

        assertEquals(SignedMoney.ZERO, net)
        assertFalse(net.isNegative)
    }

    @Test
    fun `相等性只看收入与支出`() {
        // net 是算出来的派生值，不参与身份
        assertEquals(totals(1_000, 500), totals(1_000, 500))
        assertFalse(totals(1_000, 500) == totals(1_000, 501))
    }
}
