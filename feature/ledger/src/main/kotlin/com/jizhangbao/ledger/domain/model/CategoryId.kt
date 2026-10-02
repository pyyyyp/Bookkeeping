package com.jizhangbao.ledger.domain.model

/** 分类标识的合法形态：小写字母开头，之后可含小写字母、数字与连字符。 */
private val CATEGORY_ID_PATTERN = Regex("[a-z][a-z0-9-]*")

/**
 * 分类标识（slug），例如 `food` / `transport`。
 *
 * 用 slug 而不是数据库自增 ID 或显示名：显示名是**可变的展示内容**
 * （「餐饮」将来可能改成「吃饭」），而标识必须是稳定的，否则历史条目的分类会漂移。
 *
 * 见 `docs/20-domain/ledger-model.md` 的「值对象清单」。
 */
@JvmInline
value class CategoryId(val value: String) {

    init {
        require(value.isNotBlank()) { "分类标识不可为空" }
        require(CATEGORY_ID_PATTERN.matches(value)) {
            "分类标识只能是小写字母、数字与连字符，且以字母开头：$value"
        }
    }

    override fun toString(): String = value
}
