package com.jizhangbao.ledger.domain.repository

import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryId

/**
 * 分类仓储（`REQ-004`）。
 *
 * 接口在 `domain`、实现在 `data`（R4）。方法名说领域语言：`add` / `update` / `all` / `byId`。
 *
 * ## 这里没有 `remove`，而且不是遗漏
 *
 * 分类只能**归档**（`ADR-0009`）：历史条目引用了它，物理删除要么留悬空引用、
 * 要么得改写历史。仓储层不提供删除，等于让"误删分类"这件事在类型层面无处安放。
 *
 * ## 唯一性为什么不在这个接口上
 *
 * "不能重名"是跨聚合规则（`REQ-004/BR-7`），由用例在创建前查一次并决定。
 * 仓储只负责存取，不负责裁决 —— 否则同一条规则会同时活在仓储与用例两处。
 */
interface CategoryRepository {

    suspend fun add(category: Category): Outcome<Unit>

    /** 按标识替换；不存在时返回 [com.jizhangbao.ledger.domain.error.LedgerError.CategoryNotFound]。 */
    suspend fun update(category: Category): Outcome<Unit>

    /** 全部自定义分类（**含已归档**：历史条目的名字解析需要它们）。 */
    suspend fun all(): Outcome<List<Category>>

    suspend fun byId(id: CategoryId): Outcome<Category?>
}
