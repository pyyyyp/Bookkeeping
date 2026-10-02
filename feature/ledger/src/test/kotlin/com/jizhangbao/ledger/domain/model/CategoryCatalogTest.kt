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
                Category(CategoryId("food"), "餐饮", setOf(EntryDirection.Expense)),
                Category(CategoryId("food"), "吃饭", setOf(EntryDirection.Expense)),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `展示名为空白的分类在构造时就被拒绝`() {
        Category(CategoryId("food"), "   ", setOf(EntryDirection.Expense))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `不支持任何方向的分类在构造时就被拒绝`() {
        Category(CategoryId("food"), "餐饮", emptySet())
    }
}
