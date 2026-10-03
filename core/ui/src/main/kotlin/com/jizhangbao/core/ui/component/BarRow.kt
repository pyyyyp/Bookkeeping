package com.jizhangbao.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jizhangbao.core.ui.theme.jizhangbaoColors

/**
 * 概览小格：**小号标签 + 数字**（`ADR-0014` 决策 4 的"仪表盘感"）。
 *
 * [valueTone] 决定数字颜色（支出暖红 / 收入薄荷绿 / 结余青）。
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    valueTone: AmountTone,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(label)
        AmountText(text = value, tone = valueTone, align = androidx.compose.ui.text.style.TextAlign.Start)
    }
}

/**
 * 比例条：一行 = 名称 + 金额 + 占比 + 一条**横向进度**。
 *
 * 这是"不引图表库也能表达构成"的做法（`ADR-0014` 决策 5）：纯 Compose 画圆角矩形，
 * 零依赖、可控、比引库更省体积。
 *
 * @param ratio 0f..1f；超出会被夹住 —— 脏数据不该把条画出屏幕。
 * @param caption 右上角那句话（默认显示百分比）。
 */
@Composable
fun BarRow(
    label: String,
    value: String,
    ratio: Float,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val clamped = ratio.coerceIn(0f, 1f)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AmountText(text = value, tone = AmountTone.NEUTRAL)
                SectionTitle(text = caption ?: percentLabel(clamped))
            }
        }
        ProgressBar(ratio = clamped, color = MaterialTheme.jizhangbaoColors.accent)
    }
}

/** 一条圆角进度条：底槽 + 前景（前景按比例占宽）。 */
@Composable
private fun ProgressBar(ratio: Float, color: Color) {
    val shape = RoundedCornerShape(percent = PILL_CORNER_PERCENT)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio)
                .fillMaxHeight()
                .clip(shape)
                .background(color),
        )
    }
}

private fun percentLabel(ratio: Float): String = "${(ratio * PERCENT_SCALE).toInt()}%"

/** 0f..1f → 百分数。命名而不是写字面量：detekt 的 `MagicNumber` 拦的是**使用处**。 */
private const val PERCENT_SCALE = 100

/** 胶囊圆角（50% = 两端半圆）。 */
private const val PILL_CORNER_PERCENT = 50
