package com.jizhangbao.ledger.domain.model

/**
 * 「最近的账目」这一次读取的结果（`REQ-006`）。
 *
 * ## 为什么不是一个 `List<LedgerEntry>`
 *
 * 因为**读不出来的行必须被计数**。库里可能存在过不了域校验的脏数据
 * （被外部工具改坏、迁移写错、或者像 `T-013` 冒烟时那样被我用 `sqlite3` 手写进去），
 * 而两种极端做法都不可接受：
 *
 * - **静默跳过** → 把"数据坏了"伪装成"数据少了"，用户以为账目丢了；
 * - **整批失败** → 一条坏数据藏起全部账目（`T-013` 真实发生：界面显示"还没有记账"，
 *   而库里 5 条一条不少）。
 *
 * 所以：其余照常显示，**如实说有几条读不出来**（`REQ-006/AC-3`）。
 *
 * ## 与合计的关系（`REQ-006/BR-5`）
 *
 * 合计与占比由 SQL 求和，**坏行的金额仍计入** —— 钱是事实（那一行确实存在），
 * 坏掉的只是"能不能把它渲染成条目"。二者不一致时，[unreadable] 就是解释。
 */
data class RecentEntries(
    val entries: List<LedgerEntry>,
    /** 库里存在、但过不了域校验因而读不出来的条数。 */
    val unreadable: Int,
) {
    init {
        require(unreadable >= 0) { "不可读条数不可为负：$unreadable" }
    }

    /** 界面据此决定要不要给出提示（`AC-3`：跳过必须伴随告知）。 */
    val hasUnreadable: Boolean get() = unreadable > 0

    companion object {
        /** 全部读出来了（绝大多数情况）。 */
        fun of(entries: List<LedgerEntry>): RecentEntries = RecentEntries(entries = entries, unreadable = 0)
    }
}
