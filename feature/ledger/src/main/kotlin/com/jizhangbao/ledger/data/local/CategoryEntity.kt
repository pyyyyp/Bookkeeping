package com.jizhangbao.ledger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `Category` 的 Room 实体（外部模型，`REQ-004`）。
 *
 * 与 [LedgerEntryEntity] 一样坚持**只存原始类型**，因此不需要任何 `TypeConverter`：
 * 于是「转换器该放哪个模块」这个问题根本不存在（`Money` 是共享内核类型，
 * 而 `CategoryId` 是 Ledger 的，混在一起就得让 `:core:data` 依赖 feature —— R7 禁止）。
 *
 * ## 存储细节（不是业务规则）
 *
 * - `directions` 存**方向名的逗号分隔列表**（如 `Expense` 或 `Expense,Income`，已排序）。
 *   不用位掩码：位掩码看不出内容，而调试时想直接读一眼数据库是常有的事。
 *   也不用关联表：方向只有两个取值，为它多一张表是过度设计。
 * - `archived` 存布尔（Room 落地为 INTEGER 0/1）。
 * - **没有"删除"这回事**（`ADR-0009`）：归档就是 `archived = 1`，行永远在。
 */
@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val directions: String,
    val archived: Boolean,
)
