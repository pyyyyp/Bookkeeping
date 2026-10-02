package com.jizhangbao.ledger.presentation

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.ledger.R
import com.jizhangbao.ledger.domain.error.LedgerError

/**
 * 错误与枚举 → 字符串资源 的映射。
 *
 * 单独一个文件：这些函数既不属于表单也不属于列表，
 * 而且它们的共同点很重要 —— **用户可见文案只在这里与 `strings.xml` 相关**，
 * ViewModel 与领域层传过来的都是枚举，不是句子。
 */
internal fun EntryDirection.labelRes(): Int = when (this) {
    EntryDirection.Expense -> R.string.ledger_direction_expense
    EntryDirection.Income -> R.string.ledger_direction_income
}

internal fun AmountInputError.messageRes(): Int = when (this) {
    AmountInputError.Empty -> R.string.ledger_error_amount_empty
    AmountInputError.NotANumber -> R.string.ledger_error_amount_not_a_number
    AmountInputError.TooManyDecimals -> R.string.ledger_error_amount_too_many_decimals
    AmountInputError.NotPositive -> R.string.ledger_error_amount_not_positive
}

internal fun SaveFailure.messageRes(): Int = when (this) {
    SaveFailure.NoteTooLong -> R.string.ledger_error_note_too_long
    SaveFailure.Storage -> R.string.ledger_error_storage
    is SaveFailure.Rejected -> when (error) {
        LedgerError.CategoryRequired -> R.string.ledger_error_category_required
        LedgerError.AmountNotPositive -> R.string.ledger_error_amount_not_positive
        is LedgerError.NoteTooLong -> R.string.ledger_error_note_too_long
        else -> R.string.ledger_error_rejected
    }
}
