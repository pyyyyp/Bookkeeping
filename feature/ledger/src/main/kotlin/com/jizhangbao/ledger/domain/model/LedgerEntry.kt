package com.jizhangbao.ledger.domain.model

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.error.LedgerError
import java.time.Instant

/**
 * 账目条目 —— Ledger 上下文的聚合根，也是本上下文**唯一**的聚合。
 *
 * 为什么条目本身就是聚合根（而不是「账本」大聚合）：
 * - 不变式只作用在**单条记录内部**（金额为正、必有分类、备注不超长）
 * - 条目之间没有需要原子维护的一致性 —— 本卡没有账户，也就**没有余额**要守
 * - 「聚合尽量小」：一个大聚合会把所有条目的写入串行化，代价远大于收益
 *
 * 若将来引入账户与余额，余额的一致性会成为**新的跨条目不变式**——
 * 那时需要重新裁决聚合边界并新增 ADR（见 `ADR-0005`）。
 *
 * ⚠️ 刻意**不是** `data class`：`copy()` 会绕过 `init` 里的不变式。
 * 相等性按**标识**判断 —— 实体的语义是「同一个标识就是同一条记录」。
 *
 * 见 `docs/20-domain/ledger-model.md`。
 */
class LedgerEntry private constructor(
    val id: LedgerEntryId,
    val direction: EntryDirection,
    val amount: Money,
    val categoryId: CategoryId,
    val occurredAt: Instant,
    val bookedAt: Instant,
    val note: Note?,
) {

    init {
        // INV-1：Money 已保证非负，这里排除「0 元条目」——它没有业务含义
        require(amount > Money.ZERO) { "INV-1：条目金额必须大于 0，实际为 $amount" }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is LedgerEntry && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "LedgerEntry(${id.value}, $direction, $amount, ${categoryId.value}, " +
            "occurredAt=$occurredAt, bookedAt=$bookedAt, note=${note?.text})"

    companion object {

        /**
         * 记录一笔新条目。
         *
         * `categoryId` 与 `amount` 之所以允许「不合法的值」传进来（可空 / 零），
         * 是为了把 [LedgerError.CategoryRequired] 与 [LedgerError.AmountNotPositive]
         * 表达成**领域返回值**而不是异常 —— 用户在界面上没选分类是正常路径，
         * 不是程序错误（见 `REQ-001/AC-3` `AC-4`）。
         *
         * `occurredAt` 与 `bookedAt` 刻意**不做先后校验**：是否允许记录未来时间的账
         * 是待定业务规则（`Q-021`），没有定论前不写成规则、也不静默校验。
         */
        fun record(
            direction: EntryDirection,
            amount: Money,
            categoryId: CategoryId?,
            occurredAt: Instant,
            note: Note? = null,
            bookedAt: Instant = Instant.now(),
            id: LedgerEntryId = LedgerEntryId.new(),
        ): Outcome<LedgerEntry> {
            // 两个 return，正好是 detekt 的 ReturnCount 上界。
            // 注意：`categoryId` 必须先用局部 val 接住，`when` 的分支条件**不会**
            // 让 Kotlin 对参数做智能转换（实测编译报错），而 `?:` 会。
            val category = categoryId ?: return Outcome.Err(LedgerError.CategoryRequired)

            return if (amount <= Money.ZERO) {
                Outcome.Err(LedgerError.AmountNotPositive)
            } else {
                Outcome.Ok(
                    LedgerEntry(
                        id = id,
                        direction = direction,
                        amount = amount,
                        categoryId = category,
                        occurredAt = occurredAt,
                        bookedAt = bookedAt,
                        note = note,
                    ),
                )
            }
        }

        /**
         * 从存储恢复一个已存在的条目。
         *
         * 与 [record] 分开（工厂的 `place` / `restore` 之分）：恢复的数据在那个时刻
         * 是合法的，若现在不合法，说明**数据被外部改坏了**——那是异常状况，
         * 应当立刻暴露而不是变成一条「用户没提交成功」的错误提示。
         */
        fun restore(
            id: LedgerEntryId,
            direction: EntryDirection,
            amount: Money,
            categoryId: CategoryId,
            occurredAt: Instant,
            bookedAt: Instant,
            note: Note?,
        ): LedgerEntry = LedgerEntry(
            id = id,
            direction = direction,
            amount = amount,
            categoryId = categoryId,
            occurredAt = occurredAt,
            bookedAt = bookedAt,
            note = note,
        )
    }
}
