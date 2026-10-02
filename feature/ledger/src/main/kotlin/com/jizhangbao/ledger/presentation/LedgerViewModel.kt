package com.jizhangbao.ledger.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.application.LoadRecentEntriesUseCase
import com.jizhangbao.ledger.application.RecordLedgerEntryUseCase
import com.jizhangbao.ledger.domain.model.CategoryCatalog
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.Note
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * 记账界面的状态持有者。
 *
 * ## 它不做什么
 *
 * **一条业务规则都没有**。「金额必须大于 0」「必须有分类」由聚合判定；
 * 这里只做三件事：把输入文本解析成领域值、调用用例、把结果翻译成界面状态。
 *
 * ## 为什么列表是「保存后重新查询」而不是 Flow
 *
 * 本卡不做反应式观察（见仓储接口的说明），所以保存成功后显式 [refreshEntries]。
 * 代价是多一次查询，好处是不必为一个尚未兑现的需要引入协程依赖。
 */
@HiltViewModel
internal class LedgerViewModel @Inject constructor(
    private val recordEntry: RecordLedgerEntryUseCase,
    private val loadEntries: LoadRecentEntriesUseCase,
    clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LedgerUiState.initial(clock.instant()))
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    init {
        refreshEntries()
    }

    fun onAmountChange(text: String) {
        // 用户一开始改金额，就把上一次的报错撤掉——否则提示会赖在屏幕上不走
        _uiState.update { it.copy(amountText = text, amountError = null) }
    }

    fun onDirectionChange(direction: EntryDirection) {
        _uiState.update { current ->
            // 换方向后，原先选的分类可能已经不适用（例如从「支出」切到「收入」还选着「餐饮」）。
            // 不静默保留：那会让用户提交一个与方向矛盾的分类。
            val stillSelectable = current.selectedCategoryId?.let { id ->
                CategoryCatalog.PRESET.forDirection(direction).any { it.id == id }
            } ?: false
            current.copy(
                direction = direction,
                selectedCategoryId = if (stillSelectable) current.selectedCategoryId else null,
                failure = null,
            )
        }
    }

    fun onCategorySelected(id: CategoryId) {
        _uiState.update { it.copy(selectedCategoryId = id, failure = null) }
    }

    fun onOccurredAtChange(instant: Instant) {
        _uiState.update { it.copy(occurredAt = instant) }
    }

    fun onNoteChange(text: String) {
        _uiState.update { it.copy(noteText = text, failure = null) }
    }

    fun onFailureShown() {
        _uiState.update { it.copy(failure = null) }
    }

    fun onSave() {
        val current = _uiState.value
        val amount = when (val parsed = parseYuanToMoney(current.amountText)) {
            is AmountInput.Invalid -> {
                _uiState.update { it.copy(amountError = parsed.error) }
                return
            }
            is AmountInput.Valid -> parsed.money
        }

        // 备注长度先判一次：`Note` 的 init 是**抛异常**的（值对象自校验），
        // 而「用户输入太长」是正常路径，不该以异常的形式走到界面。
        // 值对象那一层不会因此少 —— 它仍然保护所有其它调用方。
        val trimmedNote = current.noteText.trim()
        if (trimmedNote.codePointCount(0, trimmedNote.length) > Note.MAX_CODE_POINTS) {
            _uiState.update { it.copy(failure = SaveFailure.NoteTooLong) }
            return
        }
        val note = if (trimmedNote.isEmpty()) null else Note(trimmedNote)

        _uiState.update { it.copy(isSaving = true, failure = null) }

        viewModelScope.launch {
            val result = recordEntry(
                direction = current.direction,
                amount = amount,
                categoryId = current.selectedCategoryId,
                occurredAt = current.occurredAt,
                note = note,
            )

            when (result) {
                is Outcome.Ok -> {
                    _uiState.update {
                        // 方向与分类保留（连着记几笔同类支出很常见），金额与备注清空
                        it.copy(amountText = "", noteText = "", isSaving = false, failure = null)
                    }
                    refreshEntries()
                }
                is Outcome.Err -> _uiState.update {
                    it.copy(isSaving = false, failure = result.error.asSaveFailure())
                }
            }
        }
    }

    private fun refreshEntries() {
        viewModelScope.launch {
            when (val result = loadEntries()) {
                is Outcome.Ok -> _uiState.update { it.copy(entries = result.value) }
                is Outcome.Err -> _uiState.update { it.copy(failure = SaveFailure.Storage) }
            }
        }
    }

    private fun DomainError.asSaveFailure(): SaveFailure =
        if (this == DomainError.Technical.Storage) SaveFailure.Storage else SaveFailure.Rejected(this)
}
