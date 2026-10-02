package com.jizhangbao.ledger.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jizhangbao.ledger.R
import com.jizhangbao.ledger.domain.model.CategoryCatalog
import com.jizhangbao.ledger.domain.model.LedgerEntry
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * 最近账目列表（`REQ-001/AC-7`）。
 *
 * 顺序由 SQL 保证（`ORDER BY occurredAtEpochMilli DESC`）——
 * 界面**不重新排序**，否则「显示的顺序」与「数据的顺序」会变成两套规则。
 *
 * 每行右侧有删除入口（`AC-8` `AC-9`）。它调用的 [onDelete] **只表达意图**：
 * 真正的删除要等用户在确认对话框里点了「删除」。
 */
@Composable
internal fun EntriesSection(
    entries: List<LedgerEntry>,
    zone: ZoneId,
    showDeletedNotice: Boolean,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
) {
    Text(
        text = stringResource(R.string.ledger_list_title),
        style = MaterialTheme.typography.titleMedium,
    )

    if (showDeletedNotice) {
        // 删除的结果要说出来：条目从列表消失是「看得见的」，但「是不是真的删掉了」
        // 需要一个明确的确认（尤其当列表本来就有很多条时）
        Text(
            text = stringResource(R.string.ledger_entry_deleted),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (entries.isEmpty()) {
        Text(
            text = stringResource(R.string.ledger_list_empty),
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = entries, key = { it.id.value }) { entry ->
                EntryRow(
                    entry = entry,
                    zone = zone,
                    onEdit = { onEdit(entry) },
                    onDelete = { onDelete(entry) },
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: LedgerEntry,
    zone: ZoneId,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(entry.direction.labelRes()) + "  " + categoryDisplayName(entry),
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

/** 分类显示名来自领域层的预置清单（它是业务内容，不是界面文案）。 */
private fun categoryDisplayName(entry: LedgerEntry): String =
    CategoryCatalog.PRESET.byId(entry.categoryId)?.displayName ?: entry.categoryId.value
