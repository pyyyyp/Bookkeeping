package com.jizhangbao.ledger.presentation

import androidx.annotation.StringRes
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.ledger.R
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryCatalog
import com.jizhangbao.ledger.domain.model.CategoryId

/**
 * 分类管理界面的状态（`REQ-004`）。
 *
 * 与 `LedgerUiState` 分开：这是**另一个界面**（一个对话框），
 * 把它塞进记账状态会让"记账表单"与"分类列表"两组字段混在一起，
 * 而它们从不同时被用。
 */
internal data class CategoryManagerUiState(
    val categories: List<Category> = emptyList(),
    val newName: String = "",
    val newDirections: Set<EntryDirection> = setOf(EntryDirection.Expense),
    /** 正在改名的那个分类；`null` 表示没有行处于改名态。 */
    val renamingId: CategoryId? = null,
    val renameText: String = "",
    val failure: CategoryFailure? = null,
    val isBusy: Boolean = false,
) {

    /**
     * 内置分类（只读，`REQ-004/AC-7`）。
     *
     * 用**预置清单本身**判断，而不是"id 以 custom- 开头"这类约定：
     * 约定会在将来某个改动里悄悄失效，而清单是唯一的事实源。
     */
    val presetCategories: List<Category>
        get() = categories.filter { CategoryCatalog.PRESET.byId(it.id) != null }

    val customCategories: List<Category>
        get() = categories.filter { CategoryCatalog.PRESET.byId(it.id) == null }

    val canCreate: Boolean
        get() = !isBusy && newName.isNotBlank() && newDirections.isNotEmpty()

    val canConfirmRename: Boolean
        get() = !isBusy && renameText.isNotBlank()
}

/** 分类操作失败的来源。界面据此选文案（与 `AmountInputError` 同一个做法：状态里放枚举，不放文案）。 */
internal enum class CategoryFailure {
    /** 名字为空 / 全是空格 / 超过 20 字 */
    NameInvalid,

    /** 已经有同名的（未归档）分类 */
    NameTaken,

    /** 这个分类已经不在了 */
    NotFound,

    /** 存储失败 */
    Storage,
}

/**
 * 失败枚举 → 文案资源。
 *
 * 映射放在界面层是刻意的：ViewModel 只产出枚举，文案只在资源文件里
 * （AGENTS.md 第 7 节第 12 条：禁止在代码里硬编码用户可见字符串）。
 */
@StringRes
internal fun CategoryFailure.messageRes(): Int = when (this) {
    CategoryFailure.NameInvalid -> R.string.category_error_name_invalid
    CategoryFailure.NameTaken -> R.string.category_error_name_taken
    CategoryFailure.NotFound -> R.string.category_error_not_found
    CategoryFailure.Storage -> R.string.category_error_storage
}
