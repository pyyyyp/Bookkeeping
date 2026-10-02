package com.jizhangbao.ledger.data.totals

import com.jizhangbao.core.domain.CategoryBreakdown
import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.TimeRange
import com.jizhangbao.ledger.data.local.LedgerEntryEntity
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.testing.FakeCategoryRepository
import com.jizhangbao.ledger.testing.FakeLedgerEntryDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 分类占比的读取器实现测试（`REQ-005`）。
 *
 * 本卡最要紧的一条在这里被钉住：**已归档的分类照样计入**（`BR-2`）。
 * 少算它，各分类之和就不等于支出合计 —— 两个数字同屏，用户一眼看得出。
 *
 * ⚠️ SQL 本身（`GROUP BY`、`COALESCE`、半开区间）不在这里验证：用的是 fake DAO。
 * 它由真机冒烟与 Room 的编译期校验覆盖。
 */
class LedgerExpensesByCategoryTest {

    private val dao = FakeLedgerEntryDao()
    private val categories = FakeCategoryRepository()
    private val reader = LedgerTotalsReaderImpl(dao, categories)

    private val october = TimeRange(
        start = Instant.parse("2026-10-01T00:00:00Z"),
        end = Instant.parse("2026-11-01T00:00:00Z"),
    )

    private fun row(
        id: String,
        categoryId: String,
        amountCents: Long,
        direction: EntryDirection = EntryDirection.Expense,
        occurredAt: Instant = Instant.parse("2026-10-05T00:00:00Z"),
    ) = LedgerEntryEntity(
        id = id,
        direction = direction.name,
        amountCents = amountCents,
        categoryId = categoryId,
        occurredAtEpochMilli = occurredAt.toEpochMilli(),
        bookedAtEpochMilli = occurredAt.toEpochMilli(),
        note = null,
    )

    private suspend fun custom(id: String, name: String, archived: Boolean = false) {
        categories.add(
            Category.restore(CategoryId(id), name, setOf(EntryDirection.Expense), archived),
        )
    }

    @Test
    fun `按分类汇总并解析出显示名`() = runBlocking {
        custom("custom-pet", "宠物")
        dao.inserted += row("a", "custom-pet", 3_000)
        dao.inserted += row("b", "food", 1_000)

        val breakdown = (reader.expensesByCategory(october) as Outcome.Ok).value

        // 金额降序（BR-4）
        assertEquals(listOf("宠物", "餐饮"), breakdown.rows.map { it.categoryName })
        assertEquals(4_000L, breakdown.total.cents)
        assertEquals("75.0%", breakdown.shareOf(breakdown.rows.first()).toString())
    }

    @Test
    fun `已归档的分类照样计入_否则各分类之和与合计对不上`() = runBlocking {
        custom("custom-old", "话费", archived = true)
        custom("custom-pet", "宠物")
        dao.inserted += row("a", "custom-old", 2_500)
        dao.inserted += row("b", "custom-pet", 7_500)

        val breakdown = (reader.expensesByCategory(october) as Outcome.Ok).value

        // BR-2：归档的含义是"记账时别再让我选它"，不是"从历史里抹掉"
        assertEquals(2, breakdown.rows.size)
        assertEquals(10_000L, breakdown.total.cents)
        assertEquals(2_500L + 7_500L, breakdown.rows.sumOf { it.amount.cents })
        assertEquals("话费", breakdown.rows.last().categoryName)
    }

    @Test
    fun `收入不进占比清单`() = runBlocking {
        dao.inserted += row("a", "food", 1_000, direction = EntryDirection.Income)

        val breakdown = (reader.expensesByCategory(october) as Outcome.Ok).value

        // BR-1：分母只是支出合计
        assertTrue(breakdown.isEmpty)
    }

    @Test
    fun `范围外的条目不进来`() = runBlocking {
        dao.inserted += row("a", "food", 1_000, occurredAt = Instant.parse("2026-09-30T23:59:59Z"))
        dao.inserted += row("b", "food", 2_000, occurredAt = october.end)

        val breakdown = (reader.expensesByCategory(october) as Outcome.Ok).value

        // 半开区间 [start, end)：9 月那笔不算；正好等于 end 的那笔也不算
        assertTrue(breakdown.isEmpty)
    }

    @Test
    fun `没有支出时是空清单而不是错误`() = runBlocking {
        val result = reader.expensesByCategory(october)

        assertEquals(CategoryBreakdown.EMPTY, (result as Outcome.Ok).value)
    }

    @Test
    fun `存储失败时返回存储错误`() = runBlocking {
        dao.failure = IllegalStateException("disk full")

        assertEquals(
            DomainError.Technical.Storage,
            (reader.expensesByCategory(october) as Outcome.Err).error,
        )
    }
}
