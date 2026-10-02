plugins {
    alias(libs.plugins.jizhangbao.android.library)
}

android {
    namespace = "com.jizhangbao.payroll"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
}

// Payroll 薪资（核心域）：月薪 → 日薪 → 每日收入 → 工资单。
//
// 🔴 **本模块在 Q-015 定案前不得实现任何业务逻辑。**
// Q-015（缺勤与法定节假日是否带薪）直接改变每日收入公式：
//   工作日缺勤 = 0 还是 1×日薪？法定节假日休息 = 0 还是带薪 1×日薪？
// 猜错会让「日收入之和 ≠ 月薪」这个事实被掩盖进代码里，事后极难发现。
// 见 docs/20-domain/open-questions.md
//
// 本模块不得依赖任何其他 :feature:*（R2）。
