package com.jizhangbao.ledger.testing

import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.repository.CategoryRepository

/**
 * 内存版分类仓储（`REQ-004`）。
 *
 * 与 [FakeLedgerEntryRepository] 同一个立场：只回答"方法被调用了吗"与"结果由测试指定时返回什么"。
 * **唯一性不在这里实现** —— 那是用例的职责（`BR-7`），
 * 在 fake 里再实现一遍就等于让测试去验证测试自己。
 */
internal class FakeCategoryRepository : CategoryRepository {

    private val categories = mutableListOf<Category>()

    /** 非 null 时 `all` 直接返回它，用于制造失败场景。 */
    var allOutcome: Outcome<List<Category>>? = null

    /** 非 null 时 `add` 直接返回它。 */
    var addOutcome: Outcome<Unit>? = null

    /** 非 null 时 `update` 直接返回它。 */
    var updateOutcome: Outcome<Unit>? = null

    override suspend fun add(category: Category): Outcome<Unit> {
        addOutcome?.let { return it }
        categories += category
        return Outcome.Ok(Unit)
    }

    override suspend fun update(category: Category): Outcome<Unit> {
        val forced = updateOutcome
        // 用表达式 if（而不是三条 return 路径）：detekt 的 ReturnCount 上限是 2
        return if (forced != null) {
            forced
        } else {
            val index = categories.indexOfFirst { it.id == category.id }
            if (index < 0) {
                Outcome.Err(LedgerError.CategoryNotFound)
            } else {
                categories[index] = category
                Outcome.Ok(Unit)
            }
        }
    }

    override suspend fun all(): Outcome<List<Category>> =
        allOutcome ?: Outcome.Ok(categories.toList())

    override suspend fun byId(id: CategoryId): Outcome<Category> =
        categories.firstOrNull { it.id == id }
            ?.let { Outcome.Ok(it) }
            ?: Outcome.Err(LedgerError.CategoryNotFound)

    /** 真的存进去的分类。 */
    fun stored(): List<Category> = categories.toList()
}
