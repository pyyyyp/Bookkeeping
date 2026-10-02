package com.jizhangbao.ledger.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 */
@Composable
internal fun EntriesSection(entries: List<LedgerEntry>, zone: ZoneId) {
    Text(
        text = stringResource(R.string.ledger_list_title),
        style = MaterialTheme.typography.titleMedium,
    )

    if (entries.isEmpty()) {
        Text(
            text = stringResource(R.string.ledger_list_empty),
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = entries, key = { it.id.value }) { entry ->
                EntryRow(entry = entry, zone = zone)
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LedgerEntry, zone: ZoneId) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(entry.direction.labelRes()) + "  " + categoryDisplayName(entry),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = entry.amount.toString() + "   " +
                entry.occurredAt.atZone(zone).toLocalDate().format(DATE_FORMAT),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 分类显示名来自领域层的预置清单（它是业务内容，不是界面文案）。 */
private fun categoryDisplayName(entry: LedgerEntry): String =
    CategoryCatalog.PRESET.byId(entry.categoryId)?.displayName ?: entry.categoryId.value
