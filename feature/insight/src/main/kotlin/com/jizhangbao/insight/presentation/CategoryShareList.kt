package com.jizhangbao.insight.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jizhangbao.core.domain.CategoryBreakdown
import com.jizhangbao.insight.R

/**
 * 支出构成：一行一个分类（名字 / 金额 / 占比），`REQ-005`。
 *
 * ## 三个数都直接来自模型
 *
 * 金额用 `Money.toString()`、占比用 `Percentage.toString()`（`33.3%`）。
 * 界面**不做任何计算**，也不重新排序 —— 排序与四舍五入是 `CategoryBreakdown` 的规则
 * （`BR-3` `BR-4`），在这里再算一遍就会出现"两个地方各有一套口径"。
 *
 * ## 不画饼图
 *
 * v1 用文字 + 占比。视觉设计未定（`Q-020`），而饼图一旦画上去就得处理
 * 颜色、图例、极小扇区、无障碍朗读 —— 那是另一个需求的工作量。
 */
@Composable
internal fun CategoryShareList(breakdown: CategoryBreakdown) {
    if (breakdown.isEmpty) {
        // AC-5：没有支出时说清楚，而不是显示一排 0.0%
        Text(
            text = stringResource(R.string.insight_share_empty),
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.insight_share_title),
            style = MaterialTheme.typography.titleSmall,
        )
        breakdown.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    // 分类名占满剩余宽度：名字长短不一，不加 weight 会让右边的数字参差不齐
                    text = row.categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(text = row.amount.toString(), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = breakdown.shareOf(row).toString(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
