package com.jizhangbao.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/**
 * 记账宝的应用主题。
 *
 * ## 视觉方向（`ADR-0014`）
 *
 * **深色为主**：深蓝黑打底、青/紫做高光、数字前置。用户的诉求是"更有科技感一些"，
 * 而记账 App 的核心就是数字 —— 深色让数字"发光"。
 *
 * ⚠️ **默认深色**（`darkTheme = true`），但**保留浅色方案**（`PaperLightColors`）。
 * 被否的方案是"跟随系统"：在浅色系统上就得不到那个效果，用户的诉求落不了地。
 *
 * ## 换色板只动两个文件
 *
 * 颜色在 `Color.kt`、字号在 `Type.kt` —— **界面结构一行都不用改**。
 * 这是"可推翻"的具体含义：想换方向就改那两个文件。
 */
@Composable
fun JizhangbaoTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val semantics = if (darkTheme) TechDarkSemantics else PaperLightSemantics
    CompositionLocalProvider(LocalJizhangbaoColors provides semantics) {
        MaterialTheme(
            colorScheme = if (darkTheme) TechDarkColors else PaperLightColors,
            typography = TechTypography,
            shapes = TechShapes,
            content = content,
        )
    }
}

/**
 * 圆角（`ADR-0014`）：卡片 20dp、输入 14dp。
 *
 * 比 M3 默认更圆一点 —— 深色下圆角与描边一起构成"面板"的感觉。
 */
internal val TechShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 让调用点少写一次 `MaterialTheme.colorScheme`。 */
val MaterialTheme.jizhangbaoColors: JizhangbaoColors
    @Composable get() = LocalJizhangbaoColors.current
