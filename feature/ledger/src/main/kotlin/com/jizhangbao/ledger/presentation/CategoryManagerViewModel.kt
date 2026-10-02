package com.jizhangbao.ledger.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.application.ArchiveCategoryUseCase
import com.jizhangbao.ledger.application.CreateCategoryUseCase
import com.jizhangbao.ledger.application.LoadCategoriesUseCase
import com.jizhangbao.ledger.application.RenameCategoryUseCase
import com.jizhangbao.ledger.application.RestoreCategoryUseCase
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 分类管理界面的状态持有者（`REQ-004`）。
 *
 * ## 为什么是**另一个** ViewModel
 *
 * 分类管理是一个独立的界面（对话框）：它有自己的输入、自己的忙碌状态、自己的失败提示。
 * 塞进 `LedgerViewModel` 会让那个类同时管两件事，而两者的生命周期也不同
 * （记账页常在，管理对话框只在打开时存在）。
 *
 * ## 仍然是零业务规则
 *
 * 名字合法性由 `Category` 判定、重名由用例判定（`BR-7`），
 * 这里只做三件事：把输入装进领域值、调用用例、把结果翻成界面状态。
 */
@HiltViewModel
internal class CategoryManagerViewModel @Inject constructor(
    private val loadCategories: LoadCategoriesUseCase,
    private val createCategory: CreateCategoryUseCase,
    private val renameCategory: RenameCategoryUseCase,
    private val archiveCategory: ArchiveCategoryUseCase,
    private val restoreCategory: RestoreCategoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryManagerUiState())
    val uiState: StateFlow<CategoryManagerUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onNewNameChange(text: String) {
        _uiState.update { it.copy(newName = text, failure = null) }
    }

    /** 切换新分类支持的方向。至少留一个 —— 全部取消时保持原样，而不是变成一个建不出来的分类。 */
    fun onNewDirectionToggled(direction: EntryDirection) {
        _uiState.update { current ->
            val next = if (direction in current.newDirections) {
                current.newDirections - direction
            } else {
                current.newDirections + direction
            }
            current.copy(newDirections = next.ifEmpty { current.newDirections }, failure = null)
        }
    }

    fun onCreate() {
        val current = _uiState.value
        mutate {
            createCategory(current.newName, current.newDirections)
        }
        // 成功后清空输入框（在 mutate 里统一做会污染其它操作）
        _uiState.update { it.copy(newName = "") }
    }

    fun onRenameStart(category: Category) {
        _uiState.update { it.copy(renamingId = category.id, renameText = category.displayName, failure = null) }
    }

    fun onRenameTextChange(text: String) {
        _uiState.update { it.copy(renameText = text, failure = null) }
    }

    fun onRenameCancel() {
        _uiState.update { it.copy(renamingId = null, renameText = "", failure = null) }
    }

    fun onRenameConfirm() {
        val current = _uiState.value
        val id = current.renamingId
        if (id != null) {
            mutate { renameCategory(id, current.renameText) }
            _uiState.update { it.copy(renamingId = null, renameText = "") }
        }
    }

    fun onArchive(category: Category) {
        mutate { archiveCategory(category.id) }
    }

    fun onRestore(category: Category) {
        mutate { restoreCategory(category.id) }
    }

    fun onFailureShown() {
        _uiState.update { it.copy(failure = null) }
    }

    /**
     * 所有改动走同一个入口：置忙 → 跑用例 → 把错误翻成界面枚举 → **成功才重新拉清单**。
     *
     * 失败时不刷新，所以"失败后界面上看到的还是刚才那份清单"是天然成立的
     * （不需要回滚，因为没有乐观更新）。
     */
    private fun mutate(block: suspend () -> Outcome<*>) {
        _uiState.update { it.copy(isBusy = true, failure = null) }

        viewModelScope.launch {
            val result = block()
            _uiState.update {
                it.copy(isBusy = false, failure = (result as? Outcome.Err)?.error?.asCategoryFailure())
            }
            if (result is Outcome.Ok) refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            when (val result = loadCategories()) {
                is Outcome.Ok -> _uiState.update { it.copy(categories = result.value) }
                is Outcome.Err -> _uiState.update { it.copy(failure = CategoryFailure.Storage) }
            }
        }
    }

    private fun DomainError.asCategoryFailure(): CategoryFailure = when (this) {
        LedgerError.CategoryNameInvalid -> CategoryFailure.NameInvalid
        LedgerError.CategoryNameTaken -> CategoryFailure.NameTaken
        LedgerError.CategoryNotFound -> CategoryFailure.NotFound
        else -> CategoryFailure.Storage
    }
}
