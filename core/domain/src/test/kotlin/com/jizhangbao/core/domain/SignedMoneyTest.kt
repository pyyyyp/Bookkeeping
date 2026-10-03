package com.jizhangbao.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SignedMoney] 的行为测试。
 *
 * 重点是**负号**：它是唯一一处"格式即语义"的地方——少一个负号，
 * 用户会把超支看成结余（`REQ-002/AC-2`）。
 */
class SignedMoneyTest {

    @Test
    fun `零的展示不带符号`() {
        assertEquals("¥0.00", SignedMoney.ZERO.toString())
    }

    @Test
    fun `正数的展示不带正号`() {
        assertEquals("¥70.00", SignedMoney.ofCents(7_000).toString())
    }

    @Test
    fun `负数的展示带负号`() {
        assertEquals("-¥70.00", SignedMoney.ofCents(-7_000).toString())
    }

    @Test
    fun `不足一元的负数也保留两位小数`() {
        assertEquals("-¥0.05", SignedMoney.ofCents(-5).toString())
    }

    @Test
    fun `isNegative 区分超支与结余`() {
        assertTrue(SignedMoney.ofCents(-1).isNegative)
        assertFalse(SignedMoney.ZERO.isNegative)
        assertFalse(SignedMoney.ofCents(1).isNegative)
    }

    @Test
    fun `加减法按分计算`() {
        val a = SignedMoney.ofCents(1_000)
        val b = SignedMoney.ofCents(2_500)

        assertEquals(SignedMoney.ofCents(3_500), a + b)
        assertEquals(SignedMoney.ofCents(-1_500), a - b)
    }
}
