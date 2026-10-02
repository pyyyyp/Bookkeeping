package com.jizhangbao.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 分类占比的读模型测试（`REQ-005`）。
 *
 * 这些规则住内核而不是 SQL 或界面里，最直接的好处就是**这一页能纯 JVM 跑**：
 * 排序、四舍五入、"合计为 0 就空清单"都不需要数据库或设备。
 *
 * ⚠️ 本模块用 `kotlin-test`（不是 JUnit4）——`:core:domain` 是纯 Kotlin JVM 模块。
 */
class CategoryBreakdownTest {

    private fun row(name: String, cents: Long): CategoryAmount =
        CategoryAmount(categoryName = name, amount = Money.ofCents(cents))

    @Test
    fun `三个等额分类各占 33 点 3 百分比_加起来不等于 100 是正确的`() {
        val breakdown = CategoryBreakdown.of(
            rows = listOf(row("餐饮", 1_000), row("交通", 1_000), row("购物", 1_000)),
            total = Money.ofCents(3_000),
        )

        // BR-3：各自四舍五入。为了凑满 100% 必须改动某一类的数字，那比 99.9% 糟得多
        breakdown.rows.forEach { assertEquals("33.3%", breakdown.shareOf(it).toString()) }
        val sumOfTenths = breakdown.rows.sumOf { breakdown.shareOf(it).tenths }
        assertEquals(999, sumOfTenths)
    }

    @Test
    fun `按金额降序_金额相同时按分类名`() {
        val breakdown = CategoryBreakdown.of(
            rows = listOf(row("交通", 300), row("餐饮", 1_200), row("购物", 300)),
            total = Money.ofCents(1_800),
        )

        // 金额相同也要有确定顺序：否则同一份数据两次渲染可能不同
        assertEquals(listOf("餐饮", "交通", "购物"), breakdown.rows.map { it.categoryName })
    }

    @Test
    fun `单个分类占 100 百分比`() {
        val breakdown = CategoryBreakdown.of(listOf(row("餐饮", 1_234)), Money.ofCents(1_234))

        assertEquals("100.0%", breakdown.shareOf(breakdown.rows.single()).toString())
    }

    @Test
    fun `合计为零时是空清单_而不是每类零或错误`() {
        // BR-6：没有支出是正常状态。也给不出 0/0 的占比
        assertEquals(CategoryBreakdown.EMPTY, CategoryBreakdown.of(emptyList(), Money.ZERO))
        assertTrue(CategoryBreakdown.of(emptyList(), Money.ZERO).isEmpty)
    }

    @Test
    fun `有合计但没有行时也是空清单`() {
        assertTrue(CategoryBreakdown.of(emptyList(), Money.ofCents(500)).isEmpty)
    }

    @Test
    fun `等于零的行也参与占比_它是零百分比而不是被丢掉`() {
        val breakdown = CategoryBreakdown.of(
            rows = listOf(row("餐饮", 1_000), row("其他", 0)),
            total = Money.ofCents(1_000),
        )

        assertEquals(2, breakdown.rows.size)
        assertEquals("0.0%", breakdown.shareOf(breakdown.rows.last()).toString())
    }

    @Test
    fun `四舍五入到一位小数_半进位`() {
        // 1/8 = 12.5% 正好一位小数；1/16 = 6.25% → 6.3%（四舍五入）
        val breakdown = CategoryBreakdown.of(
            rows = listOf(row("甲", 1), row("乙", 15)),
            total = Money.ofCents(16),
        )

        assertEquals("6.3%", breakdown.shareOf(breakdown.rows.last()).toString())
        assertEquals("93.8%", breakdown.shareOf(breakdown.rows.first()).toString())
    }

    @Test
    fun `相等性按值`() {
        val a = CategoryBreakdown.of(listOf(row("餐饮", 100)), Money.ofCents(100))
        val b = CategoryBreakdown.of(listOf(row("餐饮", 100)), Money.ofCents(100))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `占比拒绝零合计与负金额`() {
        assertFailsWith<IllegalArgumentException> { Percentage.of(100, 0) }
        assertFailsWith<IllegalArgumentException> { Percentage.of(-1, 100) }
        assertFailsWith<IllegalArgumentException> { Percentage.ofTenths(-1) }
    }

    @Test
    fun `分类名为空的一行不合法`() {
        assertFailsWith<IllegalArgumentException> { CategoryAmount(categoryName = "  ", amount = Money.ZERO) }
    }
}
