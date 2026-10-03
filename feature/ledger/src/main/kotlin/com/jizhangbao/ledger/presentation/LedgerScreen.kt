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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.ledger.R
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
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
        onCategoriesChanged = viewModel::onCategoriesChanged,
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
    /** 分类管理界面关闭后被调用（`REQ-004`）：分类清单可能变了，要重新拉一次。 */
    onCategoriesChanged: () -> Unit,
    onDeleteRequested: (com.jizhangbao.ledger.domain.model.LedgerEntry) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteCancelled: () -> Unit,
    header: @Composable () -> Unit = {},
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }

    Scaffold(
        topBar = { LedgerTopBar(onCategoriesClick = { showCategories = true }) },
    ) { innerPadding ->
        // ⚠️ **整页一个 `LazyColumn`**（`T-016`）：顶部插槽、表单、列表头部、条目行都是它的 item。
        //
        // 它取代了 T-011 的临时结构（外层 verticalScroll + 列表用普通 Column）。那个结构当时
        // 修好了"列表够不到"，但每加一个区块都要重新确认"列表还在不在屏内" —— T-009 加合计区时
        // 就是因为没人确认这一点，制造了 T-011 才发现的那个回归。现在"列表是页面的一部分"
        // 在结构上成立，而且行恢复按需组合（不再一次性组合 200 条）。
        //
        // ⚠️ 反过来做（`LazyColumn` 嵌进可滚动容器）会因无限高度约束直接崩，别再试。
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { header() }

            entryFormItem(
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

            entriesHeaderItem(
                entriesCount = state.entries.size,
                showDeletedNotice = state.deletedNotice,
                // REQ-006/AC-3：跳过了几条读不出来的行，界面上要说出来
                unreadableEntries = state.unreadableEntries,
            )

            entriesItems(
                entries = state.entries,
                zone = zone,
                // 名字由状态解析：含用户自建与已归档的分类
                categoryName = state::categoryName,
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

    // 二次确认：删除是物理删除且不可恢复（ADR-0005），所以这一步不是装饰。
    // 用 if 而不是 let：待删的那一条只在状态里存在，这里不需要它的内容
    // （对话框刻意不显示金额，见下面的说明）
    if (state.pendingDelete != null) {
        DeleteConfirmDialog(onConfirm = onDeleteConfirmed, onDismiss = onDeleteCancelled)
    }

    // 分类管理：全屏对话框，不引入导航依赖（REQ-004）
    if (showCategories) {
        CategoryManagerDialog(
            onDismiss = {
                showCategories = false
                // 关掉之后通知一次：分类可能被增/改/归档过，选择器与列表的显示名都要跟着变
                onCategoriesChanged()
            },
        )
    }
}

/**
 * 分隔线 + 列表头部，作为 `LazyColumn` 的 item（`T-016`）。
 *
 * 分隔线与头部合成一个扩展：它们紧挨着、又都只与"列表开头的说明"有关，
 * 分成两个扩展只会让调用处更长。
 */
private fun LazyListScope.entriesHeaderItem(
    entriesCount: Int,
    showDeletedNotice: Boolean,
    unreadableEntries: Int,
) {
    item { HorizontalDivider() }
    item {
        EntriesSectionHeader(
            entriesCount = entriesCount,
            showDeletedNotice = showDeletedNotice,
            unreadableEntries = unreadableEntries,
        )
    }
}

/**
 * 表单那一块，作为 `LazyColumn` 的一个 item（`T-016`）。
 *
 * 抽出来的直接原因是 `LedgerScreen` 太长被 detekt 拦下；这一刀切得也对：
 * 那个函数现在只回答"页面由哪几块组成"，而"每一块内部怎么摆"交给各自的扩展。
 */
private fun LazyListScope.entryFormItem(
    state: LedgerUiState,
    zone: ZoneId,
    onAmountChange: (String) -> Unit,
    onDirectionChange: (EntryDirection) -> Unit,
    onCategorySelected: (CategoryId) -> Unit,
    onDateClick: () -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onEditCancel: () -> Unit,
) {
    item {
        EntryForm(
            state = state,
            zone = zone,
            onAmountChange = onAmountChange,
            onDirectionChange = onDirectionChange,
            onCategorySelected = onCategorySelected,
            onDateClick = onDateClick,
            onNoteChange = onNoteChange,
            onSave = onSave,
            onEditCancel = onEditCancel,
        )
    }
}

/**
 * 把账目行作为 `LazyColumn` 的 item 铺开（`T-016`）。
 *
 * 抽成 `LazyListScope` 的扩展是为了让 [LedgerScreen] 保持短小 —— 它只负责"页面由哪几块组成"，
 * 而"一块内部怎么铺"在这里。**必须给 `key`**：编辑或删除之后列表会重新组合，
 * 没有 key 的话行会错位（删除时尤其明显）。
 */
private fun LazyListScope.entriesItems(
    entries: List<LedgerEntry>,
    zone: ZoneId,
    categoryName: (CategoryId) -> String,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
) {
    items(items = entries, key = { it.id.value }) { entry ->
        EntryRow(
            entry = entry,
            zone = zone,
            categoryName = categoryName(entry.categoryId),
            onEdit = { onEdit(entry) },
            onDelete = { onDelete(entry) },
        )
    }
}

/**
 * 顶栏：标题 + 分类管理入口。
 *
 * 入口放这里是因为它管的是"设置"，而不是"这一笔" ——
 * 与表单里的控件区分开，避免它在用户的记账动线上挡路。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerTopBar(onCategoriesClick: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.ledger_title)) },
        actions = {
            TextButton(onClick = onCategoriesClick) {
                Text(stringResource(R.string.ledger_categories))
            }
        },
    )
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
