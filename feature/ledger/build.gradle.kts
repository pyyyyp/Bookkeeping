plugins {
    alias(libs.plugins.jizhangbao.android.library)
}

android {
    namespace = "com.jizhangbao.ledger"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
}

// Ledger 账本（核心域）：账目条目、分类、账户。
// ⚠️ 聚合尚未落地 —— 领域模型等 Q-005（多账户）/Q-008（记账日）/Q-010（去重）
// 澄清后再写，不在这里猜。见 docs/20-domain/open-questions.md
//
// 模块与依赖现在就位，因为**模块边界 = 上下文边界**，而边界越晚划越贵。
// 本模块不得依赖任何其他 :feature:*（R2）。
