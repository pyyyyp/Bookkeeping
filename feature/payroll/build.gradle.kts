plugins {
    alias(libs.plugins.jizhangbao.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jizhangbao.payroll"
}

dependencies {
    implementation(project(":core:domain"))
    // core:common：日志接口（ADR-0010）。仓储的异常翻译要把原因记下来。
    implementation(project(":core:common"))

    // 界面（T-033 / REQ-017）：工资单页与月薪配置
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // 数据层：月薪表（ADR-0007）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DI：工资单用例要能被 :app 装配（ADR-0008 的读通路；它消费 Calendar 与 Worklog 两个端口）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Payroll 薪资（核心域）：月薪 → 日薪 → 每日收入 → 工资单。
//
// Q-015 已定案（缺勤不计、法定节假日休息带薪，见 open-questions.md），
// 所以业务逻辑可以动工。算法与取舍见 docs/20-domain/payroll-model.md 与 REQ-013。
//
// 本卡只做**纯计算**：出勤/加班由 Worklog 提供，而它还没实现 ——
// 所以出勤在本轮是**参数**，不造假数据源、也不默认出勤。
//
// ⚠️ 本模块目前**不含 UI**，因此不依赖 core:ui。
// 本模块不得依赖任何其他 :feature:*（R2）。
