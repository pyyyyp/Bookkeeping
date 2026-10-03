package com.jizhangbao.ledger.data.repository

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.CategoryName
import com.jizhangbao.ledger.testing.FakeCategoryDao
import com.jizhangbao.ledger.testing.RecordingLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类仓储实现的测试（`REQ-004`）。
 *
 * 重点两处：
 * - **映射**：方向集合 ⇄ 逗号分隔字符串的往返（排序后拼接，保证同一个集合得到同一串字符）；
 * - **0 行语义**：更新一个不存在的分类返回 `CategoryNotFound`，而不是假装成功。
 *
 * ⚠️ 走的是 fake DAO，所以建表/写入/更新的 SQL 不在这里验证；
 * 迁移与 SQL 由**真机装新版本**验证（`REQ-004/AC-9`）。
 */
class CategoryRepositoryImplTest {

    private val dao = FakeCategoryDao()
    private val repository = CategoryRepositoryImpl(dao, RecordingLogger())

    private fun category(
        id: String = "custom-pet",
        name: String = "宠物",
        directions: Set<EntryDirection> = setOf(EntryDirection.Expense),
        archived: Boolean = false,
    ): Category = Category.restore(CategoryId(id), name, directions, archived)

    @Test
    fun `新增后可以查出来`() = runBlocking {
        val result = repository.add(category())

        assertEquals(Outcome.Ok(Unit), result)
        assertEquals("宠物", repository.all().let { (it as Outcome.Ok).value.single().displayName })
    }

    @Test
    fun `方向集合往返不丢信息`() = runBlocking {
        repository.add(category(directions = setOf(EntryDirection.Income, EntryDirection.Expense)))

        val stored = (repository.all() as Outcome.Ok).value.single()

        assertEquals(setOf(EntryDirection.Income, EntryDirection.Expense), stored.directions)
        assertTrue(stored.supports(EntryDirection.Income))
        assertTrue(stored.supports(EntryDirection.Expense))
    }

    @Test
    fun `同一个方向集合总是编成同一串字符_与集合的迭代顺序无关`() = runBlocking {
        // 排序后拼接；否则"内容没变但字符串变了"会让 UPDATE 白写、测试也变得不稳定
        repository.add(category(id = "a", directions = setOf(EntryDirection.Expense, EntryDirection.Income)))
        repository.add(category(id = "b", directions = setOf(EntryDirection.Income, EntryDirection.Expense)))

        val rows = dao.rows.sortedBy { it.id }

        assertEquals(rows[0].directions, rows[1].directions)
    }

    @Test
    fun `更新已存在的分类返回成功且内容已变`() = runBlocking {
        val original = category()
        repository.add(original)

        val renamed = original.rename(CategoryName.restore("猫主子"))
        val result = repository.update(renamed)

        assertEquals(Outcome.Ok(Unit), result)
        assertEquals("猫主子", (repository.all() as Outcome.Ok).value.single().displayName)
    }

    @Test
    fun `更新不存在的分类返回未找到`() = runBlocking {
        val result = repository.update(category())

        assertEquals(LedgerError.CategoryNotFound, (result as Outcome.Err).error)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `已归档的分类也在清单里_历史的名字解析需要它`() = runBlocking {
        repository.add(category(archived = true))

        val all = (repository.all() as Outcome.Ok).value

        assertEquals(1, all.size)
        assertTrue(all.single().archived)
    }

    @Test
    fun `按标识查询`() = runBlocking {
        repository.add(category(id = "custom-pet"))

        val found = (repository.byId(CategoryId("custom-pet")) as Outcome.Ok).value
        val missing = repository.byId(CategoryId("nope"))

        assertEquals("宠物", found.displayName)
        // 不存在是**领域结果**，不是存储故障，也不是 Ok(null)：
        // 否则每个调用方都要各写一遍判空
        assertEquals(LedgerError.CategoryNotFound, (missing as Outcome.Err).error)
    }

    @Test
    fun `存储失败时返回存储错误`() = runBlocking {
        dao.failure = IllegalStateException("disk full")

        assertEquals(DomainError.Technical.Storage, (repository.add(category()) as Outcome.Err).error)
        assertEquals(DomainError.Technical.Storage, (repository.all() as Outcome.Err).error)
    }
}
