package com.jizhangbao.ledger.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jizhangbao.ledger.R
import com.jizhangbao.ledger.domain.model.CategoryId
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
    /**
     * 把分类标识翻成显示名。
     *
     * 由状态提供（而不是这里查内置清单）：`REQ-004` 之后分类可能是用户自建的，
     * 而且**已归档的也要能显示**（否则历史条目会显示成 id）。
     */
    nameOf: (CategoryId) -> String,
    /**
     * 施加在列表容器上的修饰符。
     *
     * ⚠️ 这里**刻意不用 `LazyColumn`**：本区块的上层是一个可滚动的 `Column`
     * （整页要能滚，否则表单占满一屏后列表行够不到），而 `LazyColumn` 嵌在
     * 纵向可滚动父容器里会因为**无限高度约束直接崩**。
     * 代价是失去懒加载：条目上限由 `LoadRecentEntriesUseCase.DEFAULT_LIMIT`（200）兜住，
     * 在这个量级上可接受。真正需要分页时，应当把**整页**做成一个 LazyColumn
     * （表头/表单/列表都作为它的 item），而不是把 LazyColumn 嵌回来。
     */
    modifier: Modifier = Modifier,
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
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.forEach { entry ->
                EntryRow(
                    entry = entry,
                    zone = zone,
                    categoryName = nameOf(entry.categoryId),
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

