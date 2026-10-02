package com.jizhangbao.ledger.domain.model

import com.jizhangbao.core.domain.EntryDirection

/**
 * 分类：用户对收支的归类（「餐饮」「工资」）。
 *
 * **本卡里它是值对象，不是聚合**——预置分类随应用内置，用户不能增删改，
 * 因此没有需要原子维护的不变式，也没有生命周期行为。
 *
 * 什么时候它会升级为聚合：**自定义分类**落地时。那时会出现
 * 「同名不可重复」「被引用的分类不可删除」这类跨条目的一致性要求，
 * 需要重新裁决并新增 ADR。**现在不预设**（见 `docs/20-domain/ledger-model.md`）。
 *
 * `directions` 是**集合**而不是可空单值：有的分类两种方向都成立（「其他」），
 * 用可空表达会引入「null 到底是什么意思」的歧义。
 */
data class Category(
    val id: CategoryId,
    val displayName: String,
    val directions: Set<EntryDirection>,
) {
    init {
        require(displayName.isNotBlank()) { "分类显示名不可为空" }
        require(displayName.trim().length <= MAX_NAME_LENGTH) {
            "分类显示名最多 $MAX_NAME_LENGTH 个字：$displayName"
        }
        require(directions.isNotEmpty()) { "分类至少要支持一个收支方向：$displayName" }
    }

    /** 这个分类是否可用于某个收支方向。 */
    fun supports(direction: EntryDirection): Boolean = direction in directions

    companion object {
        const val MAX_NAME_LENGTH = 20
    }
}
