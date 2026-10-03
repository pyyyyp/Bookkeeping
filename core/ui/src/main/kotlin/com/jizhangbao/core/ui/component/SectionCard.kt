package com.jizhangbao.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jizhangbao.core.ui.theme.jizhangbaoColors

/**
 * 一张卡片（`ADR-0014` 决策 3）。
 *
 * 层级 = **底色 + 1dp 描边**，不用阴影（深色背景里没有可压暗的方向，阴影几乎不可见）。
 * [highlight] 为真时描边用强调色 —— 给"这一个是最重要的"那种卡片用（例如月度结余）。
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.jizhangbaoColors
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (highlight) colors.accent.copy(alpha = 0.55f) else colors.cardBorder,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/** 卡片里的小标题（小号 + 宽字距，见 `ADR-0014` 决策 4）。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.jizhangbaoColors.muted,
    )
}
