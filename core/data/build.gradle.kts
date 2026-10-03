plugins {
    alias(libs.plugins.jizhangbao.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.jizhangbao.core.data"
}

// Room 的 schema JSON 必须提交进 Git —— 没有它就无法写迁移测试（ADR-0001 决策 3）。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}

// ─────────────────────────────────────────────────────────────
// ⚠️ 本模块当前**没有 @Database 类**，这是刻意的，不是遗漏。
//
// Room 拒绝空的实体表：
//   e: [ksp] JizhangbaoDatabase.kt:26: @Database annotation must specify list of entities
// 而为了让它编译通过就随手编一个「占位表」，等于把猜出来的领域结构
// 写进唯一事实源的 schema 里 —— schema 一旦入库，删表就要写迁移。
//
// 因此 T-002 只把 Room 的**工程可用性**验证掉，不写出任何表结构：
// 在仓库外的一次性工程里（AGP 9.4.0 + KSP 2.3.12 + Room 2.8.5 + compileSdk 37）
// 实测确认 —— `kspDebugKotlin` 通过、生成了 `<Dao>_Impl.kt` 与
// `<Database>_Impl.kt`、并导出了 `schemas/<Database>/1.json`。
// 复现步骤见 docs/60-runbooks/build.md「Room 可用性验证」。
//
// 第一个 @Entity 必须由真实聚合倒推（R6）—— 但它**不住在本模块**：
// 实体/Mapper/仓储实现必须引用上下文的领域类型，而本模块不得依赖任何 feature（R7）。
// 归属见 ADR-0007：实现在 feature 模块的 data 包，唯一的 @Database 在 :app 的 data 包；
// 本模块只承担**跨上下文共享**的数据基础设施（例如 TypeConverter）。
// ─────────────────────────────────────────────────────────────
