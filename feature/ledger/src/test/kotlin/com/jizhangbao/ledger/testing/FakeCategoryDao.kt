package com.jizhangbao.ledger.testing

import com.jizhangbao.ledger.data.local.CategoryDao
import com.jizhangbao.ledger.data.local.CategoryEntity

/**
 * 内存版分类 DAO（`REQ-004`）。
 *
 * 与 `FakeLedgerEntryDao` 同一个立场：用接口 fake 而不是 Room 内存库，
 * 代价是**这条路径测不到 SQL**（`CREATE TABLE`、`INSERT`、`@Update` 都由真库验证）。
 * 归类为"够用"：仓储实现里值得测的是**映射与 0 行语义**，而那两件事与 SQL 无关。
 */
internal class FakeCategoryDao : CategoryDao {

    val rows = mutableListOf<CategoryEntity>()

    /** 非 null 时所有操作都抛这个异常，用于测异常翻译。 */
    var failure: Exception? = null

    override suspend fun insert(entity: CategoryEntity) {
        failure?.let { throw it }
        rows += entity
    }

    override suspend fun update(entity: CategoryEntity): Int {
        failure?.let { throw it }
        val index = rows.indexOfFirst { it.id == entity.id }
        if (index < 0) return 0
        rows[index] = entity
        return 1
    }

    override suspend fun all(): List<CategoryEntity> {
        failure?.let { throw it }
        return rows.sortedBy { it.name }
    }

    override suspend fun byId(id: String): CategoryEntity? {
        failure?.let { throw it }
        return rows.firstOrNull { it.id == id }
    }
}
