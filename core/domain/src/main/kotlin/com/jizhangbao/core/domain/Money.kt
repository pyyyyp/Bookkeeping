package com.jizhangbao.core.domain

/**
 * 以「分」为单位的不可变金额值对象。
 *
 * 设计决策：
 * - **用 Long 存分，绝不用 Double/BigDecimal** —— 浮点误差在记账场景是不可接受的
 * - **本类型只表示非负数量**，收支方向由 [EntryDirection] 显式表达。
 *   用正负号兼职表示方向会让聚合不变式难以推理（见 glossary 术语裁决记录）
 *
 * 见 docs/00-charter/glossary.md#Ledger
 */
@JvmInline
value class Money private constructor(val cents: Long) : Comparable<Money> {

    init {
        require(cents >= 0) { "金额不可为负：$cents 分（方向请用 EntryDirection 表达）" }
    }

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    operator fun plus(other: Money): Money = Money(cents + other.cents)

    operator fun minus(other: Money): Money {
        require(cents >= other.cents) { "金额不足：$cents 分 < ${other.cents} 分" }
        return Money(cents - other.cents)
    }

    operator fun times(factor: Int): Money {
        require(factor >= 0) { "倍数不可为负：$factor" }
        return Money(cents * factor)
    }

    /** 展示用格式。刻意不用浮点，避免 `105 分` 显示成 `1.0499999` */
    override fun toString(): String =
        "¥${cents / CENTS_PER_YUAN}.${(cents % CENTS_PER_YUAN).toString().padStart(2, '0')}"

    companion object {
        /**
         * 一元 = 100 分。
         *
         * 它出现在格式化（除、取余）与构造（[ofYuan] 乘）两处共三次，
         * 因此是具名常量而不是散落的字面量 —— 由 detekt 的 MagicNumber 规则发现（T-006）。
         */
        private const val CENTS_PER_YUAN = 100

        val ZERO: Money = Money(0)

        fun ofCents(cents: Long): Money = Money(cents)

        fun ofYuan(yuan: Long): Money = Money(yuan * CENTS_PER_YUAN)
    }
}
