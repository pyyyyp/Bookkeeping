package com.jizhangbao.ledger.domain.model

import com.jizhangbao.core.domain.EntryDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预置分类清单 —— `REQ-001/AC-4`（必须能选到分类）与 `BR-6`（本卡只读）。
 *
 * 这些断言的意义：清单是**业务内容**，改动它应当让测试红一次，
 * 而不是悄无声息地改变「用户能选到什么」。
 */
class CategoryCatalogTest {

    private val catalog = CategoryCatalog.PRESET

    @Test
    fun `预置分类的标识互不重复`() {
        val ids = catalog.all().map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `支出可选到餐饮但选不到工资`() {
        val expenseNames = catalog.forDirection(EntryDirection.Expense).map { it.displayName }

        assertTrue("餐饮" in expenseNames)
        assertTrue("工资" !in expenseNames)
    }

    @Test
    fun `收入可选到工资但选不到餐饮`() {
        val incomeNames = catalog.forDirection(EntryDirection.Income).map { it.displayName }

        assertTrue("工资" in incomeNames)
        assertTrue("餐饮" !in incomeNames)
    }

    @Test
    fun `其他在两个方向都能选到`() {
        val other = catalog.byId(CategoryId("other"))

        assertTrue(other != null)
        assertTrue(other!!.supports(EntryDirection.Expense))
        assertTrue(other.supports(EntryDirection.Income))
    }

    @Test
    fun `未知分类标识查不到`() {
        assertNull(catalog.byId(CategoryId("not-a-category")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `标识重复的分类清单在构造时就被拒绝`() {
        // 同一个标识不能代表两个分类；否则历史条目会指向「哪一个餐饮」变得不确定
        CategoryCatalog(
            listOf(
                preset("food", "餐饮"),
                preset("food", "吃饭"),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `展示名为空白的分类在构造时就被拒绝`() {
        preset("food", "   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `不支持任何方向的分类在构造时就被拒绝`() {
        preset("food", "餐饮", emptySet())
    }

    /**
     * 构造一个内置分类。
     *
     * `REQ-004` 之后 `Category` 是聚合（多一个 `archived` 状态），
     * 而这些测试要的是"**可信常量**走 restore 路径"这层语义 —— 与 `CategoryCatalog.PRESET` 一致。
     */
    private fun preset(
        id: String,
        name: String,
        directions: Set<EntryDirection> = setOf(EntryDirection.Expense),
    ): Category = Category.restore(CategoryId(id), name, directions, archived = false)
}
