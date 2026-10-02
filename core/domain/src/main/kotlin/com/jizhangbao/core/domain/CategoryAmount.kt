package com.jizhangbao.core.domain

/**
 * 分类占比清单里的**一行**：分类名 + 该分类的支出合计（`REQ-005`）。
 *
 * ## 为什么是"名字"而不是"分类标识"
 *
 * `CategoryId` 住在 `:feature:ledger`，而本类型住共享内核 —— 按 R2，
 * 消费方 Insight 看不见 Ledger 的分类模型。契约在内核里（`ADR-0008`），
 * 所以传的是**已经解析好的、给人看的名字**。
 *
 * 这不是"把展示细节混进模型"：对一个**读模型**来说，"用户看到的分类名"
 * 就是它的数据。谁解析这个名字（Ledger）、包括哪些分类（含已归档），
 * 由 `REQ-005/BR-2` 与 `BR-7` 规定。
 */
data class CategoryAmount(
    val categoryName: String,
    val amount: Money,
) {
    init {
        require(categoryName.isNotBlank()) { "分类名不可为空" }
    }
}
