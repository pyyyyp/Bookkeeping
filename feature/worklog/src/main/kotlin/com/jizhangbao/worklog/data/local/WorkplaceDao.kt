package com.jizhangbao.worklog.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 工作地点的 DAO（`REQ-016/AC-7`）。
 *
 * `REPLACE` 让"改一个地点的名字或半径"是同一条语句 —— 与分类那边一样，
 * **地点只有一条写路径**（多写一条 update 就多一处能漂移的地方）。
 */
@Dao
interface WorkplaceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkplaceEntity)

    @Query("SELECT * FROM workplace ORDER BY name")
    suspend fun all(): List<WorkplaceEntity>

    @Query("DELETE FROM workplace WHERE id = :id")
    suspend fun deleteById(id: String)
}
