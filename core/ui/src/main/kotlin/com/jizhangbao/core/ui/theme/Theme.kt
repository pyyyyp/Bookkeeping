package com.jizhangbao.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 记账宝的应用主题。
 *
 * ⚠️ **配色与排版目前是 Material 3 的默认值，不是设计结果。**
 * 本项目的视觉设计尚未确定（没有设计稿），因此这里刻意不编造品牌色板——
 * 编一套看起来专业但无人拍板的颜色，会让后续真正的设计决策更难落地。
 * 待设计确定后，只需替换下面两个 ColorScheme 与 typography 参数，
 * 调用点（[JizhangbaoTheme]）不变。
 *
 * 见 `docs/20-domain/open-questions.md` 的 Q-020。
 */
@Composable
fun JizhangbaoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

// TODO(Q-020): 待设计稿确定后替换为品牌色板；当前为 Material 3 默认值。
private val LightColors = lightColorScheme()

// TODO(Q-020): 同上。
private val DarkColors = darkColorScheme()
