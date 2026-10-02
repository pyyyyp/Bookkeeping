package com.jizhangbao.ledger.domain.model

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Category` 聚合与 `CategoryName` 的行为测试（`REQ-004`）。
 *
 * 这里**不测唯一性**：它是跨聚合规则，由应用层的用例测试覆盖（`BR-7`）。
 * 一个聚合测试去测"重名会被拒"是测不到的 —— 单个聚合看不见别的聚合。
 */
class CategoryAggregateTest {

    private fun create(
        rawName: String = "宠物",
        directions: Set<EntryDirection> = setOf(EntryDirection.Expense),
    ): Outcome<Category> = Category.create(CategoryId("custom-pet"), rawName, directions)

    @Test
    fun `新建的分类默认未归档且名字就是输入`() {
        val category = (create() as Outcome.Ok).value

        assertEquals("宠物", category.displayName)
        assertFalse(category.archived)
        assertTrue(category.supports(EntryDirection.Expense))
        assertFalse(category.supports(EntryDirection.Income))
    }

    @Test
    fun `名字为空或全是空格时被拒`() {
        assertEquals(LedgerError.CategoryNameInvalid, (create("") as Outcome.Err).error)
        assertEquals(LedgerError.CategoryNameInvalid, (create("   ") as Outcome.Err).error)
    }

    @Test
    fun `名字超过 20 个字时被拒`() {
        val tooLong = "字".repeat(CategoryName.MAX_LENGTH + 1)

        assertEquals(LedgerError.CategoryNameInvalid, (create(tooLong) as Outcome.Err).error)
        // 边界：正好 20 个字是合法的
        assertTrue(create("字".repeat(CategoryName.MAX_LENGTH)) is Outcome.Ok)
    }

    @Test
    fun `名字前后空白被去掉`() {
        val category = (create("  宠物  ") as Outcome.Ok).value

        assertEquals("宠物", category.displayName)
    }

    @Test
    fun `没有方向时被拒`() {
        assertEquals(
            LedgerError.CategoryDirectionRequired,
            (create(directions = emptySet()) as Outcome.Err).error,
        )
    }

    @Test
    fun `改名后标识与归档状态不变`() {
        val original = (create() as Outcome.Ok).value
        val renamed = original.rename(CategoryName.restore("猫主子"))

        assertEquals(original.id, renamed.id)
        assertEquals(false, renamed.archived)
        assertEquals("猫主子", renamed.displayName)
        // 相等性按全部字段：改过之后的内容与原来不同
        assertNotEquals(original, renamed)
    }

    @Test
    fun `归档后名字不变且幂等`() {
        val original = (create() as Outcome.Ok).value

        val archived = original.archive()

        assertTrue(archived.archived)
        assertEquals("宠物", archived.displayName)
        // 幂等：再归档一次不会产生"又一个新实例"，也不会把状态翻回去
        assertTrue(archived.archive() === archived)
    }

    @Test
    fun `归档可以撤销`() {
        val archived = (create() as Outcome.Ok).value.archive()

        val restored = archived.restoreFromArchive()

        assertFalse(restored.archived)
        assertEquals("宠物", restored.displayName)
        assertTrue(restored.restoreFromArchive() === restored)
    }

    @Test
    fun `归档状态下改名仍是归档`() {
        val archived = (create() as Outcome.Ok).value.archive()

        val renamed = archived.rename(CategoryName.restore("猫主子"))

        // 顺序无关：改名不该把归档状态丢掉
        assertTrue(renamed.archived)
    }

    @Test
    fun `名字的规范化形式用于比较_忽略大小写与空白`() {
        val a = CategoryName.ofOrNull(" Food ")!!
        val b = CategoryName.ofOrNull("food")!!

        // 唯一性检查比的是这个（BR-3）；展示时保留用户输入的样子
        assertEquals(a.normalized, b.normalized)
        assertEquals("Food", a.display)
    }

    @Test
    fun `从存储恢复非法名字会立刻抛异常`() {
        // 存储里的值写入时已校验；不合法说明被外部改坏了，应当暴露而不是变成"用户没提交成功"
        val thrown = runCatching { CategoryName.restore("  ") }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }
}
