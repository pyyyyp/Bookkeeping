package com.jizhangbao.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 排印（`ADR-0014` 决策 4）。
 *
 * ## 两条让它"像仪表盘"的规则
 *
 * 1. **金额用等宽数字**（`fontFeatureSettings = "tnum"`）：列表里数字**上下对齐**。
 *    这是"数据感"里最廉价也最有效的一招 —— 一眼扫下去不会参差。
 * 2. **标签小号 + 宽字距**（11–12sp、`letterSpacing` 1sp）：仪表盘上的刻度标签就是这个样子。
 *
 * ⚠️ **不引入字体文件**：那会增加体积，而系统字体 + 字距已经够用（决策 4）。
 */
private const val HERO_SIZE = 34f
private const val HERO_TRACKING = -0.6f
private const val TITLE_SIZE = 17f
private const val BODY_SIZE = 14f
private const val TILE_VALUE_SIZE = 20f
private const val LABEL_SIZE = 11f
private const val LABEL_TRACKING = 1.2f

/** 数字专用：等宽，让列表里的金额对齐。 */
private const val TABULAR = "tnum"

internal val TechTypography = Typography(
    // 主金额（月度支出/结余那种"主角"）
    displaySmall = TextStyle(
        fontSize = HERO_SIZE.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = HERO_TRACKING.sp,
        fontFeatureSettings = TABULAR,
    ),
    // 区块标题
    titleMedium = TextStyle(
        fontSize = TITLE_SIZE.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    // 次级标题（卡片里的小标题）
    titleSmall = TextStyle(
        fontSize = BODY_SIZE.sp,
        fontWeight = FontWeight.Medium,
    ),
    // 正文与列表项
    bodyLarge = TextStyle(fontSize = BODY_SIZE.sp, fontFeatureSettings = TABULAR),
    bodyMedium = TextStyle(fontSize = BODY_SIZE.sp, fontFeatureSettings = TABULAR),
    bodySmall = TextStyle(fontSize = LABEL_SIZE.sp, fontFeatureSettings = TABULAR),
    // 概览块里的数字
    headlineSmall = TextStyle(
        fontSize = TILE_VALUE_SIZE.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = TABULAR,
    ),
    // 刻度标签：小号 + 宽字距
    labelSmall = TextStyle(
        fontSize = LABEL_SIZE.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = LABEL_TRACKING.sp,
    ),
    labelMedium = TextStyle(
        fontSize = LABEL_SIZE.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = LABEL_TRACKING.sp,
    ),
)
