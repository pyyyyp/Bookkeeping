package com.jizhangbao.ledger.domain.repository

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.TimeRange
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.LedgerEntryId
import com.jizhangbao.ledger.domain.model.RecentEntries

/**
 * 账目条目的仓储接口。
 *
 * 接口在 `domain`、实现在 `data`（R4 依赖倒置）。方法名说**领域语言**
 * （`add` / `remove` / `recent`），不说 SQL 语言（不出现 `insert` / `query` / `limit` 字样）。
 *
 * 只放聚合根：本卡没有 `CategoryRepository`——分类是只读的内置数据
 * （见 [com.jizhangbao.ledger.domain.model.CategoryCatalog]）。
 *
 * ## 为什么返回值是 `Outcome` 而不是直接返回数据 / 抛异常
 *
 * 「不抛异常跨层」是本项目的硬规则。存储失败（磁盘满、数据库损坏）是**可预期**的失败，
 * 因此它必须像其它领域失败一样，用 `Outcome` 表达：
 * `data` 层负责把 `SQLiteException` 之类的技术异常**翻译**成
 * [com.jizhangbao.core.domain.DomainError.Technical]，
 * 裸异常不得越过 `data` → `domain` 边界。
 *
 * ## 为什么没有 `observeRecent(...): Flow<...>`
 *
 * 反应式观察要求领域层依赖 kotlinx-coroutines，而引入新依赖需要单独裁决。
 * 本卡的界面只要「改动后重新查询」就能刷新，所以 `suspend` 就够了。
 */
interface LedgerEntryRepository {

    suspend fun add(entry: LedgerEntry): Outcome<Unit>

    /**
     * 用 `entry`（同标识）**替换**账本里已有的那条（`REQ-003`）。
     *
     * 不存在时返回 [com.jizhangbao.ledger.domain.error.LedgerError.EntryNotFound]，
     * 与 [remove] 一致的「影响行数为 0」语义：**不**静默插入一条新的
     * （`REQ-003/BR-5`——悄悄插入会让用户以为改动生效了）。
     */
    suspend fun update(entry: LedgerEntry): Outcome<Unit>

    suspend fun remove(id: LedgerEntryId): Outcome<Unit>

    /**
     * 最近 [limit] 条，**按发生时间倒序**（`REQ-001/AC-7`）。
     *
     * 返回 [RecentEntries] 而不是 `List<LedgerEntry>`：库里可能有**读不出来**的行
     * （数据被外部改坏），那时要"跳过它 + 计数 + 告知"，而不是整批失败或静默丢弃
     * （`REQ-006/AC-3`）。
     */
    suspend fun recent(limit: Int): Outcome<RecentEntries>

    /**
     * 某个分类、某个方向在某段区间内的条目，**按发生时间倒序**（`REQ-007`，占比下钻）。
     *
     * 口径必须与合计/占比**完全一致**（`REQ-007/BR-1`）：用户会把清单里的金额加起来，
     * 核对占比行上那个数字，而两个数字就在同一屏上。
     *
     * 同样返回 [RecentEntries]：下钻清单也会遇到坏行，处理方式与主列表一致（`REQ-006/AC-3`）。
     */
    suspend fun inCategory(
        categoryId: CategoryId,
        direction: EntryDirection,
        range: TimeRange,
        limit: Int,
    ): Outcome<RecentEntries>
}
