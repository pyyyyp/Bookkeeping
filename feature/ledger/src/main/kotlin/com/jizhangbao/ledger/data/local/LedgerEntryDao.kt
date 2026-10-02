package com.jizhangbao.ledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 账目条目的 Room DAO。
 *
 * Query 里的 SQL 在**编译期**由 Room 校验（列名写错、类型不匹配都会构建失败），
 * 因此这里的字符串不是「运行时才发现写错」的那种。
 */
@Dao
interface LedgerEntryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: LedgerEntryEntity)

    /** 返回受影响行数 —— 仓储据此区分「删掉了」与「本来就没有」。 */
    @Query("DELETE FROM ledger_entry WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /**
     * 按**发生时间倒序**取最近若干条（`REQ-001/AC-7`）。
     *
     * 第二排序键 `bookedAtEpochMilli` 是为了**确定性**：同一天补记多条时，
     * 没有它数据库可以任意返回顺序，列表会随机跳动。
     * 这是工程取舍，不是业务规则。
     */
    @Query(
        "SELECT * FROM ledger_entry " +
            "ORDER BY occurredAtEpochMilli DESC, bookedAtEpochMilli DESC " +
            "LIMIT :limit",
    )
    suspend fun recent(limit: Int): List<LedgerEntryEntity>
}
