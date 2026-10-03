package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.CategoryName
import com.jizhangbao.ledger.domain.repository.CategoryRepository

/**
 * 分类名唯一性检查（`REQ-004/AC-5` `BR-3` `BR-7`）。
 *
 * ## 为什么它是应用层的、而且是**一个**函数
 *
 * 「所有分类里不能重名」是**跨聚合**规则：单个 `Category` 看不见别人，
 * 所以它不属于聚合（放进 `init` 会得到一个测不到的规则）。
 *
 * 而新建与改名**都要**这条规则 —— 写两遍就是 `T-011/BR-6` 那个坑：
 * 两条路径各有一份校验，迟早漂移，其中一条会漏掉后来新增的细则。
 * 所以它做成仓储上的一个扩展函数，两处调用同一个实现。
 *
 * ## 两条细则
 *
 * - **只比未归档的**：归档的「宠物」不该挡住用户重新建一个「宠物」
 *   （他要的就是"别让我选到旧的"，而不是"永远不能再用这个名字"）。
 * - **改名时排除自己**（`excluding`）：否则"把 A 改成 A"会被判成重名。
 */
internal suspend fun CategoryRepository.ensureNameAvailable(
    name: CategoryName,
    excluding: CategoryId? = null,
): Outcome<Unit> {
    val loaded = all()
    if (loaded is Outcome.Err) return loaded

    val clash = (loaded as Outcome.Ok).value.any { existing ->
        existing.id != excluding &&
            !existing.archived &&
            existing.name.normalized == name.normalized
    }

    return if (clash) Outcome.Err(LedgerError.CategoryNameTaken) else Outcome.Ok(Unit)
}
