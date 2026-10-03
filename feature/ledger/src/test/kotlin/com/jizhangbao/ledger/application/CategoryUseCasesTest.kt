package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.CategoryName
import com.jizhangbao.ledger.testing.FakeCategoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类用例的测试（`REQ-004`）。
 *
 * 这里才是**唯一性**该被测的地方（`BR-7`）：它是跨聚合规则，
 * 单个 `Category` 看不见别人 —— 用聚合测试去测"重名被拒"只会测出自己骗自己。
 */
class CategoryUseCasesTest {

    private val repository = FakeCategoryRepository()
    private val create = CreateCategoryUseCase(repository)
    private val rename = RenameCategoryUseCase(repository)
    private val archive = ArchiveCategoryUseCase(repository)
    private val restore = RestoreCategoryUseCase(repository)
    private val load = LoadCategoriesUseCase(repository)

    private suspend fun seed(
        id: String = "custom-pet",
        name: String = "宠物",
        directions: Set<EntryDirection> = setOf(EntryDirection.Expense),
        archived: Boolean = false,
    ): Category {
        val category = Category.restore(CategoryId(id), name, directions, archived)
        repository.add(category)
        return category
    }

    @Test
    fun `新建的分类落库且返回它的标识`() = runBlocking {
        val id = (create("宠物", setOf(EntryDirection.Expense)) as Outcome.Ok).value

        val stored = repository.stored().single()
        assertEquals(id, stored.id)
        assertEquals("宠物", stored.displayName)
        // 标识带前缀：一眼能看出是用户建的，而不是内置的
        assertTrue(stored.id.value.startsWith("custom-"))
    }

    @Test
    fun `重名被拒_且不落库`() = runBlocking {
        seed(name = "宠物")

        val result = create("宠物", setOf(EntryDirection.Expense))

        assertEquals(LedgerError.CategoryNameTaken, (result as Outcome.Err).error)
        assertEquals(1, repository.stored().size) // 没有多出来第二个
    }

    @Test
    fun `重名比较忽略空白与大小写`() = runBlocking {
        seed(name = "Food")

        val result = create("  food  ", setOf(EntryDirection.Expense))

        assertEquals(LedgerError.CategoryNameTaken, (result as Outcome.Err).error)
    }

    @Test
    fun `已归档分类的名字可以让出来`() = runBlocking {
        seed(name = "宠物", archived = true)

        // 用户要的是"别让我选到旧的"，不是"永远不能再用这个名字"
        assertTrue(create("宠物", setOf(EntryDirection.Expense)) is Outcome.Ok)
    }

    @Test
    fun `名字非法时被拒_且不碰仓储`() = runBlocking {
        val result = create("   ", setOf(EntryDirection.Expense))

        assertEquals(LedgerError.CategoryNameInvalid, (result as Outcome.Err).error)
        assertTrue(repository.stored().isEmpty())
    }

    @Test
    fun `改名只换名字_标识不变`() = runBlocking {
        val target = seed()

        val result = rename(target.id, "猫主子")

        assertEquals(Outcome.Ok(Unit), result)
        val stored = repository.stored().single()
        assertEquals(target.id, stored.id)
        assertEquals("猫主子", stored.displayName)
    }

    @Test
    fun `改名成自己原来的名字不算重名`() = runBlocking {
        val target = seed(name = "宠物")

        // 唯一性检查必须排除自己，否则"把 A 改成 A"会被判成重名
        assertEquals(Outcome.Ok(Unit), rename(target.id, "宠物"))
    }

    @Test
    fun `改名撞上别人的名字被拒`() = runBlocking {
        seed(id = "custom-pet", name = "宠物")
        val other = seed(id = "custom-phone", name = "话费")

        val result = rename(other.id, "宠物")

        assertEquals(LedgerError.CategoryNameTaken, (result as Outcome.Err).error)
        assertEquals("话费", repository.stored().single { it.id == other.id }.displayName)
    }

    @Test
    fun `改名一个不存在的分类返回未找到`() = runBlocking {
        val result = rename(CategoryId("custom-nope"), "随便")

        assertEquals(LedgerError.CategoryNotFound, (result as Outcome.Err).error)
    }

    @Test
    fun `归档与恢复`() = runBlocking {
        val target = seed()

        assertEquals(Outcome.Ok(Unit), archive(target.id))
        assertTrue(repository.stored().single().archived)

        assertEquals(Outcome.Ok(Unit), restore(target.id))
        assertFalse(repository.stored().single().archived)
    }

    @Test
    fun `归档一个不存在的分类返回未找到`() = runBlocking {
        assertEquals(
            LedgerError.CategoryNotFound,
            (archive(CategoryId("custom-nope")) as Outcome.Err).error,
        )
    }

    @Test
    fun `可选清单合并预置与自定义_并保留已归档的_因为历史要显示名字`() = runBlocking {
        seed(id = "custom-pet", name = "宠物")
        seed(id = "custom-old", name = "话费", archived = true)

        val all = (load() as Outcome.Ok).value

        // 预置在前
        assertEquals("餐饮", all.first().displayName)
        // 自定义（含已归档）都在
        assertTrue(all.any { it.displayName == "宠物" && !it.archived })
        assertTrue(all.any { it.displayName == "话费" && it.archived })
        assertEquals(8 + 2, all.size)
    }

    @Test
    fun `可选清单不因归档而丢掉预置`() = runBlocking {
        val all = (load() as Outcome.Ok).value

        assertEquals(8, all.size)
        assertEquals(CategoryName.MAX_LENGTH, 20) // 顺手确认常量没被意外改动
    }
}
