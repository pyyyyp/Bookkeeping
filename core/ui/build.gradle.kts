plugins {
    alias(libs.plugins.jizhangbao.android.library)
    // Compose 编译器随 Kotlin 版本发布（自 Kotlin 2.0 起），故版本号 = kotlin。
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jizhangbao.core.ui"
    // ⚠️ 刻意不写 buildFeatures { compose = true }：
    // AGP 9.4 下应用了 org.jetbrains.kotlin.plugin.compose 之后，该开关会被忽略并告警
    // （"Compose feature will be turned on. Remove android.buildFeatures.compose flag"）。
    // Compose 由插件开启，不由标志位开启。
}

dependencies {
    // core:ui 是设计系统，只放可复用的 UI 原语，不含业务规则（见 module-graph.md）
    implementation(project(":core:domain"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)

    // 预览/布局检查器只在 debug 需要
    debugImplementation(libs.compose.ui.tooling)
}
