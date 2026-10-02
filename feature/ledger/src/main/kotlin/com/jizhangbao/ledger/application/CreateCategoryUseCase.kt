package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * 新建一个自定义分类（`REQ-004/AC-1`）。
 *
 * ## 三步，顺序有讲究
 *
 * 1. **先让聚合校验名字**（空 / 超长）—— 规则住在聚合里，用例不重复判断；
 * 2. **再查唯一性** —— 这是跨聚合规则，只能在这一层做（`BR-7`），
 *    实现与"改名"共用同一个函数（`ensureNameAvailable`），避免两份规则漂移；
 * 3. **最后落库**。
 *
 * 校验不过就**不碰仓储**，所以"非法输入不会留下半个分类"是天然成立的。
 *
 * 标识用 [CategoryId.new] 生成，而不是按名字做 slug：名字可以改，标识必须稳定；
 * 而且按名字生成会让「宠物」与「宠 物」拿到两个不同 id，唯一性检查就形同虚设。
 */
class CreateCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {

    suspend operator fun invoke(
        rawName: String,
        directions: Set<EntryDirection>,
    ): Outcome<CategoryId> {
        val created = Category.create(CategoryId.new(), rawName, directions)
        if (created is Outcome.Err) return created

        val category = (created as Outcome.Ok).value

        return when (val available = repository.ensureNameAvailable(category.name)) {
            is Outcome.Err -> Outcome.Err(available.error)
            is Outcome.Ok -> when (val stored = repository.add(category)) {
                is Outcome.Err -> Outcome.Err(stored.error)
                is Outcome.Ok -> Outcome.Ok(category.id)
            }
        }
    }
}
