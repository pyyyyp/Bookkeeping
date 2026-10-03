package com.jizhangbao.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 色板（`ADR-0014`）。
 *
 * ## 为什么是深蓝黑，不是纯黑
 *
 * 纯黑（`#000000`）配高饱和高光会"糊"：边缘没有过渡，看起来像劣质 OLED 演示。
 * 深蓝黑留出一点空间感，也让青色高光**干净**（这正是"科技感"的来源）。
 *
 * ## 为什么单独一层语义色（不用 M3 现成的 role）
 *
 * Material 3 的 `ColorScheme` 里**没有**"支出/收入"这种业务含义的位置。
 * 把它们硬塞进 `error`/`tertiary`，会让"出错了"和"花了钱"共用一个红 ——
 * 两个完全不同的意思共用一个槽位，迟早出错。所以见 [JizhangbaoColors]。
 */
internal val Ink = Color(0xFF0B1020)
internal val InkSurface = Color(0xFF141B2E)
internal val InkSurfaceHigh = Color(0xFF1B2337)
internal val InkSurfaceHighest = Color(0xFF212B44)
internal val InkSurfaceLow = Color(0xFF101627)
internal val InkBorder = Color(0xFF2A3552)
internal val InkText = Color(0xFFE6ECFF)
internal val InkTextMuted = Color(0xFFA8B3D0)
internal val Cyan = Color(0xFF22D3EE)
internal val Violet = Color(0xFF8B5CF6)
internal val ExpenseWarm = Color(0xFFFF6B81)
internal val IncomeMint = Color(0xFF34D399)

/** 浅色方案（用户方向是深色为主，但跟随系统时仍要能用）。 */
internal val PaperBg = Color(0xFFF6F8FC)
internal val PaperSurface = Color(0xFFFFFFFF)
internal val PaperSurfaceVariant = Color(0xFFEDF1F9)
internal val PaperBorder = Color(0xFFDCE3F0)
internal val PaperText = Color(0xFF151B2B)
internal val PaperTextMuted = Color(0xFF5B6785)
internal val ExpenseDeep = Color(0xFFD92D4B)
internal val IncomeDeep = Color(0xFF0E9F6E)
internal val AccentDeep = Color(0xFF0891B2)

/**
 * 业务语义色（`ADR-0014` 决策 2）。
 *
 * `cardBorder` 单独一个槽：深色下**层级靠描边**表达（阴影在深色里几乎看不见）。
 */
@Immutable
data class JizhangbaoColors(
    /** 支出：暖红（中文记账 App 的习惯）。 */
    val expense: Color,
    /** 收入：薄荷绿。 */
    val income: Color,
    /** 强调：结余、选中态、关键数字 —— 它既不是支出也不是收入。 */
    val accent: Color,
    /** 次要文字、单位、说明。 */
    val muted: Color,
    /** 卡片描边。 */
    val cardBorder: Color,
)

internal val TechDarkSemantics = JizhangbaoColors(
    expense = ExpenseWarm,
    income = IncomeMint,
    accent = Cyan,
    muted = InkTextMuted,
    cardBorder = InkBorder,
)

internal val PaperLightSemantics = JizhangbaoColors(
    expense = ExpenseDeep,
    income = IncomeDeep,
    accent = AccentDeep,
    muted = PaperTextMuted,
    cardBorder = PaperBorder,
)

/**
 * 当前主题的语义色。用 `staticCompositionLocalOf`（不是 `compositionLocalOf`）：
 * 主题切换会重建整棵树，不需要细粒度订阅。
 */
val LocalJizhangbaoColors = staticCompositionLocalOf { TechDarkSemantics }

/** 深色方案。`surfaceContainer*` 也要给 —— 新版的 Card / Dialog 正是从它们取底色。 */
internal val TechDarkColors = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF00212B),
    primaryContainer = Color(0xFF0B3A47),
    onPrimaryContainer = Color(0xFFB6F2FF),
    secondary = Violet,
    onSecondary = Color(0xFF1B0B3A),
    secondaryContainer = Color(0xFF2C1A55),
    onSecondaryContainer = Color(0xFFDDD1FF),
    background = Ink,
    onBackground = InkText,
    surface = InkSurface,
    onSurface = InkText,
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = InkTextMuted,
    surfaceContainerLowest = InkSurfaceLow,
    surfaceContainerLow = InkSurface,
    surfaceContainer = InkSurfaceHigh,
    surfaceContainerHigh = InkSurfaceHighest,
    surfaceContainerHighest = InkSurfaceHighest,
    outline = InkBorder,
    outlineVariant = InkBorder,
    error = ExpenseWarm,
    onError = Color(0xFF3A0A12),
)

/** 浅色方案（保留，不主动使用）。 */
internal val PaperLightColors = lightColorScheme(
    primary = AccentDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F1F8),
    onPrimaryContainer = Color(0xFF04303B),
    secondary = Violet,
    onSecondary = Color.White,
    background = PaperBg,
    onBackground = PaperText,
    surface = PaperSurface,
    onSurface = PaperText,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = PaperTextMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = PaperSurface,
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperSurfaceVariant,
    surfaceContainerHighest = PaperSurfaceVariant,
    outline = PaperBorder,
    outlineVariant = PaperBorder,
    error = ExpenseDeep,
    onError = Color.White,
)
