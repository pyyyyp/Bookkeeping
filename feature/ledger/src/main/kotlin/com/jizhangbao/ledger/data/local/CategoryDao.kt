package com.jizhangbao.ledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * 分类的 Room DAO（`REQ-004`）。
 *
 * 注意这里**没有 `delete`**：分类只能归档（`ADR-0009`）。
 * 少一个方法不是疏漏，是让"物理删除分类"在数据层根本无从下手。
 */
@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CategoryEntity)

    /** 返回受影响行数 —— 仓储据此区分「改掉了」与「本来就没有」。 */
    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: CategoryEntity): Int

    /**
     * 全部自定义分类（含已归档）。
     *
     * 一次全取：分类是几十个量级，而"可选清单"与"显示名解析"都需要在内存里做合并，
     * 分两个查询反而要维护两套过滤条件。**不按 `archived` 过滤**是刻意的 ——
     * 归档过的分类仍然要能把历史条目的名字显示出来（`REQ-004/AC-3`），
     * 而过不过滤由用例决定。
     */
    @Query("SELECT * FROM category ORDER BY name ASC")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun byId(id: String): CategoryEntity?
}
