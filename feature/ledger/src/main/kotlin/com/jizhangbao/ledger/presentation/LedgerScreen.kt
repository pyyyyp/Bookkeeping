package com.jizhangbao.ledger.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhangbao.ledger.R
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 记账界面的对外入口。
 *
 * 签名里**没有内部类型**：`LedgerViewModel` 是 `internal` 的，
 * 所以这里不接收它作参数，而是在函数体内取——这样 `:app` 只需调用 `LedgerRoute()`，
 * 不必看见 ViewModel 的类型（模块的内部实现不泄露到公共 API）。
 *
 * @param onEntriesChanged 账本数据发生变化时被调用（成功记账或删除各一次）。
 *   由组合根用来通知其他上下文重算（`T-009`：Insight 的合计）。本模块**不认识** Insight，
 *   它只是把"我这里变了"说出来 —— 这是 R2 之下唯一可行的做法。
 * @param header 界面顶部的**通用插槽**（表单之上）。`:app` 用它把合计区放进来，
 *   于是 Ledger 不必知道合计区是谁、属于哪个上下文。
 */
@Composable
fun LedgerRoute(
    onEntriesChanged: () -> Unit = {},
    header: @Composable () -> Unit = {},
) {
    LedgerRoute(
        viewModel = hiltViewModel(),
        onEntriesChanged = onEntriesChanged,
        header = header,
    )
}

@Composable
internal fun LedgerRoute(
    viewModel: LedgerViewModel,
    onEntriesChanged: () -> Unit = {},
    header: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 修订号变大 = 账本数据变了。初始的 0 不触发（那时还没发生任何变化）
    LaunchedEffect(state.entriesRevision) {
        if (state.entriesRevision > 0) onEntriesChanged()
    }

    LedgerScreen(
        state = state,
        onAmountChange = viewModel::onAmountChange,
        onDirectionChange = viewModel::onDirectionChange,
        onCategorySelected = viewModel::onCategorySelected,
        onOccurredAtChange = viewModel::onOccurredAtChange,
        onNoteChange = viewModel::onNoteChange,
        onSave = viewModel::onSave,
        onEditRequested = viewModel::onEditRequested,
        onEditCancel = viewModel::onEditCancelled,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteCancelled = viewModel::onDeleteCancelled,
        header = header,
    )
}

/**
 * 记账界面骨架：顶部插槽、表单、最近账目。
 *
 * 只负责布局与「哪个回调接到哪个子组件」，具体控件在
 * [EntryForm] 与 [EntriesSection] 里——这样这个函数不必既管骨架又管细节。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerScreen(
    state: LedgerUiState,
    onAmountChange: (String) -> Unit,
    onDirectionChange: (com.jizhangbao.core.domain.EntryDirection) -> Unit,
    onCategorySelected: (com.jizhangbao.ledger.domain.model.CategoryId) -> Unit,
    onOccurredAtChange: (Instant) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onEditRequested: (com.jizhangbao.ledger.domain.model.LedgerEntry) -> Unit,
    onEditCancel: () -> Unit,
    onDeleteRequested: (com.jizhangbao.ledger.domain.model.LedgerEntry) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteCancelled: () -> Unit,
    header: @Composable () -> Unit = {},
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.ledger_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            header()

            EntryForm(
                state = state,
                zone = zone,
                onAmountChange = onAmountChange,
                onDirectionChange = onDirectionChange,
                onCategorySelected = onCategorySelected,
                onDateClick = { showDatePicker = true },
                onNoteChange = onNoteChange,
                onSave = onSave,
                onEditCancel = onEditCancel,
            )

            HorizontalDivider()

            EntriesSection(
                entries = state.entries,
                zone = zone,
                showDeletedNotice = state.deletedNotice,
                onEdit = onEditRequested,
                onDelete = onDeleteRequested,
            )
        }
    }

    if (showDatePicker) {
        OccurredAtPickerDialog(
            occurredAt = state.occurredAt,
            zone = zone,
            onOccurredAtChange = onOccurredAtChange,
            onDismiss = { showDatePicker = false },
        )
    }

    // 二次确认：删除是物理删除且不可恢复（ADR-0005），所以这一步不是装饰
    state.pendingDelete?.let { target ->
        DeleteConfirmDialog(
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteCancelled,
        )
    }
}

/**
 * 删除前的二次确认。
 *
 * 刻意**不显示金额**：那句话会变成「删除 ¥12.50 这条吗」，
 * 而对话框的作用是让人停一下，不是帮他确认细节——细节在列表里已经看过了。
 * 重要的是把**后果**写清楚（不可恢复）。
 */
@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ledger_delete_title)) },
        text = { Text(stringResource(R.string.ledger_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.ledger_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ledger_cancel))
            }
        },
    )
}

/**
 * 选择「发生日期」的对话框。
 *
 * 选择器返回的是**那一天的 UTC 零点**，所以取回日期后要按本机时区重算零点：
 * 否则东八区会把 10-01 记成 09-30。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OccurredAtPickerDialog(
    occurredAt: Instant,
    zone: ZoneId,
    onOccurredAtChange: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = occurredAt.toEpochMilli())

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        onOccurredAtChange(picked.atStartOfDay(zone).toInstant())
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.ledger_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ledger_cancel))
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
