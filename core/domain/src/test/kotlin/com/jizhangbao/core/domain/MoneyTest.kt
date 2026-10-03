package com.jizhangbao.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `金额不可为负`() {
        // 方向由 EntryDirection 表达，Money 本身不接受负数
        assertFailsWith<IllegalArgumentException> { Money.ofCents(-1) }
        assertFailsWith<IllegalArgumentException> { Money.ofCents(-100) }
    }

    @Test
    fun `以分为单位运算不产生浮点误差`() {
        // 0.1 元累加 10 次必须精确等于 1 元；若用 Double 会得到 0.9999999999999999
        val oneJiao = Money.ofCents(10)

        val sum = (1..10).fold(Money.ZERO) { acc, _ -> acc + oneJiao }

        assertEquals(Money.ofCents(100), sum)
        assertEquals(Money.ofYuan(1), sum)
    }

    @Test
    fun `相同金额相等`() {
        assertEquals(Money.ofYuan(1), Money.ofCents(100))
        assertEquals(Money.ofYuan(1).hashCode(), Money.ofCents(100).hashCode())
    }

    @Test
    fun `减法不足时被拒绝而不是变成负数`() {
        val result = runCatching { Money.ofYuan(1) - Money.ofYuan(2) }

        assertTrue(result.isFailure)
        assertFailsWith<IllegalArgumentException> { Money.ofYuan(1) - Money.ofYuan(2) }
    }

    @Test
    fun `减法恰好为零时允许`() {
        assertEquals(Money.ZERO, Money.ofYuan(1) - Money.ofYuan(1))
    }

    @Test
    fun `展示格式不使用浮点`() {
        assertEquals("¥0.00", Money.ZERO.toString())
        assertEquals("¥1.00", Money.ofYuan(1).toString())
        assertEquals("¥1.05", Money.ofCents(105).toString())
        assertEquals("¥0.09", Money.ofCents(9).toString())
    }

    @Test
    fun `可比较大小`() {
        assertTrue(Money.ofYuan(2) > Money.ofYuan(1))
        assertTrue(Money.ofCents(99) < Money.ofYuan(1))
    }

    @Test
    fun `倍数不可为负`() {
        assertFailsWith<IllegalArgumentException> { Money.ofYuan(1) * -1 }
    }
}
