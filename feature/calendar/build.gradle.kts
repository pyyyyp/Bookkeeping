plugins {
    alias(libs.plugins.jizhangbao.android.library)
}

android {
    namespace = "com.jizhangbao.calendar"
}

dependencies {
    implementation(project(":core:domain"))
}

// Calendar 工作日历（支撑域）：判定某天是工作日 / 休息日 / 法定节假日（含调休）。
//
// ⚠️ 本模块**不含 UI**，因此不依赖 core:ui —— 它是被其他上下文查询的判定服务。
//
// 🔴 Q-016（节假日数据来源与更新机制）未定，数据层不能动工：
// 内置 JSON、第三方 API、用户手动标记三条路会写出完全不同的数据层。
// 本模块不得依赖任何其他 :feature:*（R2）。
