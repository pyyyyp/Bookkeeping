plugins {
    alias(libs.plugins.jizhangbao.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jizhangbao.ledger"
}

// 本模块没有 @Database 类（它按 ADR-0007 住在 :app 的 data 包），
// 所以这里不需要 room.schemaLocation —— schema 由持有 @Database 的模块导出。

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    // 数据层：Room 实体 / DAO / 仓储实现（ADR-0007）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DI：@Inject / @Binds 与生成代码
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
}

// Ledger 账本（核心域）：账目条目、分类。
// 第一次业务切片见 REQ-001 与 docs/20-domain/ledger-model.md；
// 范围取舍（不做账户/多币种、物理删除、预置只读分类）见 ADR-0005；
// 数据层为什么住在这里（而不是 core:data）见 ADR-0007。
//
// 本模块不得依赖任何其他 :feature:*（R2）。
