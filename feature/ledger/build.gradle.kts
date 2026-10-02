plugins {
    alias(libs.plugins.jizhangbao.android.library)
}

android {
    namespace = "com.jizhangbao.ledger"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    // 领域测试：只需要断言与测试运行器，不需要 Android 测试设施
    testImplementation(libs.junit4)
}

// Ledger 账本（核心域）：账目条目、分类。
// 第一次业务切片见 REQ-001 与 docs/20-domain/ledger-model.md；
// 范围取舍（不做账户/多币种、物理删除、预置只读分类）见 ADR-0005。
//
// 本模块不得依赖任何其他 :feature:*（R2）。
