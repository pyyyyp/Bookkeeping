package com.jizhangbao.ledger.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.application.DeleteLedgerEntryUseCase
import com.jizhangbao.ledger.application.LoadRecentEntriesUseCase
import com.jizhangbao.ledger.application.RecordLedgerEntryUseCase
import com.jizhangbao.ledger.application.ReviseLedgerEntryUseCase
import com.jizhangbao.ledger.domain.model.CategoryCatalog
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
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
    private val deleteEntry: DeleteLedgerEntryUseCase,
    private val reviseEntry: ReviseLedgerEntryUseCase,
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

    /**
     * 用户点了某一条的「删除」——**先把确认交给用户，不碰数据**。
     *
     * `AC-8` 的「取消则不删」就是靠这个状态实现的：在 [onDeleteConfirmed] 之前，
     * 仓储一次都不会被调用。
     */
    fun onDeleteRequested(entry: LedgerEntry) {
        _uiState.update { it.copy(pendingDelete = entry, failure = null, deletedNotice = false) }
    }

    /** 用户取消 —— 清掉待确认项，什么都不删。 */
    fun onDeleteCancelled() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    /**
     * 用户点了某一条的「编辑」——把它的**现有值**填进表单，进入编辑态（`REQ-003/AC-1`）。
     *
     * 注意它不碰数据，只是换一个表单形态：之后的保存会走"替换"而不是"新增"。
     * 金额文本由分反向格式化而来，因此**不会**丢精度（不经过浮点）。
     */
    fun onEditRequested(entry: LedgerEntry) {
        _uiState.update {
            it.copy(
                editing = entry,
                amountText = entry.amount.toPlainYuanText(),
                direction = entry.direction,
                selectedCategoryId = entry.categoryId,
                occurredAt = entry.occurredAt,
                noteText = entry.note?.text.orEmpty(),
                amountError = null,
                failure = null,
                deletedNotice = false,
            )
        }
    }

    /**
     * 用户取消编辑（`REQ-003/AC-6`）。
     *
     * 表单回到"记一笔"形态：**清空金额与备注**（它们属于刚被放弃的那次编辑），
     * 但保留方向与分类（与保存成功后的行为一致：连着记同类账目很常见）。
     * 全程不碰仓储。
     */
    fun onEditCancelled() {
        _uiState.update {
            it.copy(editing = null, amountText = "", noteText = "", amountError = null, failure = null)
        }
    }

    /** 用户确认 —— 真正删除（物理删除，不可恢复，见 `ADR-0005`）。 */
    fun onDeleteConfirmed() {
        val target = _uiState.value.pendingDelete ?: return

        viewModelScope.launch {
            when (val result = deleteEntry(target.id)) {
                is Outcome.Ok -> {
                    _uiState.update {
                        it.copy(
                            pendingDelete = null,
                            deletedNotice = true,
                            entriesRevision = it.entriesRevision + 1,
                        )
                    }
                    refreshEntries()
                }
                is Outcome.Err -> _uiState.update {
                    // 删除失败时**保留** pendingDelete：让用户能重试，而不是以为删掉了
                    it.copy(failure = result.error.asSaveFailure())
                }
            }
        }
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
            val result = persist(current, amount, note)

            when (result) {
                is Outcome.Ok -> {
                    _uiState.update {
                        // 方向与分类保留（连着记几笔同类支出很常见），金额与备注清空；
                        // 编辑态也一并退出（AC-1：保存后回到"记一笔"形态）；
                        // 修订号 +1：让组合根知道"账本变了"（合计要重算，AC-5）
                        it.copy(
                            amountText = "",
                            noteText = "",
                            editing = null,
                            isSaving = false,
                            failure = null,
                            entriesRevision = it.entriesRevision + 1,
                        )
                    }
                    refreshEntries()
                }
                is Outcome.Err -> _uiState.update {
                    // 失败时**保留编辑态**：用户能改完再试，而不是以为改动生效了
                    it.copy(isSaving = false, failure = result.error.asSaveFailure())
                }
            }
        }
    }

    /**
     * 把表单当前的内容落库：**编辑态走"替换"，否则走"新增"**。
     *
     * 两者都归一成 `Outcome<Unit>`，因为之后要做的事完全一样（清表单或报错）。
     * 抽成独立函数的直接原因是 `onSave` 太长被 detekt 拦下，
     * 但这一刀切得是对的：它把"点保存会发生什么"与"保存之后界面怎么变"分开了。
     *
     * 编辑目标不存在时（`BR-5`）返回 `EntryNotFound` —— 界面会显示"这条记录已经不在了"。
     */
    private suspend fun persist(
        current: LedgerUiState,
        amount: Money,
        note: Note?,
    ): Outcome<Unit> {
        val editing = current.editing

        return if (editing != null) {
            when (
                val revised = reviseEntry(
                    target = editing,
                    direction = current.direction,
                    amount = amount,
                    categoryId = current.selectedCategoryId,
                    occurredAt = current.occurredAt,
                    note = note,
                )
            ) {
                is Outcome.Err -> Outcome.Err(revised.error)
                is Outcome.Ok -> Outcome.Ok(Unit)
            }
        } else {
            when (
                val recorded = recordEntry(
                    direction = current.direction,
                    amount = amount,
                    categoryId = current.selectedCategoryId,
                    occurredAt = current.occurredAt,
                    note = note,
                )
            ) {
                is Outcome.Err -> Outcome.Err(recorded.error)
                is Outcome.Ok -> Outcome.Ok(Unit)
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

    /**
     * 分 → 表单里的"元"文本（`12.50`）。
     *
     * **不经过浮点**：`12.50` 元 = 1250 分，用整数除与取余拼字符串，
     * 所以"填进表单再解析回来"必然得到同一个金额（`AC-2` 的往返不会悄悄改数）。
     * 与 `Money.toString()` 的区别只是不带 `¥` —— 输入框里不该出现货币符号。
     */
    private fun Money.toPlainYuanText(): String {
        val yuan = cents / 100
        val fen = (cents % 100).toString().padStart(2, '0')
        return "$yuan.$fen"
    }
}
