plugins {
    alias(libs.plugins.jizhangbao.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jizhangbao.worklog"
}

dependencies {
    implementation(project(":core:domain"))
    // core:ui：设计系统（T-037 起工时页也用卡片/语义色）。R1 明确允许 feature -> core:ui。
    implementation(project(":core:ui"))
    // 界面（T-031）：Compose 与 ViewModel。
    // ⚠️ 仍然**没有**引入导航库（Q-028）—— 页面切换在 :app 用一个 when 做。
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    // 权限申请（REQ-016/AC-8）：rememberLauncherForActivityResult 住这里
    implementation(libs.androidx.activity.compose)
    // core:common：日志接口（ADR-0010）。数据层的异常翻译要把原因记下来（REQ-006 的教训）。
    implementation(project(":core:common"))

    // 数据层：Room 实体 / DAO / 仓储实现（ADR-0007）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Flow / callbackFlow（LocationSource 的端口形状，REQ-016）。
    // ⚠️ 这**不是**引入新依赖：coroutines 1.9.0 本来就在这张依赖图里（各模块都在用，
    // 版本目录里早就有版本号），这里只是把 worklog 用到它的那部分**显式声明**出来 ——
    // 靠 Room 传递进来是脆的，哪天 Room 换了实现就会突然编译不过。
    implementation(libs.kotlinx.coroutines.core)

    // DI：@Inject / @Binds 与生成代码
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    // DAO 的挂起函数在测试里要 runBlocking
    testImplementation(libs.kotlinx.coroutines.test)

    // 仪器化测试（T-029）：DAO 的 SQL 只有真 SQLite 能验证。
    // ⚠️ CI 跑不了它（runner 上没有模拟器），只能本机 `am instrument` —— 已知且已记录的缺口。
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // androidTest 源集里那个测试专用的 @Database 也要 Room 生成实现
    kspAndroidTest(libs.androidx.room.compiler)
}

// Worklog 工时（核心域）：记录工作时段，给出**出勤事实**。
// v0.3 起职责收敛：工时不参与金额计算（Q-012），它只回答"这天工作了多少分钟（已确认）"。
//
// ⚠️ v1 是**手工记录**：地理围栏要新依赖 + 位置权限，属于必须先问的改动（Q-024），
// 见 ADR-0012 决策 3。加班门槛不在本模块（它是钱的规则，归 Payroll，ADR-0012 决策 2）。
//
// ⚠️ 本模块**含 UI**（T-031 起），T-037 起用 core:ui 的设计系统。
// 本模块不得依赖任何其他 :feature:*（R2）。
