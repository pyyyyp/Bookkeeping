package com.jizhangbao.ledger.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jizhangbao.ledger.R
import com.jizhangbao.ledger.domain.model.LedgerEntry
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * 列表的**头部**：标题、读不出来的告知、删除结果、空状态（`REQ-001/AC-7` `REQ-006/AC-3`）。
 *
 * ## 为什么它和"行"分开了（`T-016`）
 *
 * 整页现在是一个 `LazyColumn`，头部与每一行都是它的 item —— 分开之后行才能被
 * `items()` **按需组合**（此前列表是一个普通 `Column`，200 条会一次性全部组合）。
 *
 * 顺序由 SQL 保证（`ORDER BY occurredAtEpochMilli DESC`）——
 * 界面**不重新排序**，否则「显示的顺序」与「数据的顺序」会变成两套规则。
 */
@Composable
internal fun EntriesSectionHeader(
    entriesCount: Int,
    showDeletedNotice: Boolean,
    /** 读不出来的条数（`REQ-006/AC-3`）：> 0 时必须说出来，不能让它看起来像"账目变少了"。 */
    unreadableEntries: Int,
) {
    Text(
        text = stringResource(R.string.ledger_list_title),
        style = MaterialTheme.typography.titleMedium,
    )

    if (unreadableEntries > 0) {
        // 说清楚三件事：有几条、为什么、以及它们不会让合计变少
        Text(
            text = stringResource(R.string.ledger_unreadable_entries, unreadableEntries),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (showDeletedNotice) {
        // 删除的结果要说出来：条目从列表消失是「看得见的」，但「是不是真的删掉了」
        // 需要一个明确的确认（尤其当列表本来就有很多条时）
        Text(
            text = stringResource(R.string.ledger_entry_deleted),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (entriesCount == 0) {
        Text(
            text = stringResource(R.string.ledger_list_empty),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * 列表里的一行（`REQ-001/AC-7`，编辑入口见 `REQ-003`，删除入口见 `AC-8` `AC-9`）。
 *
 * 两个回调都**只表达意图**：编辑只是把值填进表单（数据一个字不改），
 * 删除还要等用户在确认对话框里点「删除」。
 *
 * `internal` 而不是 `private`：它现在由外层 `LazyColumn` 的 `items()` 直接使用（`T-016`）。
 */
@Composable
internal fun EntryRow(
    entry: LedgerEntry,
    zone: ZoneId,
    categoryName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(entry.direction.labelRes()) + "  " + categoryName,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.amount.toString() + "   " +
                    entry.occurredAt.atZone(zone).toLocalDate().format(DATE_FORMAT),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onEdit) {
                Text(stringResource(R.string.ledger_edit))
            }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.ledger_delete))
            }
        }
    }
}
