package com.jizhangbao.ledger.presentation

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryCatalog
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import java.time.Instant

/**
 * 记账界面的状态。
 *
 * 所有字段都是**不可变数据**：界面只读它、ViewModel 只产出新副本，
 * 因此不存在「界面读到一半状态被改」的情况。
 *
 * `amountError` 与 `failure` 都是**枚举而不是文案** —— 文案在资源文件里（见 [AmountInputError]）。
 */
internal data class LedgerUiState(
    val amountText: String,
    val direction: EntryDirection,
    val selectedCategoryId: CategoryId?,
    val occurredAt: Instant,
    val noteText: String,
    val entries: List<LedgerEntry>,
    val amountError: AmountInputError?,
    /** 保存失败（含领域规则拒绝与存储失败），由界面映射成提示。 */
    val failure: SaveFailure?,
    val isSaving: Boolean,
    /**
     * 等待用户二次确认的那一条。
     *
     * 删除是**物理删除且不可恢复**（`ADR-0005`），所以「确认」这一步必须由状态显式表达：
     * 只要它非空，界面就必须拦着不让删；用户取消时它被清空，**仓储一次都不会被调用**。
     */
    val pendingDelete: LedgerEntry?,
    /** 刚刚删掉了一条 —— 用于给出反馈，不让操作结果静默。 */
    val deletedNotice: Boolean,
) {

    /** 当前方向下可选的分类（预置清单已按方向过滤）。 */
    val selectableCategories: List<Category>
        get() = CategoryCatalog.PRESET.forDirection(direction)

    val canPressSave: Boolean
        get() = !isSaving

    companion object {
        /** 初始状态必须由调用方给出「现在」——不在这里 `Instant.now()`，否则状态构造就不纯了。 */
        fun initial(now: Instant): LedgerUiState = LedgerUiState(
            amountText = "",
            direction = EntryDirection.Expense,
            selectedCategoryId = null,
            occurredAt = now,
            noteText = "",
            entries = emptyList(),
            amountError = null,
            failure = null,
            isSaving = false,
            pendingDelete = null,
            deletedNotice = false,
        )
    }
}

/** 保存失败的来源。界面据此选择提示文案。 */
internal sealed interface SaveFailure {
    /** 领域规则拒绝（例如没选分类） */
    data class Rejected(val error: com.jizhangbao.core.domain.DomainError) : SaveFailure

    /** 备注过长 —— 在界面层先拦下，因为「输入太长」是正常路径而不是程序错误 */
    data object NoteTooLong : SaveFailure

    /** 存储失败 */
    data object Storage : SaveFailure
}
