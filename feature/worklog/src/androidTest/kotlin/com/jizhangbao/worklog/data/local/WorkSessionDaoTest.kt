package com.jizhangbao.worklog.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jizhangbao.worklog.domain.SessionState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO 的**真 SQLite** 验证（`T-029`）。
 *
 * 单元测试用的是内存里的假 DAO，它证明不了 SQL 对不对：列名、`WHERE` 的边界、
 * `ORDER BY` 全都要真的执行一次才算数。而这里的区间是**半开**的 —— 差一毫秒就会
 * 把第二天的时段算进今天，那会直接影响工资（休息日算不算加班差一倍）。
 *
 * ⚠️ 方法名必须 ASCII（教训 17：仪器化环境里中文方法名会让上报层抛异常，
 * 结果是"空结果 + 退出码 1"，看不出跑没跑）。
 */
@RunWith(AndroidJUnit4::class)
class WorkSessionDaoTest {

    private lateinit var database: WorklogTestDatabase
    private lateinit var dao: WorkSessionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorklogTestDatabase::class.java,
        ).build()
        dao = database.workSessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(id: String, startedAt: Long, state: String = SessionState.CONFIRMED.name) =
        WorkSessionEntity(
            id = id,
            startedAtEpochMilli = startedAt,
            endedAtEpochMilli = startedAt + 60 * 60 * 1000,
            state = state,
        )

    /** id 必须是合法 UUID（领域的 `WorkSessionId` 会校验），所以这里用真的 UUID。 */
    private fun uuid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

    @Test
    fun inserts_then_reads_back_with_all_columns() = runBlocking {
        dao.insert(entity(uuid(1), startedAt = 1_000L, state = SessionState.RUNNING.name))

        val all = dao.startedBetween(0L, 2_000L)

        assertEquals(1, all.size)
        assertEquals(uuid(1), all.single().id)
        assertEquals(1_000L, all.single().startedAtEpochMilli)
        assertEquals(SessionState.RUNNING.name, all.single().state)
    }

    @Test
    fun range_is_half_open_upper_bound_excluded() = runBlocking {
        // 边界上的那一条：正好等于上界 → **不该**被取到（半开区间）
        dao.insert(entity(uuid(1), startedAt = 1_000L))
        dao.insert(entity(uuid(2), startedAt = 2_000L))

        val result = dao.startedBetween(1_000L, 2_000L)

        assertEquals(listOf(uuid(1)), result.map { it.id })
    }

    @Test
    fun orders_by_started_at() = runBlocking {
        dao.insert(entity(uuid(3), startedAt = 3_000L))
        dao.insert(entity(uuid(1), startedAt = 1_000L))
        dao.insert(entity(uuid(2), startedAt = 2_000L))

        val result = dao.startedBetween(0L, 9_999L)

        assertEquals(listOf(uuid(1), uuid(2), uuid(3)), result.map { it.id })
    }

    @Test
    fun empty_range_returns_nothing() = runBlocking {
        dao.insert(entity(uuid(1), startedAt = 1_000L))

        assertTrue(dao.startedBetween(5_000L, 9_000L).isEmpty())
    }

    @Test
    fun null_ended_at_survives_the_round_trip() = runBlocking {
        // Running 的时段没有结束时刻 —— 这一列的"可空"必须真的存得下 null
        dao.insert(
            WorkSessionEntity(
                id = uuid(9),
                startedAtEpochMilli = 1_000L,
                endedAtEpochMilli = null,
                state = SessionState.RUNNING.name,
            ),
        )

        val stored = dao.startedBetween(0L, 2_000L).single()

        assertEquals(null, stored.endedAtEpochMilli)
        assertEquals(SessionState.RUNNING.name, stored.state)
    }

    @Test
    fun update_replaces_the_whole_row() = runBlocking {
        // T-031：确认/作废走的是"整行覆盖"，不是窄更新 —— 所以这里验证
        // "改过的字段确实写进去了、没改的字段没被动过"
        dao.insert(
            WorkSessionEntity(
                id = uuid(7),
                startedAtEpochMilli = 1_000L,
                endedAtEpochMilli = null,
                state = SessionState.RUNNING.name,
            ),
        )

        dao.update(
            WorkSessionEntity(
                id = uuid(7),
                startedAtEpochMilli = 1_000L,
                endedAtEpochMilli = 2_000L,
                state = SessionState.CONFIRMED.name,
            ),
        )

        val stored = dao.startedBetween(0L, 9_999L).single()
        assertEquals(SessionState.CONFIRMED.name, stored.state)
        assertEquals(2_000L, stored.endedAtEpochMilli)
        // 开始时刻没被动过（窄更新最容易写错的就是这一点）
        assertEquals(1_000L, stored.startedAtEpochMilli)
    }
}
