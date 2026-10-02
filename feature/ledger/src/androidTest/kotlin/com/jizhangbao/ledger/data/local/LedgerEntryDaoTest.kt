package com.jizhangbao.ledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **测试专用**的数据库（`T-010`）。
 *
 * 生产的 `@Database` 住在 `:app`（`ADR-0007`：只有组合根能同时看见所有 feature），
 * 因此 `:feature:ledger` 看不见它。要在本模块内验证 DAO，就需要一个只属于测试的 `@Database`：
 *
 * - 它**不进 APK**（在 `androidTest` 源集里）；
 * - `exportSchema = false`：测试库没有 schema 需要留档，也不该往 `schemas/` 里写东西；
 * - 用 `inMemoryDatabaseBuilder`：每次测试一张全新的空库，测试之间互不影响。
 *
 * ⚠️ 这**不是**"第二套生产数据库"——它就是测试夹具。
 */
@Database(entities = [LedgerEntryEntity::class], version = 1, exportSchema = false)
internal abstract class TestLedgerDatabase : RoomDatabase() {
    abstract fun ledgerEntryDao(): LedgerEntryDao
}

/**
 * `LedgerEntryDao` 的 SQL 测试。
 *
 * ## 为什么这些用例非有不可
 *
 * 单元测试用的是内存 fake DAO，所以下面这些**从来没被验证过**：
 * `ORDER BY` 的次序对不对、`LIMIT` 有没有生效、`DELETE` 返回的影响行数是不是真的。
 * 它们写错的后果是"列表顺序随机跳"或"删了不存在的条目却提示成功"，而这两种都不报错。
 *
 * ## ⚠️ 方法名必须用 ASCII（实测踩过）
 *
 * 这里的方法名没有像单元测试那样用反引号写中文。原因不是偏好，而是**实测**：
 * 用中文方法名跑 `connectedDebugAndroidTest` 时，AGP 的测试上报层会在
 * `CompositeTestExecutionListener.executionFinished` 里抛
 * `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0`，
 * 结果文件里 `tests=` 是空的、退出码 1 —— **测试跑没跑、过没过，完全看不出来**。
 * （已核实 `debugAndroidTestRuntimeClasspath` 上只有 JUnit 4.13.2，没有 JUnit 5，
 * 所以不是"两套测试框架打架"。）
 * 中文说明放在 KDoc 与断言消息里，可读性不损失。
 */
@RunWith(AndroidJUnit4::class)
class LedgerEntryDaoTest {

    private lateinit var database: TestLedgerDatabase
    private lateinit var dao: LedgerEntryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TestLedgerDatabase::class.java).build()
        dao = database.ledgerEntryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(
        id: String,
        occurredAt: Long,
        bookedAt: Long = occurredAt,
        amountCents: Long = 1250,
        direction: String = "Expense",
    ) = LedgerEntryEntity(
        id = id,
        direction = direction,
        amountCents = amountCents,
        categoryId = "food",
        occurredAtEpochMilli = occurredAt,
        bookedAtEpochMilli = bookedAt,
        note = null,
    )

    /** 插入后可以查出来 */
    @Test
    fun insert_then_recent_returns_the_row() = runBlocking {
        dao.insert(entity(id = "a", occurredAt = 1_000))

        val found = dao.recent(limit = 10)

        assertEquals(1, found.size)
        assertEquals("a", found.single().id)
    }

    /** 按发生时间倒序返回（`REQ-001/AC-7`） */
    @Test
    fun recent_orders_by_occurred_at_desc() = runBlocking {
        dao.insert(entity(id = "old", occurredAt = 1_000))
        dao.insert(entity(id = "new", occurredAt = 3_000))
        dao.insert(entity(id = "mid", occurredAt = 2_000))

        val ids = dao.recent(limit = 10).map { it.id }

        assertEquals(listOf("new", "mid", "old"), ids)
    }

    /** 发生时间相同时按录入时间倒序：没有第二排序键，列表会随机跳动 */
    @Test
    fun recent_uses_booked_at_as_tiebreaker() = runBlocking {
        dao.insert(entity(id = "bookedFirst", occurredAt = 1_000, bookedAt = 1_000))
        dao.insert(entity(id = "bookedLater", occurredAt = 1_000, bookedAt = 2_000))

        val ids = dao.recent(limit = 10).map { it.id }

        assertEquals(listOf("bookedLater", "bookedFirst"), ids)
    }

    /** 遵守 limit */
    @Test
    fun recent_respects_limit() = runBlocking {
        (1..5).forEach { dao.insert(entity(id = "e$it", occurredAt = it * 1_000L)) }

        val ids = dao.recent(limit = 2).map { it.id }

        assertEquals(listOf("e5", "e4"), ids)
    }

    /** 删除存在的条目返回受影响行数 1 */
    @Test
    fun delete_existing_row_returns_one() = runBlocking {
        dao.insert(entity(id = "a", occurredAt = 1_000))

        val affected = dao.deleteById("a")

        assertEquals(1, affected)
        assertEquals(0, dao.recent(limit = 10).size)
    }

    /** 删除不存在的条目返回 0 而不是报错 —— 仓储就是靠这个值区分「删掉了」与「本来就没有」 */
    @Test
    fun delete_missing_row_returns_zero() = runBlocking {
        val affected = dao.deleteById("nope")

        assertEquals(0, affected)
    }

    /** 只删指定的那一条 */
    @Test
    fun delete_only_removes_the_target_row() = runBlocking {
        dao.insert(entity(id = "keep", occurredAt = 1_000))
        dao.insert(entity(id = "drop", occurredAt = 2_000))

        dao.deleteById("drop")

        assertEquals(listOf("keep"), dao.recent(limit = 10).map { it.id })
    }

    /** 合计只算匹配的方向与半开区间（`REQ-002` 跨上下文读端口用的那条 SQL） */
    @Test
    fun sum_only_counts_matching_direction_and_range() = runBlocking {
        dao.insert(entity(id = "inRange", occurredAt = 1_500, amountCents = 100))
        dao.insert(entity(id = "alsoInRange", occurredAt = 1_900, amountCents = 250))
        // 方向不同：不算
        dao.insert(entity(id = "income", occurredAt = 1_500, amountCents = 9_999, direction = "Income"))
        // 下界含、上界不含
        dao.insert(entity(id = "beforeFrom", occurredAt = 999, amountCents = 9_999))
        dao.insert(entity(id = "atTo", occurredAt = 2_000, amountCents = 9_999))

        val sum = dao.sumAmountCents(direction = "Expense", fromEpochMilli = 1_000, toEpochMilli = 2_000)

        assertEquals(350, sum)
    }

    /** 空区间返回 0 而不是 NULL —— COALESCE 就是为这个加的（空月是正常的零，REQ-002/AC-3） */
    @Test
    fun sum_of_empty_range_is_zero() = runBlocking {
        dao.insert(entity(id = "outside", occurredAt = 5_000, amountCents = 100))

        val sum = dao.sumAmountCents(direction = "Expense", fromEpochMilli = 1_000, toEpochMilli = 2_000)

        assertEquals(0, sum)
    }
}
