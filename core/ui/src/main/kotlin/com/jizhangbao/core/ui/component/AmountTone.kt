package com.jizhangbao.core.ui.component

/**
 * 金额的语气（`ADR-0014` 决策 2）。
 *
 * ⚠️ 刻意**不引用领域层的 `EntryDirection`**：界面组件不该认识业务类型，
 * 而"用什么语气显示"本来就是调用方的决定。这样 `core:ui` 保持纯设计系统。
 *
 * ⚠️ 单独一个文件：detekt 的 `MatchingDeclarationName` 要求"单一声明"的文件与文件名同名 ——
 * 把它和 `AmountText` 放一起，被点名的是这个枚举。
 */
enum class AmountTone { NEUTRAL, EXPENSE, INCOME, ACCENT, MUTED }
