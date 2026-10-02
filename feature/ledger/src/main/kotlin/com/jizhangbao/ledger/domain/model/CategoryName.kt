package com.jizhangbao.ledger.domain.model

/**
 * 分类的名字（值对象）。
 *
 * `REQ-004` 之前，名字只是 `Category` 里的一个 `String` 加上两句 `require`。
 * 现在它有两条理由独立出来：
 *
 * 1. **规则要被复用**：新建与改名两条路径都要校验，写在两处迟早漂移（`T-011/BR-6` 的同一个道理）。
 * 2. **名字是要被比较的东西**：唯一性检查（`REQ-004/BR-3`）比的是**规范化之后**的名字 ——
 *    " 宠物 " 与 "宠物" 是同一个名字，不规范化就会放过两个看起来一样的分类。
 *
 * 注意唯一性**不在这里**：它是跨聚合规则，属于应用层（`BR-7`）。
 * 这个类型只回答"这个名字本身合法吗"，以及"两个名字算不算同一个"。
 */
@JvmInline
value class CategoryName private constructor(val value: String) {

    /** 去掉首尾空白后的展示形式。存的就是它，所以 `value` 与 `display` 相同。 */
    val display: String get() = value

    /**
     * 规范化形式：去掉首尾空白 + 转小写，**仅用于比较**（唯一性检查）。
     *
     * 不把规范化后的结果存起来：中文没有大小写，但用户可能混用中英（「Food」/「food」），
     * 而展示时应当保留用户输入的样子。
     */
    val normalized: String get() = value.trim().lowercase()

    override fun toString(): String = value

    companion object {
        /** 与 `REQ-001` 时一致：最多 20 个字。 */
        const val MAX_LENGTH = 20

        /**
         * 校验并构造。不合法时返回 `null`（而不是抛异常）：
         * "用户输了个空名字"是**正常路径**，界面要给出提示，不该以异常形式穿过层。
         *
         * 与之相对，[Category.restore] 路径上的非法数据是**数据被改坏了**，那里才用 require。
         */
        fun ofOrNull(raw: String): CategoryName? {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty() -> null
                trimmed.length > MAX_LENGTH -> null
                else -> CategoryName(trimmed)
            }
        }

        /**
         * 从存储恢复：存储里的名字必须是合法的（写入时已校验）。
         * 不合法说明数据被外部改坏了 —— 立刻暴露，不要静默变成"用户没提交成功"。
         */
        fun restore(value: String): CategoryName {
            require(value.isNotBlank()) { "存储里的分类名不可为空" }
            require(value.trim().length <= MAX_LENGTH) {
                "存储里的分类名超过 $MAX_LENGTH 个字：$value"
            }
            return CategoryName(value.trim())
        }
    }
}
