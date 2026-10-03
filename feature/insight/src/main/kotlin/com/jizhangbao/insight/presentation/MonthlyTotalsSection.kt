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
import com.jizhangbao.core.domain.CategoryAmount
import com.jizhangbao.core.domain.MonthlyComparison
import com.jizhangbao.core.domain.MonthlyTotals
import com.jizhangbao.insight.R
import java.time.YearMonth

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
fun MonthlyTotalsRoute(
    refreshSignal: Int = 0,
    /**
     * 用户点了占比里的某一行（`REQ-007`，下钻）。
     *
     * 回传的是**整行 + 当前月份**：月份必须由这里给（只有它知道用户在看哪个月），
     * 而那个不透明标识 Insight **不解释**（`REQ-007/BR-4`）。
     */
    onCategorySelected: (CategoryAmount, YearMonth) -> Unit = { _, _ -> },
) {
    MonthlyTotalsRoute(
        viewModel = hiltViewModel(),
        refreshSignal = refreshSignal,
        onCategorySelected = onCategorySelected,
    )
}

@Composable
internal fun MonthlyTotalsRoute(
    viewModel: MonthlyTotalsViewModel,
    refreshSignal: Int,
    onCategorySelected: (CategoryAmount, YearMonth) -> Unit = { _, _ -> },
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
        onCategorySelected = onCategorySelected,
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
    onCategorySelected: (CategoryAmount, YearMonth) -> Unit = { _, _ -> },
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
            // REQ-009：与上月的支出环比。
            // 与占比共用一个门槛（AC-4）：本月没有记账时两个都不显示 ——
            // 没有数据的月份谈不上"与上月持平"，那是噪音
            state.comparison?.let { MonthlyComparisonLine(it) }

            CategoryShareList(
                breakdown = state.breakdown,
                // 月份由状态给：它才是"用户现在看的是哪个月"的唯一来源
                onCategorySelected = { row -> onCategorySelected(row, state.month) },
            )
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

/**
 * 「比上月多花 / 少花 ¥X」（`REQ-009`）。
 *
 * 三种说法的选择在**这里**，减法在模型里（`BR-3`）：界面只回答
 * "怎么把这个差额说成人话"，不重新算一遍 —— 在界面里做减法就是第二处口径。
 *
 * 少花时用 `magnitude` 而不是原值：句子里已经有"少"了，
 * 再显示负号会读成"少了负的"。
 */
@Composable
private fun MonthlyComparisonLine(comparison: MonthlyComparison) {
    val delta = comparison.expenseDelta
    val text = when {
        comparison.isUnchanged -> stringResource(R.string.insight_comparison_same)
        delta.isNegative -> stringResource(R.string.insight_comparison_less, delta.magnitude.toString())
        else -> stringResource(R.string.insight_comparison_more, delta.toString())
    }

    Text(text = text, style = MaterialTheme.typography.bodySmall)
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
