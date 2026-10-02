plugins {
    alias(libs.plugins.jizhangbao.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jizhangbao.app"
    // ⚠️ 不写 buildFeatures { compose = true }：见 core/ui/build.gradle.kts 的说明。
}

dependencies {
    // :app 是唯一可同时依赖多个 feature 的模块，且不含业务规则（R8）
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))

    implementation(project(":feature:ledger"))
    implementation(project(":feature:worklog"))
    implementation(project(":feature:payroll"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:insight"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
