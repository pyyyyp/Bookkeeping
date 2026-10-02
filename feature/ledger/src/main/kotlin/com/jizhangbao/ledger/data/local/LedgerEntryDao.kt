package com.jizhangbao.ledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

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

    /**
     * 某个方向、某个时间范围内的金额合计（`REQ-002`，跨上下文读端口用）。
     *
     * ## 三个刻意的选择
     *
     * 1. **在 SQL 里 SUM，而不是把整表读进内存再相加**。条目量级会增长，而合计是每次
     *    记账/删除都要重算的。数据库本来就是干这个的。
     * 2. **`COALESCE(..., 0)`**：没有任何匹配行时 `SUM` 返回 `NULL`，
     *    而"空月"在领域里是正常的零（`MonthlyTotals.ZERO`），不是"没有数据"（`REQ-002/AC-3`）。
     * 3. **区间半开** `[from, to)`，与 `TimeRange` 的口径一致 ——
     *    否则跨月边界的那一毫秒会被算进两个月。
     *
     * 归属口径是 `occurredAtEpochMilli`（**发生时间**），不是录入时间（`REQ-002/BR-2`）。
     */
    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM ledger_entry " +
            "WHERE direction = :direction " +
            "AND occurredAtEpochMilli >= :fromEpochMilli " +
            "AND occurredAtEpochMilli < :toEpochMilli",
    )
    suspend fun sumAmountCents(
        direction: String,
        fromEpochMilli: Long,
        toEpochMilli: Long,
    ): Long

    /**
     * 某个方向、某个范围内的金额**按分类分组**（`REQ-005`，分类占比用）。
     *
     * 归属口径与 [sumAmountCents] **完全一致**（发生时间、半开区间、`COALESCE`）——
     * 两个数字并排显示在同一屏上，口径不一致就是错的（`REQ-005/BR-5`）。
     *
     * **不按 `archived` 过滤**，也没法过滤：这里只看得见条目的 `categoryId`。
     * 已归档的分类必须计入（`BR-2`），否则各分类之和 ≠ 支出合计。
     *
     * 排序写在这里只是为了结果确定；真正生效的是 `CategoryBreakdown.of` 里的排序
     * （换数据源也成立，且能在纯 JVM 上测）。
     */
    @Query(
        "SELECT categoryId AS categoryId, COALESCE(SUM(amountCents), 0) AS amountCents " +
            "FROM ledger_entry " +
            "WHERE direction = :direction " +
            "AND occurredAtEpochMilli >= :fromEpochMilli " +
            "AND occurredAtEpochMilli < :toEpochMilli " +
            "GROUP BY categoryId " +
            "ORDER BY amountCents DESC, categoryId ASC",
    )
    suspend fun sumByCategory(
        direction: String,
        fromEpochMilli: Long,
        toEpochMilli: Long,
    ): List<CategorySumRow>

    /**
     * 用同样的主键**整行替换**（`REQ-003`）。
     *
     * 用 `@Update` 而不是手写 `UPDATE ... SET`：列清单由 Room 从实体生成，
     * 将来给实体加字段不会漏掉某一列（手写的 SET 列清单是"加了字段却忘了同步"的经典来源）。
     *
     * 返回**受影响行数** —— 0 表示这条已经不在库里了，
     * 仓储据此返回 `EntryNotFound`，而不是退化成插入（`REQ-003/BR-5`）。
     */
    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: LedgerEntryEntity): Int
}
