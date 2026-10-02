package com.jizhangbao.insight.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhangbao.core.domain.MonthlyTotals
import com.jizhangbao.insight.R

/**
 * 合计区的对外入口。
 *
 * @param refreshSignal 由**组合根**（`:app`）在账本数据变动后递增。
 *
 * ⚠️ 为什么是"外部通知"而不是自己订阅：Insight **不认识** Ledger（R2 禁止 feature 互相依赖），
 * 所以它无法观察账本的写入。`:app` 同时看得见两者，由它把「数据变了」这件事传进来
 * （见 `T-009` 卡与 `ADR-0008`）。这不是偷懒，是那条依赖规则的直接后果。
 */
@Composable
fun MonthlyTotalsRoute(refreshSignal: Int = 0) {
    MonthlyTotalsRoute(viewModel = hiltViewModel(), refreshSignal = refreshSignal)
}

@Composable
internal fun MonthlyTotalsRoute(
    viewModel: MonthlyTotalsViewModel,
    refreshSignal: Int,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 初始为 0 时不重复加载（ViewModel 的 init 已经加载过一次）
    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0) viewModel.refresh()
    }

    MonthlyTotalsSection(
        state = state,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
    )
}

/**
 * 合计区：月份 + 翻月 + 支出/收入/结余三项。
 *
 * 无状态（state + 回调），所以能直接预览与测试，不需要 ViewModel。
 */
@Composable
internal fun MonthlyTotalsSection(
    state: MonthlyTotalsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.insight_month_format,
                    state.month.year,
                    state.month.monthValue,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPreviousMonth) {
                    Text(stringResource(R.string.insight_previous_month))
                }
                TextButton(
                    onClick = onNextMonth,
                    // BR-7：当月时禁用，而不是"点了没反应"
                    enabled = state.canGoToNextMonth,
                ) {
                    Text(stringResource(R.string.insight_next_month))
                }
            }
        }

        TotalsRow(state.totals)

        // REQ-005：支出构成。这个月有活动时才显示 ——
        // 整月都没有记账时，下面的「这个月还没有记账」已经把话说清楚了，
        // 再叠一句"本月还没有支出"只是噪音
        if (state.totals != MonthlyTotals.ZERO && !state.isLoading) {
            CategoryShareList(breakdown = state.breakdown)
        }

        if (state.totals == MonthlyTotals.ZERO && !state.isLoading) {
            // AC-3：零要么是"这个月还没记账"，要么是"读不出来"——两者必须分开说
            Text(
                text = stringResource(R.string.insight_empty_month),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state.hasFailure) {
            Text(
                text = stringResource(R.string.insight_failure),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TotalsRow(totals: MonthlyTotals) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TotalItem(stringResource(R.string.insight_expense_total), totals.expense.toString())
        TotalItem(stringResource(R.string.insight_income_total), totals.income.toString())
        // 负号由 SignedMoney.toString() 负责 —— 这是唯一一处"格式即语义"的地方
        TotalItem(stringResource(R.string.insight_net_total), totals.net.toString())
    }
}

@Composable
private fun TotalItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
