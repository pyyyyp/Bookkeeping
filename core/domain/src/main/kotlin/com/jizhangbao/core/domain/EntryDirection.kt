package com.jizhangbao.core.domain

/**
 * 账目条目的收支方向。
 *
 * 刻意用显式类型而不是金额的正负号：正负号在聚合不变式中极易出错
 * （例如「抵扣后金额 >= 0」在带符号表示下要判断的是两件事）。
 * 正负号只应在展示层出现。
 *
 * 见 docs/00-charter/glossary.md 术语裁决记录
 */
enum class EntryDirection {
    /** 收入 */
    Income,

    /** 支出 */
    Expense,
}
