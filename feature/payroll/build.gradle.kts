plugins {
    alias(libs.plugins.jizhangbao.android.library)
}

android {
    namespace = "com.jizhangbao.payroll"
}

dependencies {
    implementation(project(":core:domain"))

    testImplementation(libs.junit4)
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
