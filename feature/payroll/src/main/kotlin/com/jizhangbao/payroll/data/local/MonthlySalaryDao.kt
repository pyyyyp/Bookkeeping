package com.jizhangbao.payroll.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 月薪的 DAO（`REQ-017/AC-1`）。
 *
 * ⚠️ [effectiveAt] 是这个表**唯一**的读法：给一个日期，取"到那天为止最晚生效的那一份"。
 * 这条规则就是 `Q-019` 的「涨薪不改历史月份的工资」—— 写成 SQL 而不是在内存里过滤，
 * 是因为它必须**只有一处**（在别处再写一遍就会漂移）。
 */
@Dao
interface MonthlySalaryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MonthlySalaryEntity)

    @Query(
        "SELECT * FROM monthly_salary " +
            "WHERE effectiveFromEpochDay <= :epochDay " +
            "ORDER BY effectiveFromEpochDay DESC LIMIT 1",
    )
    suspend fun effectiveAt(epochDay: Long): MonthlySalaryEntity?

    @Query("SELECT * FROM monthly_salary ORDER BY effectiveFromEpochDay DESC")
    suspend fun all(): List<MonthlySalaryEntity>
}
