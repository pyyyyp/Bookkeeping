package com.jizhangbao.core.domain

/**
 * 占比，单位是**千分之一**（`333` 表示 `33.3%`）。
 *
 * ## 为什么用整数而不是 `Double`
 *
 * `0.1` 在二进制浮点里是无限循环，`33.3%` 这种展示值会变成 `33.299999…`；
 * 于是"这个分类占多少"变成一个**不可精确断言**的量，界面上还可能渲染成
 * `33.300000000000004%`。千分之一的整数既够用（1 位小数）又完全确定。
 *
 * ## 各自四舍五入，不强行凑成 100%（`REQ-005/BR-3`）
 *
 * 三个各占三分之一的分类会显示 33.3% / 33.3% / 33.3%，加起来 99.9% —— 这是**正确的**。
 * 为了凑满 100% 必须改动某一类的数字，那比"99.9%"糟得多：用户拿明细加一遍，
 * 会发现某个分类的金额与百分比对不上。
 */
@JvmInline
value class Percentage private constructor(val tenths: Int) {

    /** 展示形式：`33.3%`。 */
    override fun toString(): String =
        "${tenths / TENTHS_PER_PERCENT}.${tenths % TENTHS_PER_PERCENT}%"

    companion object {
        /** 1 个百分点 = 10 个千分之一单位（本类型的最小单位）。 */
        private const val TENTHS_PER_PERCENT = 10

        val ZERO: Percentage = Percentage(0)

        fun ofTenths(tenths: Int): Percentage {
            require(tenths >= 0) { "占比不可为负：$tenths" }
            return Percentage(tenths)
        }

        /**
         * `amount ÷ total`，四舍五入到 1 位小数。
         *
         * 全程整数运算：`+ total / 2` 再整除就是"四舍五入"，不必经过浮点。
         * 合计为 0 时**没有占比可言**，直接拒绝 —— 调用方应当先避开这个情况
         * （空清单时根本不会问占比，见 [CategoryBreakdown]）。
         */
        fun of(amountCents: Long, totalCents: Long): Percentage {
            require(totalCents > 0) { "合计为 0 时没有占比可言" }
            require(amountCents >= 0) { "金额不可为负：$amountCents" }

            val tenths = (amountCents * 1_000 + totalCents / 2) / totalCents
            return Percentage(tenths.toInt())
        }
    }
}
