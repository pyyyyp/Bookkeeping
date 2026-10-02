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
}
