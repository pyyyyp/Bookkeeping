package com.jizhangbao.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.jizhangbao.core.ui.theme.jizhangbaoColors

/**
 * 金额文本：**等宽数字**（列表里上下对齐）+ 按语义上色。
 *
 * [emphasis] 用大字号（主金额那种"主角"），否则跟正文同级。
 */
@Composable
fun AmountText(
    text: String,
    tone: AmountTone = AmountTone.NEUTRAL,
    emphasis: Boolean = false,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.End,
) {
    val colors = MaterialTheme.jizhangbaoColors
    Text(
        text = text,
        modifier = modifier,
        style = if (emphasis) {
            MaterialTheme.typography.displaySmall
        } else {
            MaterialTheme.typography.bodyLarge
        },
        color = when (tone) {
            AmountTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
            AmountTone.EXPENSE -> colors.expense
            AmountTone.INCOME -> colors.income
            AmountTone.ACCENT -> colors.accent
            AmountTone.MUTED -> colors.muted
        },
        textAlign = align,
    )
}
