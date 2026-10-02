package com.jizhangbao.ledger.presentation

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryCatalog
import com.jizhangbao.ledger.domain.model.CategoryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 界面状态里**分类相关派生逻辑**的测试（`REQ-004`）。
 *
 * 这些逻辑值得单测，而不是只靠真机冒烟：它们是 `AC-3` 的全部内容 ——
 * "归档的进不了选择器、但名字还得显示得出来"。
 * 一个真机冒烟很难稳定覆盖"归档之后选择器里没有它"（要点开对话框、点归档、再回来看），
 * 而这里是几行断言。
 */
class LedgerUiStateCategoryTest {

    private val now: Instant = Instant.parse("2026-10-02T03:04:05Z")

    private fun custom(id: String, archived: Boolean = false): Category = Category.restore(
        id = CategoryId(id),
        name = id,
        directions = setOf(EntryDirection.Expense),
        archived = archived,
    )

    private fun state(
        categories: List<Category>,
        direction: EntryDirection = EntryDirection.Expense,
    ): LedgerUiState = LedgerUiState.initial(now).copy(
        allCategories = categories,
        direction = direction,
    )

    @Test
    fun `可选分类里没有已归档的`() {
        val active = custom("custom-pet")
        val archived = custom("custom-old", archived = true)

        val selectable = state(listOf(active, archived)).selectableCategories

        assertTrue(selectable.contains(active))
        // AC-3：归档之后它不该再出现在记账选择器里
        assertFalse(selectable.contains(archived))
    }

    @Test
    fun `可选分类只保留支持当前方向的`() {
        val expenseOnly = custom("custom-pet")
        val incomeOnly = Category.restore(
            id = CategoryId("custom-salary2"),
            name = "外快",
            directions = setOf(EntryDirection.Income),
            archived = false,
        )
        val both = state(listOf(expenseOnly, incomeOnly))

        assertEquals(listOf(expenseOnly), both.selectableCategories)
        assertEquals(listOf(incomeOnly), both.copy(direction = EntryDirection.Income).selectableCategories)
    }

    @Test
    fun `预置分类仍然出现在可选清单里`() {
        val selectable = state(CategoryCatalog.PRESET.all()).selectableCategories

        // 8 个预置里有 7 个支持支出（工资只支持收入）
        assertEquals(7, selectable.size)
    }

    @Test
    fun `显示名能解析已归档分类_否则历史条目会显示成标识`() {
        val archived = custom("custom-old", archived = true)

        // 这一条与"可选分类过滤掉归档"是一对：选择器不要它，但**历史要显示得出来**
        assertEquals("custom-old", state(listOf(archived)).categoryName(archived.id))
    }

    @Test
    fun `显示名查不到时退回标识本身`() {
        // 不返回空串：至少能看出是哪一条，也不会让界面看起来像渲染坏了
        assertEquals("custom-nope", state(emptyList()).categoryName(CategoryId("custom-nope")))
    }

    @Test
    fun `初始状态就带预置分类_读库之前界面也不该是空的`() {
        val initial = LedgerUiState.initial(now)

        assertEquals(CategoryCatalog.PRESET.all().size, initial.allCategories.size)
        assertTrue(initial.selectableCategories.isNotEmpty())
    }
}
