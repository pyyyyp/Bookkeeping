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
    /**
     * **不透明标识**（`REQ-007/BR-4`）：消费方（Insight）拿它指回"用户点的是哪一行"，
     * 并把它**原样回传**；但**不得解释它** —— 它是不是分类 id、长什么样，都是 Ledger 的事。
     *
     * 为什么不能只靠 [categoryName]：名字是给人看的，可能重名
     * （`REQ-004` 的唯一性是应用层规则，而且重命名会改历史显示），
     * 用它当键会在改名后指向别处。**键要稳定，名字要可变**。
     */
    val categoryKey: String,
    val categoryName: String,
    val amount: Money,
) {
    init {
        require(categoryKey.isNotBlank()) { "分类标识不可为空" }
        require(categoryName.isNotBlank()) { "分类名不可为空" }
    }
}
