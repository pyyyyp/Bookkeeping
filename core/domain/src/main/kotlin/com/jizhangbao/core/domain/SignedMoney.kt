package com.jizhangbao.core.domain

/**
 * 以「分」为单位的**带符号**金额。
 *
 * ## 为什么它与 [Money] 是两个类型，而不是把 `Money` 放开成可负
 *
 * [Money] 的"非负"是**刻意的**：一笔交易的金额永远不是负数，方向由 [EntryDirection] 表达。
 * 这条不变量撑住了"金额与方向分开"的建模——否则「支出 50 元」就能被写成「收入 -50 元」，
 * 同一个事实有了两种写法，聚合不变式也就难以推理（见 glossary 的术语裁决记录）。
 *
 * 但**差额**（收入合计 − 支出合计）是另一回事：它不是任何一笔交易的金额，
 * 而是一个**带符号的派生量**。把两种语义压进一个类型，就等于为了一个派生量
 * 放弃上面那条不变量。所以：
 *
 * | 类型 | 用途 | 可以为负吗 |
 * |---|---|---|
 * | [Money] | 金额（某笔交易的数量） | ❌ 不行 |
 * | `SignedMoney` | 差额（派生量） | ✅ 可以 |
 *
 * 两者的内部表示相同（`cents: Long`），所以转换只发生在少数几个明确的地方。
 *
 * ## 负号在展示上必须可见
 *
 * `toString()` 对负数会输出 `-¥70.00`。这是**唯一一处"格式即语义"**的地方：
 * 少一个负号，用户会把超支看成结余（`REQ-002/AC-2`）。
 *
 * ## 一处边界说明
 *
 * 取绝对值时用的是 `if (cents < 0) -cents else cents`，这在 `Long.MIN_VALUE` 上会溢出。
 * 实践中到不了：本类型的值来自若干个非负 [Money] 的加减，
 * 而金额入口已经把关（`AmountInput` 要求分值不超过 62 位，见 `parseYuanToMoney`）。
 */
@JvmInline
value class SignedMoney private constructor(val cents: Long) {

    operator fun plus(other: SignedMoney): SignedMoney = SignedMoney(cents + other.cents)

    operator fun minus(other: SignedMoney): SignedMoney = SignedMoney(cents - other.cents)

    /** 是否为负（超支）。 */
    val isNegative: Boolean get() = cents < 0

    /**
     * 展示用格式：负数带负号，正数与零不带正号。
     *
     * 不用浮点，理由与 [Money.toString] 相同。
     */
    override fun toString(): String {
        val magnitude = if (cents < 0) -cents else cents
        val body = "¥${magnitude / CENTS_PER_YUAN}." +
            (magnitude % CENTS_PER_YUAN).toString().padStart(2, '0')
        return if (cents < 0) "-$body" else body
    }

    companion object {
        private const val CENTS_PER_YUAN = 100

        val ZERO: SignedMoney = SignedMoney(0)

        fun ofCents(cents: Long): SignedMoney = SignedMoney(cents)

        /** 从非负金额转换（差额为正的那一半）。 */
        fun of(money: Money): SignedMoney = SignedMoney(money.cents)
    }
}
