package com.jizhangbao.ledger.data.local

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryId

/**
 * `Category` ↔ [CategoryEntity] 的转换（防腐层 ACL，R6 的落点）。
 *
 * 方向列表的编解码只在这里发生：数据库里是 `"Expense,Income"` 这样的字符串，
 * 领域里是 `Set<EntryDirection>`。**排序**后再拼接，让同一个集合永远得到同一串字符 ——
 * 否则"内容没变但字符串变了"会让 UPDATE 白写、也让测试变得不稳定。
 */
internal object CategoryMapper {

    private const val SEPARATOR = ","

    fun toEntity(category: Category): CategoryEntity = CategoryEntity(
        id = category.id.value,
        name = category.name.value,
        directions = category.directions
            .map { it.name }
            .sorted()
            .joinToString(SEPARATOR),
        archived = category.archived,
    )

    /**
     * 从存储恢复。
     *
     * 走 [Category.restore]：存进去时是合法的，读出来不合法说明**数据被外部改坏了**，
     * 必须立刻抛错（由仓储翻译成存储类错误），而不是变成一句「您没提交成功」。
     */
    fun toDomain(entity: CategoryEntity): Category = Category.restore(
        id = CategoryId(entity.id),
        name = entity.name,
        directions = entity.directions
            .split(SEPARATOR)
            .filter { it.isNotBlank() }
            .map { EntryDirection.valueOf(it) }
            .toSet(),
        archived = entity.archived,
    )
}
