plugins {
    alias(libs.plugins.jizhangbao.android.library)
}

android {
    namespace = "com.jizhangbao.worklog"
}

dependencies {
    implementation(project(":core:domain"))

    testImplementation(libs.junit4)
}

// Worklog 工时（核心域）：记录工作时段，给出**出勤事实**。
// v0.3 起职责收敛：工时不参与金额计算（Q-012），它只回答"这天工作了多少分钟（已确认）"。
//
// ⚠️ v1 是**手工记录**：地理围栏要新依赖 + 位置权限，属于必须先问的改动（Q-024），
// 见 ADR-0012 决策 3。加班门槛不在本模块（它是钱的规则，归 Payroll，ADR-0012 决策 2）。
//
// ⚠️ 本模块目前**不含 UI**，因此不依赖 core:ui。
// 本模块不得依赖任何其他 :feature:*（R2）。
