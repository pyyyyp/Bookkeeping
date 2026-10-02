package com.jizhangbao.core.domain

/**
 * 跨上下文的**读契约**：按时间范围给出账本的收支合计。
 *
 * ## 为什么它住在共享内核（而不是 Insight 或 Ledger）
 *
 * 消费方是 `:feature:insight`，生产方是 `:feature:ledger`，而 R2 **禁止 feature 之间互相依赖**。
 * 于是契约必须放在一个双方都能依赖的地方——共享内核。见 `ADR-0008`。
 *
 * 依赖方向因此是 `ledger → core:domain ← insight`，两边都不知道对方存在。
 *
 * ## 为什么不是仓储接口
 *
 * 它没有聚合、没有增删改，只有一个读方法。叫 `Repository` 会让下一个人以为
 * 这里能写数据（[MonthlyTotals] 本身也不可变/无身份）。
 *
 * ## 风格与既有仓储保持一致
 *
 * `suspend` + 返回 [Outcome]：不抛异常跨层，失败是一个值（见 `docs/20-domain/ledger-model.md`）。
 */
interface LedgerTotalsReader {

    /**
     * 统计 `range` 内的收入合计与支出合计。
     *
     * 口径由实现方遵守（`ADR-0008` 与 `REQ-002/BR-2`）：**按条目的发生时间归属**，
     * 不是按录入时间——否则补记的条目会同时出现在两个月里。
     *
     * 区间为空（该月一条都没有）时返回 [MonthlyTotals.ZERO]，**不是** [Outcome.Err]：
     * "没有记账"是正常状态，"查不到"才是错误。
     */
    suspend fun totalsIn(range: TimeRange): Outcome<MonthlyTotals>
}
