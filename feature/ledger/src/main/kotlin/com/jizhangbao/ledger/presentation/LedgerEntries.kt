package com.jizhangbao.ledger.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.ui.component.AmountText
import com.jizhangbao.core.ui.component.AmountTone
import com.jizhangbao.core.ui.component.SectionTitle
import com.jizhangbao.core.ui.theme.jizhangbaoColors
import com.jizhangbao.ledger.R
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.UnreadableRow
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

/** 方向在界面上的语气（`ADR-0014` 决策 2）：支出暖红、收入薄荷绿。 */
private fun EntryDirection.tone(): AmountTone = when (this) {
    EntryDirection.Expense -> AmountTone.EXPENSE
    EntryDirection.Income -> AmountTone.INCOME
}

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
    /**
     * 读不出来的行（`REQ-006/AC-3` `REQ-008`）：非空时必须说出来，
     * 不能让它看起来像"账目变少了"。
     */
    unreadableRows: List<UnreadableRow>,
    /** 用户点「处理」（`REQ-008/AC-1`）：打开坏行清单。 */
    onUnreadableClick: () -> Unit = {},
) {
    Text(
        text = stringResource(R.string.ledger_list_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )

    if (unreadableRows.isNotEmpty()) {
        // 说清楚两件事：有几条、以及它们不会让合计变少。
        // 第三件事（"你能拿它怎么办"）由下面的按钮回答 —— REQ-008 之前这里是无解的。
        Text(
            text = stringResource(R.string.ledger_unreadable_entries, unreadableRows.size),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onUnreadableClick) {
            Text(stringResource(R.string.ledger_unreadable_manage))
        }
    }

    if (showDeletedNotice) {
        // 删除的结果要说出来：条目从列表消失是「看得见的」，但「是不是真的删掉了」
        // 需要一个明确的确认（尤其当列表本来就有很多条时）
        Text(
            text = stringResource(R.string.ledger_entry_deleted),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.jizhangbaoColors.muted,
        )
    }

    if (entriesCount == 0) {
        Text(
            text = stringResource(R.string.ledger_list_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.jizhangbaoColors.muted,
        )
    }
}

/**
 * 列表里的一行（`REQ-001/AC-7`，编辑入口见 `REQ-003`，删除入口见 `AC-8` `AC-9`）。
 *
 * 两个回调都**只表达意图**：编辑只是把值填进表单（数据一个字不改），
 * 删除还要等用户在确认对话框里点「删除」。
 *
 * ## 视觉（`T-036` / `ADR-0014`）
 *
 * 以前是"分类 + 金额 + 日期 + 两个文字按钮"挤成一行、四种元素等权。
 * 现在分三层：**分类名**（主体，大字）→ **方向 · 日期**（次要，小号 muted）→ **金额**（按方向上色）。
 * 编辑/删除退成图标按钮 —— 它们是"偶尔用一次"的动作，不该和金额抢注意力。
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SectionTitle(
                text = stringResource(entry.direction.labelRes()) + " · " +
                    entry.occurredAt.atZone(zone).toLocalDate().format(DATE_FORMAT),
            )
        }

        // 金额按方向上色：一眼分清"花了"与"挣了"
        AmountText(text = entry.amount.toString(), tone = entry.direction.tone())

        // ⚠️ 用文字按钮而不是图标：`androidx.compose.material.icons` **不在本模块的依赖里**
        // （实测 unresolved reference），而"不引新依赖"是硬规矩 —— 为两个图标加一个包不值。
        // 见 ADR-0014 决策 5 的更正说明。
        TextButton(onClick = onEdit) {
            Text(
                text = stringResource(R.string.ledger_edit),
                color = MaterialTheme.jizhangbaoColors.accent,
            )
        }
        TextButton(onClick = onDelete) {
            Text(
                text = stringResource(R.string.ledger_delete),
                color = MaterialTheme.jizhangbaoColors.muted,
            )
        }
    }
}
