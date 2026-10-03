package com.jizhangbao.ledger.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhangbao.ledger.R
import com.jizhangbao.ledger.domain.model.LedgerEntry
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy 年 MM 月")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * 分类下钻清单（`REQ-007`）——**组合根用的入口**。
 *
 * 为什么由 Ledger 暴露一个"自带 ViewModel"的组合式入口，而不是把状态提到 `:app`：
 * 下钻看的是**账目条目**，那是 Ledger 的模型；把它搬到组合根，等于让 `:app`
 * 认识 `LedgerEntry` 并自己拉数据 —— `:app` 只该负责**拼装**（`R8`），
 * 不该长出自己的数据流。所以组合根只说"用户点了哪个分类的哪个月"，
 * 剩下的都在这里。
 *
 * ## 每行能改能删（`REQ-011`），而且**没有第二套流程**
 *
 * `REQ-007` 当时做成只读，理由是"改动路径只能有一条"（两条路径各写一套校验与确认
 * 迟早漂移）。`REQ-011` 没有推翻这条理由，而是换了个做法：这里的「改」「删」
 * **只是把这一条交给账本页那套流程** ——
 *
 * - 「改」→ [LedgerViewModel.onEditRequested]，账本页的编辑表单随之打开并回填；
 * - 「删」→ [LedgerViewModel.onDeleteRequested]，账本页的**同一个** `DeleteConfirmDialog`
 *   随之弹出（文案逐字相同，因为它就是同一份）。
 *
 * ## ⚠️ 这里有一处**承重**的假设
 *
 * [hiltViewModel] 取到的 `LedgerViewModel` 与账本页 `LedgerRoute` 取到的是**同一个实例**
 * （Hilt 按 Activity 作用域，两者都在同一棵组合树里）。如果将来有人把这个对话框挪到
 * 别的 `ViewModelStoreOwner` 下面，它会静默变成**第二个** ViewModel ——
 * 表现是"点了改/删没反应"（状态没人渲染）。真机冒烟能立刻看出这一点
 * （`T-023` 的验收就是点「改」后表单必须回填），所以这条假设是被验证过的，不是想当然。
 *
 * 另一条边界：**一次只处理一件事**（`BR-3`）。点「改」或「删」先把下钻关掉，
 * 不让两个对话框叠着 —— 既避免点错层，也让"我改的是哪一条"始终只有一个答案。
 */
@Composable
fun LedgerCategoryEntriesDialog(
    categoryKey: String,
    categoryName: String,
    month: YearMonth,
    onDismiss: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    // ViewModel 在这里取，而不是做成参数：这个组合式函数是 `:app` 用的**对外入口**，
    // 而 `CategoryEntriesViewModel` 是 internal —— 公开函数不该暴露内部类型。
    val viewModel: CategoryEntriesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // REQ-011：改动走账本页那套流程，所以这里要的是**账本页那个** ViewModel。
    // 见上面 KDoc 里那条承重假设。
    val ledgerViewModel: LedgerViewModel = hiltViewModel()

    // 键变化就重新拉：同一个对话框实例可能被连续用来打开不同分类
    LaunchedEffect(categoryKey, month) { viewModel.load(categoryKey, categoryName, month, zone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            // AC-1（REQ-007）：标题必须说清"哪个分类、哪个月"
            Text(stringResource(R.string.ledger_category_entries_title, categoryName, month.format(MONTH_FORMAT)))
        },
        text = {
            CategoryEntriesContent(
                state = state,
                zone = zone,
                onEdit = { entry ->
                    onDismiss() // BR-3：先关下钻，再打开编辑表单
                    ledgerViewModel.onEditRequested(entry)
                },
                onDelete = { entry ->
                    onDismiss() // BR-3：先关下钻，再弹（账本页那套）二次确认
                    ledgerViewModel.onDeleteRequested(entry)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ledger_category_entries_close))
            }
        },
    )
}

/**
 * 对话框正文：加载中 / 读失败 / 空 / 条目清单，四态互斥。
 *
 * 抽出来是为了让 [LedgerCategoryEntriesDialog] 只回答"对话框由哪几块组成"
 * （`T-016` 在记账页用过同一手法：detekt 的 `LongMethod` 与"可读性"在这里指向同一个方向）。
 */
@Composable
private fun CategoryEntriesContent(
    state: CategoryEntriesUiState,
    zone: ZoneId,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
) {
    val textStyle = MaterialTheme.typography.bodyMedium

    when {
        state.isLoading -> Text(text = stringResource(R.string.ledger_category_entries_empty), style = textStyle)
        state.hasFailure -> Text(
            // 读失败与"这个月没有"必须分开说（REQ-006/AC-2 的同一原则）
            text = stringResource(R.string.ledger_category_entries_failed),
            color = MaterialTheme.colorScheme.error,
            style = textStyle,
        )
        state.entries.isEmpty() -> Text(
            text = stringResource(R.string.ledger_category_entries_empty),
            style = textStyle,
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.unreadableEntries > 0) {
                // REQ-006/AC-3：跳过了坏行就要说，不能让它看起来像"条目变少了"
                Text(
                    text = stringResource(R.string.ledger_unreadable_entries, state.unreadableEntries),
                    color = MaterialTheme.colorScheme.error,
                    style = textStyle,
                )
            }

            state.entries.forEach { entry ->
                CategoryEntryRow(entry = entry, zone = zone, onEdit = onEdit, onDelete = onDelete)
            }
        }
    }
}

/**
 * 一行：金额 + 发生日期 + 「改」「删」（`REQ-011/AC-1`）。
 *
 * 两个按钮的文案**复用**账本页那两条字符串（`ledger_edit` / `ledger_delete`）——
 * 同一个动作在两个地方叫两个名字，是"漂移"最开始的样子。
 */
@Composable
private fun CategoryEntryRow(
    entry: LedgerEntry,
    zone: ZoneId,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.occurredAt.atZone(zone).toLocalDate().format(DATE_FORMAT),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(text = entry.amount.toString(), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            TextButton(onClick = { onEdit(entry) }) {
                Text(stringResource(R.string.ledger_edit))
            }
            TextButton(onClick = { onDelete(entry) }) {
                Text(stringResource(R.string.ledger_delete))
            }
        }
    }
}
