package com.jizhangbao.worklog.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * 工时时段的 DAO。
 *
 * ## 为什么按**时刻**查，而不是按"归属日"查
 *
 * 表里没有 `day` 这一列（见 [WorkSessionEntity] 的 KDoc）：归属日是**推导**出来的，
 * 存一份就会多一个可能不一致的地方。所以这里按 `startedAtEpochMilli` 的半开区间查，
 * 由仓储把"日期区间 + 时区"换算成时刻区间 —— **归属规则仍然只有一处**。
 *
 * ## 为什么只有插入，没有改状态
 *
 * v1 是手工录入，录进去就是**已确认**（`BR-3`）。状态转换（结束/确认/作废）
 * 是给将来的地理围栏流程留的（`Q-024`），那时再加按 id 改状态的语句。
 */
@Dao
interface WorkSessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: WorkSessionEntity)

    /**
     * 把**整行**覆盖回去（`T-031` 加的）。
     *
     * 为什么不是"按 id 窄更新状态"：状态转换发生在**领域**里
     * （`WorkSession.confirm()` 走状态机、重新校验不变量），这里只负责把结果存回去。
     * 窄更新会让"库里那一行"与"领域认为的那一行"有机会不一致 —— 而这条链路的终点是工资。
     */
    @Update
    suspend fun update(entity: WorkSessionEntity)

    /**
     * 取开始时刻落在 `[fromEpochMilli, toEpochMilli)` 里的那些时段。
     *
     * 半开区间与内核的 `TimeRange` 一致：上界排他，不重不漏。
     */
    @Query(
        "SELECT * FROM work_session " +
            "WHERE startedAtEpochMilli >= :fromEpochMilli AND startedAtEpochMilli < :toEpochMilli " +
            "ORDER BY startedAtEpochMilli",
    )
    suspend fun startedBetween(fromEpochMilli: Long, toEpochMilli: Long): List<WorkSessionEntity>
}
