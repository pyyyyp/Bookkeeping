plugins {
    alias(libs.plugins.jizhangbao.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jizhangbao.insight"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    // presentation：Compose 与 ViewModel
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // DI：用例与 ViewModel 由 Hilt 构造注入
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Insight 统计（支撑域）：日 / 周 / 月汇总。
// **它是读模型，没有聚合、没有不变式** —— 不要在这里放业务规则。
//
// 数据从哪来：**不直接读 Ledger 的表**，而是通过共享内核里的
// `LedgerTotalsReader` 端口（ADR-0008）。这里只有"把年月换算成时间范围"这一件实质逻辑。
//
// 本模块不得依赖任何其他 :feature:*（R2）—— 也正因为如此，
// 「账本变了要重算」这件事由组合根（:app）通过 `refreshSignal` 传进来，本模块不订阅账本。

