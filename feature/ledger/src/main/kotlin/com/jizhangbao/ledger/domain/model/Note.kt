package com.jizhangbao.ledger.domain.model

/**
 * 条目备注。
 *
 * 长度按 **Unicode 码点**计，不按 `String.length`——后者数的是 UTF-16 码元，
 * 一个 emoji 会被算成 2，用户看到的「200 字」与实际限制会不一致。
 *
 * `note` 在聚合里是**可空**的（不填备注是常态）；[Note] 本身一旦存在就不能是空白，
 * 这样「空备注」只有一种表达（`null`），不会出现 `null` 与 `""` 两种等价状态。
 *
 * 见 `docs/20-domain/ledger-model.md` 的 `INV-5`。
 */
@JvmInline
value class Note(val text: String) {

    init {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "备注若提供就不能是空白（不填请传 null）" }
        require(trimmed.codePointCount(0, trimmed.length) <= MAX_CODE_POINTS) {
            "备注最多 $MAX_CODE_POINTS 个字，实际 ${trimmed.codePointCount(0, trimmed.length)}"
        }
    }

    override fun toString(): String = text

    companion object {
        /** `INV-5` 的上界。 */
        const val MAX_CODE_POINTS = 200
    }
}
