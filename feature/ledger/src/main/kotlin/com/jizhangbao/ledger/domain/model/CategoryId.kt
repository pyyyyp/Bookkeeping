package com.jizhangbao.ledger.domain.model

import java.util.UUID

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

    companion object {
        /**
         * 生成一个新标识（自定义分类用，`REQ-004`）。
         *
         * 前缀 `custom-` 是刻意的：一眼能看出这个分类是用户建的还是内置的 ——
         * 调试、写迁移、排查数据问题时都用得上（内置的那 8 个都是手写 slug）。
         *
         * 用 UUID 而不是"名字的 slug"：名字可以改，而标识必须稳定；
         * 而且按名字生成会让「宠物」与「宠 物」拿到两个不同 id，
         * 唯一性检查就形同虚设。
         */
        fun new(): CategoryId = CategoryId("custom-" + UUID.randomUUID().toString().lowercase())
    }
}
