package com.jizhangbao.ledger.domain.model

/**
 * 「最近的账目」这一次读取的结果（`REQ-006`）。
 *
 * ## 为什么不是一个 `List<LedgerEntry>`
 *
 * 因为**读不出来的行必须被如实报告**。库里可能存在过不了域校验的脏数据
 * （被外部工具改坏、迁移写错、或者像 `T-013` 冒烟时那样被我用 `sqlite3` 手写进去），
 * 而两种极端做法都不可接受：
 *
 * - **静默跳过** → 把"数据坏了"伪装成"数据少了"，用户以为账目丢了；
 * - **整批失败** → 一条坏数据藏起全部账目（`T-013` 真实发生：界面显示"还没有记账"，
 *   而库里 5 条一条不少）。
 *
 * 所以：其余照常显示，**如实说有几条读不出来**（`REQ-006/AC-3`）。
 *
 * ## 为什么是 `List<UnreadableRow>` 而不是一个计数（`REQ-008`）
 *
 * 只报数字会让用户停在死路上：他看得到问题，却**没有任何办法处理**。
 * 要给出路就得能**指回那一行** —— 而"指回去"需要它的**原始主键**
 * （域模型还原不出来，所以只能从库里原样带出来）。
 * 计数退化成 [unreadable]（`size`），界面上的那句话不用改。
 *
 * ## 与合计的关系（`REQ-006/BR-5`）
 *
 * 合计与占比由 SQL 求和，**坏行的金额仍计入** —— 钱是事实（那一行确实存在），
 * 坏掉的只是"能不能把它渲染成条目"。二者不一致时，[unreadableRows] 就是解释，
 * 也是用户**消除这个不一致**的入口（删掉它，`REQ-008/AC-3`）。
 */
data class RecentEntries(
    val entries: List<LedgerEntry>,
    /** 读不出来的行：原始标识 + 原因。 */
    val unreadableRows: List<UnreadableRow> = emptyList(),
) {
    /** 读不出来的条数（界面提示用它）。 */
    val unreadable: Int get() = unreadableRows.size

    /** 界面据此决定要不要给出提示（`REQ-006/AC-3`：跳过必须伴随告知）。 */
    val hasUnreadable: Boolean get() = unreadableRows.isNotEmpty()

    companion object {
        /** 全部读出来了（绝大多数情况）。 */
        fun of(entries: List<LedgerEntry>): RecentEntries = RecentEntries(entries = entries)
    }
}

/**
 * 一行**读不出来**的数据（`REQ-008`）。
 *
 * ## 它刻意不是 `LedgerEntry`
 *
 * 因为"读不出来"的**定义**就是"还原不成 `LedgerEntry`"（域校验拒绝了它）。
 * 用一个假的 `LedgerEntry` 去表示它，等于把不合格的数据放进领域模型 ——
 * 那正是本项目最不愿意做的事。
 *
 * 所以它是一对**原始事实**：库里那一行的主键，以及域校验给出的原因。
 */
data class UnreadableRow(
    /**
     * 库里的原始主键。
     *
     * 它**是唯一能指回这一行的东西** —— 删除要靠它，别的字段都不可信
     * （正是因为不可信才读不出来）。
     */
    val rawId: String,
    /**
     * 读不出来的原因，**照实**写域校验抛出的那句话（`REQ-008/BR-4`）。
     *
     * 不翻译成"数据损坏"这类含糊说法：含糊的提示会把排障从一分钟变成一小时。
     * 它不含金额/备注/分类名（`BR-6`）。
     */
    val reason: String,
) {
    init {
        require(rawId.isNotBlank()) { "坏行的原始标识不可为空" }
        // reason 允许为空：域校验可能没给出消息。界面在空的时候显示一句固定文案，
        // 而不是让这里 require 失败 —— 那样"一行坏数据"会变成"一次崩溃"。
    }
}
