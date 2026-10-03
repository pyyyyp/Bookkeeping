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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jizhangbao.ledger.R
import com.jizhangbao.ledger.domain.model.UnreadableRow

/**
 * 坏行清单（`REQ-008`）：**看得到 + 删得掉**。
 *
 * ## 为什么这个清单必须存在
 *
 * `REQ-006` 只说了"有 N 条读不出来"，用户看得到问题却**无从处理**：
 * 那条钱一直算在合计里，而他在列表里找不到它。这个对话框就是那个出口。
 *
 * ## 为什么不做"编辑"
 *
 * 因为它**读不出来** —— 域的校验拒绝了这一行，所以根本没有可编辑的字段
 * （`BR-1`）。这不是省事，是不可能。
 *
 * ## 为什么不做"自动清理"
 *
 * 见 `BR-3`：**悄悄删用户的钱是这类功能里最不能犯的错**。
 * 所以这里每一步都要用户明确点下去 —— 包括二次确认。
 */
@Composable
internal fun UnreadableRowsDialog(
    rows: List<UnreadableRow>,
    onDiscard: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 二次确认住在这里（局部状态）：它是"这一次交互"的中间态，用户关掉就该忘掉。
    // 对比 `pendingDelete` —— 那个是**业务事实**（有一条正等着被删），所以它住 ViewModel。
    var pending by remember { mutableStateOf<UnreadableRow?>(null) }
    val target = pending

    if (target == null) {
        UnreadableListDialog(rows = rows, onDiscardRequested = { pending = it }, onDismiss = onDismiss)
    } else {
        UnreadableConfirmDialog(
            row = target,
            onConfirm = {
                pending = null
                onDiscard(target.rawId)
            },
            onCancel = { pending = null },
        )
    }
}

@Composable
private fun UnreadableListDialog(
    rows: List<UnreadableRow>,
    onDiscardRequested: (UnreadableRow) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ledger_unreadable_title, rows.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.ledger_unreadable_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                rows.forEach { row -> UnreadableRowItem(row = row, onDiscard = { onDiscardRequested(row) }) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ledger_category_entries_close))
            }
        },
    )
}

/**
 * 一行坏数据：**原始标识 + 原因 + 删除按钮**。
 *
 * 原始标识必须显示出来：它是唯一能指回这一行的东西（别的字段都不可信），
 * 也是用户拿去和 `sqlite3` 对照的依据。原因**照实显示**（`BR-4`）——
 * 说"标识不是合法 UUID"比说"数据损坏"有用得多。
 */
@Composable
private fun UnreadableRowItem(row: UnreadableRow, onDiscard: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.rawId, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = row.reason.ifBlank { stringResource(R.string.ledger_unreadable_no_reason) },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onDiscard) {
            Text(stringResource(R.string.ledger_unreadable_discard))
        }
    }
}

@Composable
private fun UnreadableConfirmDialog(row: UnreadableRow, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.ledger_unreadable_confirm_title)) },
        // 把原始标识再写一遍：用户点的可能不是他想删的那一条，这一步是最后的机会
        text = { Text(stringResource(R.string.ledger_unreadable_confirm_body, row.rawId)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.ledger_unreadable_confirm_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.ledger_unreadable_confirm_cancel))
            }
        },
    )
}
