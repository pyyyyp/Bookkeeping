package com.jizhangbao.ledger.testing

import com.jizhangbao.ledger.data.local.LedgerEntryDao
import com.jizhangbao.ledger.data.local.LedgerEntryEntity

/**
 * 内存版 DAO，用来在**不启动 Android** 的前提下测仓储实现。
 *
 * 用接口 fake 而不是 Room in-memory 数据库：Room 的内存库需要 Android 运行时，
 * 而 `androidx.test` 系列依赖目前不在版本目录里 —— 引入它们需要单独裁决。
 * 代价是**这条路径测不到 SQL 本身**（排序、LIMIT、删除条件由 Room 在编译期校验，
 * 运行期行为靠模拟器上的手工冒烟确认）。这一点写在 `T-007` 卡里，不假装覆盖了。
 */
internal class FakeLedgerEntryDao : LedgerEntryDao {

    val inserted = mutableListOf<LedgerEntryEntity>()

    /** `deleteById` 的返回值：1 = 删掉了，0 = 本来就不存在。 */
    var deleteAffectedRows = 1

    var recentRows: List<LedgerEntryEntity> = emptyList()

    /** 非 null 时所有操作都抛这个异常，用于测异常翻译。 */
    var failure: Exception? = null

    override suspend fun insert(entity: LedgerEntryEntity) {
        failure?.let { throw it }
        inserted += entity
    }

    override suspend fun deleteById(id: String): Int {
        failure?.let { throw it }
        return deleteAffectedRows
    }

    override suspend fun recent(limit: Int): List<LedgerEntryEntity> {
        failure?.let { throw it }
        return recentRows.take(limit)
    }

    /**
     * 按方向与半开区间求和。
     *
     * 这里刻意**真的按存储的行算**，而不是返回一个预设值：这样端口实现的测试
     * （收入、支出分别求和再组装）才有意义。
     * 但 SQL 本身（`COALESCE(SUM(...), 0)`、`>= from AND < to`）**这条路径测不到** ——
     * 那由 `androidTest` 里的 `LedgerEntryDaoTest` 在真 SQLite 上验证。
     */
    override suspend fun sumAmountCents(
        direction: String,
        fromEpochMilli: Long,
        toEpochMilli: Long,
    ): Long {
        failure?.let { throw it }
        return inserted
            .filter {
                it.direction == direction &&
                    it.occurredAtEpochMilli >= fromEpochMilli &&
                    it.occurredAtEpochMilli < toEpochMilli
            }
            .sumOf { it.amountCents }
    }
}
