package com.jizhangbao.ledger.domain.repository

import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.LedgerEntryId

/**
 * 账目条目的仓储接口。
 *
 * 接口在 `domain`、实现在 `data`（R4 依赖倒置）。方法名说**领域语言**
 * （`add` / `remove` / `recent`），不说 SQL 语言（不出现 `insert` / `query` / `limit` 字样）。
 *
 * 只放聚合根：本卡没有 `CategoryRepository`——分类是只读的内置数据
 * （见 [com.jizhangbao.ledger.domain.model.CategoryCatalog]）。
 *
 * `suspend` 而不返回 `Flow`：本卡只需「改动后重新查询」即可让列表刷新，
 * 反应式观察等数据层落地时再引入（那时会在 `:core:data` 声明协程依赖，
 * 而不是让领域层先背上一份）。
 */
interface LedgerEntryRepository {

    suspend fun add(entry: LedgerEntry)

    suspend fun remove(id: LedgerEntryId)

    /** 最近 [limit] 条，**按发生时间倒序**（`REQ-001/AC-7`）。 */
    suspend fun recent(limit: Int): List<LedgerEntry>
}
