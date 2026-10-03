package com.jizhangbao.insight.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jizhangbao.core.domain.CategoryAmount
import com.jizhangbao.core.domain.CategoryBreakdown
import com.jizhangbao.core.ui.component.BarRow
import com.jizhangbao.core.ui.component.SectionCard
import com.jizhangbao.core.ui.component.SectionTitle
import com.jizhangbao.core.ui.theme.jizhangbaoColors
import com.jizhangbao.insight.R

/** `Percentage` 的 `tenths`（千分点）→ 0f..1f。见下面的说明。 */
private const val TENTHS_PER_WHOLE = 1000f

/**
 * 支出构成：一行一个分类（名字 / 金额 / 占比 + 一条比例条），`REQ-005`。
 *
 * ## 三个数都直接来自模型
 *
 * 金额用 `Money.toString()`、占比用 `Percentage.toString()`（`33.3%`）。
 * 界面**不做任何计算**，也不重新排序 —— 排序与四舍五入是 `CategoryBreakdown` 的规则
 * （`BR-3` `BR-4`），在这里再算一遍就会出现"两个地方各有一套口径"。
 *
 * ## 比例条的宽度是不是"界面在做计算"（`T-037`）
 *
 * **不是**：占比**已经由模型算好**（`Percentage.tenths`，千分点），
 * 这里只做**单位换算**（千分点 → 0..1）好让条子知道画多宽。
 * 没有四舍五入、没有重新求占比、也没有排序。
 *
 * ⚠️ 对比：趋势那块（`MonthlyTotalsSection`）**没有**条，因为模型**压根没给比例** ——
 * 那才叫界面在做计算，所以那一处宁可不做。
 *
 * ## 仍然不画饼图
 *
 * `Q-020` 已由 `ADR-0014` 定案，但饼图要处理颜色、图例、极小扇区、无障碍朗读 ——
 * 那是另一个需求的工作量。一条比例条已经能表达"谁占大头"。
 */
@Composable
internal fun CategoryShareList(
    breakdown: CategoryBreakdown,
    /**
     * 用户点了某一行（`REQ-007`，下钻）。
     *
     * 注意传出去的是**整行**（含那个不透明标识），Insight **不解释**它 ——
     * 它只负责说"用户点了这一行"（`REQ-007/BR-4`）。谁认识分类、谁去取条目，
     * 由组合根与 Ledger 决定。
     */
    onCategorySelected: (CategoryAmount) -> Unit = {},
) {
    if (breakdown.isEmpty) {
        // AC-5：没有支出时说清楚，而不是显示一排 0.0%
        Text(
            text = stringResource(R.string.insight_share_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.jizhangbaoColors.muted,
        )
        return
    }

    SectionCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.insight_share_title))
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            breakdown.rows.forEach { row ->
                val share = breakdown.shareOf(row)
                BarRow(
                    label = row.categoryName,
                    value = row.amount.toString(),
                    ratio = share.tenths / TENTHS_PER_WHOLE,
                    caption = share.toString(),
                    // 整行可点：占比里的数字是聚合值，用户的第一反应就是"这是哪几笔"（REQ-007/AC-1）
                    modifier = Modifier.clickable { onCategorySelected(row) },
                )
            }
        }
    }
}
