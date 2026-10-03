package com.jizhangbao.ledger.data.local

/**
 * [LedgerEntryDao.sumByCategory] 的投影行（`REQ-005`）。
 *
 * Room 要求 `@Query` 的返回类型是可构造的 POJO，所以它必须存在 ——
 * 它是**外部模型**（一个查询结果），不是领域概念：领域那边对应的是
 * 内核的 `CategoryAmount`（分类名 + 金额），转换在读取器实现里做。
 */
data class CategorySumRow(
    val categoryId: String,
    val amountCents: Long,
)
