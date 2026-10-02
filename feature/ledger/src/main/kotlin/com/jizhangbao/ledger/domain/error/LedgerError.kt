package com.jizhangbao.ledger.domain.error

import com.jizhangbao.core.domain.DomainError

/**
 * Ledger 上下文的领域错误。
 *
 * 为什么需要它，而不是直接用共享内核的 `DomainError.InvalidInput`：
 * 内核那几个泛型分支**无法区分**「金额不合法」与「没选分类」，
 * 而 `REQ-001/AC-3` 与 `AC-4` 要求给出**各不相同**的提示。校验发生在领域层
 * （业务规则住在聚合里），所以错误也必须在领域层就能被区分。
 *
 * 技术性错误（数据库读写失败）**不在这里**：内核已有
 * [DomainError.Technical.Storage]，直接复用，不另造一套。
 *
 * ⚠️ 这个类型让内核的 `DomainError` 拥有了跨模块的实现者。
 * 若编译器不允许（sealed 的封闭性），则需要先改内核——见 `ADR-0006`。
 */
sealed interface LedgerError : DomainError {

    /** `INV-1`：金额必须大于 0 */
    data object AmountNotPositive : LedgerError

    /** `INV-2`：每条条目必须有分类 */
    data object CategoryRequired : LedgerError

    /** `INV-5`：备注过长 */
    data class NoteTooLong(val maxCodePoints: Int) : LedgerError

    /** 要删除 / 查询的条目不存在 */
    data object EntryNotFound : LedgerError

    /** `REQ-004/AC-6`：分类名不合法（空 / 只有空格 / 超过 20 字） */
    data object CategoryNameInvalid : LedgerError

    /** `REQ-004/BR-8`：分类至少要支持一个收支方向 */
    data object CategoryDirectionRequired : LedgerError

    /**
     * `REQ-004/AC-5`：分类名已被占用。
     *
     * ⚠️ 它由**应用层**产生，不是聚合抛的：唯一性是跨聚合规则（`BR-7`）。
     * 放在这里是因为它仍是**领域的失败语义**（界面要给出"这个名字已经有了"），
     * 而不是技术故障。
     */
    data object CategoryNameTaken : LedgerError

    /** 要改名 / 归档 / 恢复的分类不存在（与 `EntryNotFound` 同一个语义） */
    data object CategoryNotFound : LedgerError
}
