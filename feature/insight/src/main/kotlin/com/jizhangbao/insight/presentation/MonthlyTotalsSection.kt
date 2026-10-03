package com.jizhangbao.insight.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.jizhangbao.core.domain.MonthlyTrend
import com.jizhangbao.core.ui.component.AmountText
import com.jizhangbao.core.ui.component.AmountTone
import com.jizhangbao.core.ui.component.SectionCard
import com.jizhangbao.core.ui.component.SectionTitle
import com.jizhangbao.core.ui.component.StatTile
import com.jizhangbao.core.ui.theme.jizhangbaoColors
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
 * 合计区：**月度概览卡** + 环比 + 趋势 + 构成。
 *
 * ## 视觉改动只做两件事（`T-036` / `ADR-0014`）
 *
 * 1. **主次**：以前三项（支出/收入/结余）是并排的三行等权文字，看不出"这个月花了多少"是主角。
 *    现在支出是**卡里的主金额**（大号 + 支出色），收入与结余退成两个小格。
 * 2. **色彩语义**：支出暖红、收入薄荷绿、结余青 —— 三个数字一眼分得开。
 *
 * ## 界面仍然不做任何计算
 *
 * 每一项都由模型给出（`MonthlyTotals` / `MonthlyComparison` / `MonthlyTrend`）。
 * 这里唯一的"判断"是 `point.month == state.month`（**当月那一行加重**）——
 * 那是比较两个已给的值，不是算术。
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthlyOverviewCard(
            state = state,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
        )

        // REQ-005：支出构成。这个月有活动时才显示 ——
        // 整月都没有记账时，下面的「这个月还没有记账」已经把话说清楚了，
        // 再叠一句"本月还没有支出"只是噪音
        if (state.totals != MonthlyTotals.ZERO && !state.isLoading) {
            // REQ-010：最近几个月的支出趋势。
            // 放在环比后面、占比前面：它和环比都是"时间轴"上的事，而占比是"构成"。
            // 与占比共用同一个门槛：空月不谈趋势（和 AC-4 同一个原则）
            if (!state.trend.isEmpty) {
                MonthlyTrendCard(trend = state.trend, currentMonth = state.month)
            }

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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.jizhangbaoColors.muted,
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
 * 月度概览卡：月份 + 翻月 + **支出（主金额）** + 环比 + 收入/结余两个小格。
 *
 * `highlight = true` → 描边用强调色：这一屏上它是主角（`ADR-0014` 决策 3）。
 */
@Composable
private fun MonthlyOverviewCard(
    state: MonthlyTotalsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    SectionCard(highlight = true, modifier = Modifier.fillMaxWidth()) {
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

        Spacer(modifier = Modifier.height(10.dp))

        SectionTitle(stringResource(R.string.insight_expense_total))
        AmountText(
            text = state.totals.expense.toString(),
            tone = AmountTone.EXPENSE,
            emphasis = true,
            align = androidx.compose.ui.text.style.TextAlign.Start,
        )

        // REQ-009：与上月的支出环比（模型算的差额，界面只负责说人话）
        state.comparison?.let { comparison ->
            MonthlyComparisonLine(comparison)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StatTile(
                label = stringResource(R.string.insight_income_total),
                value = state.totals.income.toString(),
                valueTone = AmountTone.INCOME,
            )
            StatTile(
                label = stringResource(R.string.insight_net_total),
                // 负号由 SignedMoney.toString() 负责 —— 这是唯一一处"格式即语义"的地方
                value = state.totals.net.toString(),
                valueTone = AmountTone.ACCENT,
            )
        }
    }
}

/**
 * 最近几个月的支出（`REQ-010`）。
 *
 * ## 为什么还是文字，只是分了主次
 *
 * `Q-020` 已由 `ADR-0014` 定案，但**比例条的比例必须由模型给**（本文件的铁律：界面不做计算）。
 * `MonthlyTrend` 目前只给"每个月多少"，没给"相对最大值是多少" ——
 * 在界面里现算就是第二处口径，所以这一轮**不加条**：当月那一行用强调色加重即可。
 * 真要条，就给 `MonthlyTrend` 加一个模型层的比例（另一张卡）。
 */
@Composable
private fun MonthlyTrendCard(trend: MonthlyTrend, currentMonth: YearMonth) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.insight_trend_title, trend.points.size))
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            trend.points.forEach { point ->
                val isCurrent = point.month == currentMonth
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(
                            R.string.insight_month_format,
                            point.month.year,
                            point.month.monthValue,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.jizhangbaoColors.muted
                        },
                    )
                    AmountText(
                        text = point.totals.expense.toString(),
                        tone = if (isCurrent) AmountTone.ACCENT else AmountTone.MUTED,
                    )
                }
            }
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

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.jizhangbaoColors.muted,
    )
}
