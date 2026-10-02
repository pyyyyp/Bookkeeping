plugins {
    alias(libs.plugins.jizhangbao.android.library)
}

android {
    namespace = "com.jizhangbao.worklog"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
}

// Worklog 工时（核心域）：地理围栏记录工作时段。
// v0.3 起它的职责收敛为两件事：判定「哪天算加班」+ 给出工时统计。
// 工时不参与金额计算（Q-012）。
//
// ⚠️ 聚合尚未落地 —— 等 Q-009（固定班次）/Q-014（围栏提醒未响应）澄清。
// 本模块不得依赖任何其他 :feature:*（R2）。
