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

    /**
     * 一共配过几份（`T-034`）。
     *
     * ⚠️ 它分辨的是"**从没配过**"与"配了但该月未生效" —— 这两种情况要给用户完全不同的话
     * （`T-033` 的真机缺口：刚保存完却显示"还没有配月薪"，看起来像保存失败）。
     */
    @Query("SELECT COUNT(*) FROM monthly_salary")
    suspend fun count(): Int

    @Query("SELECT * FROM monthly_salary ORDER BY effectiveFromEpochDay DESC")
    suspend fun all(): List<MonthlySalaryEntity>
}
