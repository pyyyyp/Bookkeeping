package com.jizhangbao.ledger.domain.model

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError

/**
 * 分类：用户对收支的归类（「餐饮」「工资」）。
 *
 * ## 它是聚合根，不是值对象（`REQ-004` / `ADR-0009`）
 *
 * `T-007` 时它是值对象，因为预置分类随应用内置、用户不能改：没有生命周期行为，
 * 也没有需要原子维护的不变式。`REQ-004` 让用户能建自己的分类之后，三件事同时成立：
 *
 * - 有**生命周期**：新建 → 改名 → 归档 → 恢复；
 * - 有**标识与身份**：条目存的是 [CategoryId]，所以改名不该产生第二个分类；
 * - 有**要一起变的状态**：名字与 `archived` 必须一致地变。
 *
 * ## 唯一性**不在这里**（`REQ-004/BR-7`）
 *
 * 「所有分类里不能重名」是**跨聚合**规则：单个 `Category` 看不见别人。
 * 塞进 `init` 会得到一个聚合内无法验证、也测不准的规则。它属于应用层（用例创建前查一次）。
 *
 * ## 归档而不是删除（`ADR-0009`）
 *
 * 条目引用了分类，物理删除要么留悬空引用、要么得改写历史。
 * 注意这与**条目**的物理删除不矛盾：条目没有下游引用者。
 */
class Category private constructor(
    val id: CategoryId,
    val name: CategoryName,
    val directions: Set<EntryDirection>,
    val archived: Boolean,
) {

    init {
        require(directions.isNotEmpty()) { "分类至少要支持一个收支方向：${name.value}" }
    }

    /**
     * 展示名。
     *
     * 之所以留这个属性而不是让界面写 `category.name.value`：
     * 界面绝大多数地方只关心"给人看的那个字符串"，多一层 `.value` 只是噪音。
     */
    val displayName: String get() = name.display

    /** 这个分类是否可用于某个收支方向。 */
    fun supports(direction: EntryDirection): Boolean = direction in directions

    /**
     * 改名。返回**新实例**，标识与归档状态不变（`REQ-004/AC-2`：
     * 历史条目跟着显示新名字，因为它们是同一个分类改了名，不是复制出一份）。
     */
    fun rename(newName: CategoryName): Category = Category(id, newName, directions, archived)

    /**
     * 归档（`ADR-0009`）：不再出现在记账选择器，历史条目仍能解析出名字。
     *
     * 幂等：已经归档时返回自己，不产生无意义的新实例。
     */
    fun archive(): Category = if (archived) this else Category(id, name, directions, true)

    /** 撤销归档。同样幂等。 */
    fun restoreFromArchive(): Category = if (!archived) this else Category(id, name, directions, false)

    /**
     * 相等性按**全部字段**（与 `LedgerEntry` 同一个理由，见那条 KDoc）：
     * 本类不可变，若只按标识相等，"改过名的分类"会等于"改之前的它"，
     * 状态差分就会丢掉这次改名（`T-011` 真机上真的踩过）。
     */
    override fun equals(other: Any?): Boolean =
        this === other || (
            other is Category &&
                other.id == id &&
                other.name == name &&
                other.directions == directions &&
                other.archived == archived
            )

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + directions.hashCode()
        result = 31 * result + archived.hashCode()
        return result
    }

    override fun toString(): String =
        "Category(${id.value}, ${name.value}, $directions, archived=$archived)"

    companion object {

        /**
         * 新建分类。
         *
         * 名字不合法（空 / 只有空格 / 超过 20 字）时返回 [LedgerError.CategoryNameInvalid] ——
         * 那是**正常路径**（用户输错了），所以是领域返回值而不是异常。
         * 唯一性不在这里（见类 KDoc）。
         */
        fun create(
            id: CategoryId,
            rawName: String,
            directions: Set<EntryDirection>,
        ): Outcome<Category> {
            val name = CategoryName.ofOrNull(rawName)
                ?: return Outcome.Err(LedgerError.CategoryNameInvalid)

            return if (directions.isEmpty()) {
                Outcome.Err(LedgerError.CategoryDirectionRequired)
            } else {
                Outcome.Ok(Category(id, name, directions, archived = false))
            }
        }

        /**
         * 从存储恢复。
         *
         * 与 [create] 分开（`place` / `restore` 之分）：存储里的数据写入时已校验过，
         * 现在不合法说明**被外部改坏了** —— 那是异常状况，应当立刻暴露，
         * 而不是变成一条"用户没提交成功"的提示。
         */
        fun restore(
            id: CategoryId,
            name: String,
            directions: Set<EntryDirection>,
            archived: Boolean,
        ): Category = Category(
            id = id,
            name = CategoryName.restore(name),
            directions = directions,
            archived = archived,
        )
    }
}
