plugins {
    alias(libs.plugins.jizhangbao.android.library)
    alias(libs.plugins.kotlin.compose)
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
    // core:common：日志接口（T-015 / ADR-0010）。数据层的异常翻译要把原因记下来，
    // 而"往哪记"是可替换的基础设施细节 —— 接口住 core:common，实现绑在 :app。
    implementation(project(":core:common"))

    // presentation：Compose 与 ViewModel
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // 数据层：Room 实体 / DAO / 仓储实现（ADR-0007）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DI：@Inject / @Binds 与生成代码
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    // ViewModel 测试需要 Dispatchers.setMain：viewModelScope 默认跑在 Dispatchers.Main 上，
    // 而单元测试里没有主线程。版本与生产解析到的 coroutines-core 对齐（见 libs.versions.toml）
    testImplementation(libs.kotlinx.coroutines.test)

    // 仪器化测试（T-010 数据层）：DAO 的 SQL 只有真 SQLite 能验证。
    // ⚠️ CI 跑不了它（runner 上没有模拟器），只能本机 `connectedDebugAndroidTest`——
    // 这是**已知且已记录**的缺口，不是"应该能跑"。
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // androidTest 源集里那个测试专用的 @Database 也要 Room 生成实现
    kspAndroidTest(libs.androidx.room.compiler)
}

// Ledger 账本（核心域）：账目条目、分类。
// 第一次业务切片见 REQ-001 与 docs/20-domain/ledger-model.md；
// 范围取舍（不做账户/多币种、物理删除、预置只读分类）见 ADR-0005；
// 数据层为什么住在这里（而不是 core:data）见 ADR-0007。
//
// 本模块不得依赖任何其他 :feature:*（R2）。
