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
 * ⚠️ **只读**（`BR-3`）：这里没有编辑/删除。改动路径只能有一条（记账页那条），
 * 两条路径各写一套校验迟早漂移（`REQ-003` 的教训）。
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

    // 键变化就重新拉：同一个对话框实例可能被连续用来打开不同分类
    LaunchedEffect(categoryKey, month) { viewModel.load(categoryKey, categoryName, month, zone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            // AC-1：标题必须说清"哪个分类、哪个月"
            Text(stringResource(R.string.ledger_category_entries_title, categoryName, month.format(MONTH_FORMAT)))
        },
        text = { CategoryEntriesContent(state = state, zone = zone) },
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
private fun CategoryEntriesContent(state: CategoryEntriesUiState, zone: ZoneId) {
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
        else -> CategoryEntriesList(state = state, zone = zone)
    }
}

@Composable
private fun CategoryEntriesList(state: CategoryEntriesUiState, zone: ZoneId) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.unreadableEntries > 0) {
            // REQ-006/AC-3：跳过了坏行就要说，不能让它看起来像"条目变少了"
            Text(
                text = stringResource(R.string.ledger_unreadable_entries, state.unreadableEntries),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        state.entries.forEach { entry -> CategoryEntryRow(entry = entry, zone = zone) }
    }
}

/** 只读的一行：金额 + 发生日期。**没有**编辑/删除（`BR-3`）。 */
@Composable
private fun CategoryEntryRow(entry: LedgerEntry, zone: ZoneId) {
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
    }
}
