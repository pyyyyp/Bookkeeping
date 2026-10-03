plugins {
    alias(libs.plugins.jizhangbao.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jizhangbao.calendar"
}

dependencies {
    implementation(project(":core:domain"))
    // core:common：日志接口（T-015 / ADR-0010）。坏数据行要记下来，
    // 而"往哪记"是可替换的基础设施细节 —— 接口住 core:common，实现绑在 :app。
    implementation(project(":core:common"))

    // DI：端口实现要能被 :app 装配（ADR-0008 的读通路，ADR-0011 决策 4 的裁决）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)

    // 仪器化测试：**资产文件真的被打进包里了吗**。
    // 纯 JVM 测试证明不了这一点，而它坏掉的表现是"永远显示今年没有节假日" ——
    // 一个安静的错误比一个崩溃难查得多（T-025 / REQ-012/AC-4）。
    // ⚠️ CI 跑不了它（runner 上没有模拟器），只能本机 `am instrument` —— 已知且已记录的缺口。
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// Calendar 工作日历（支撑域）：判定某天是工作日 / 休息日 / 法定节假日（含调休）。
//
// ⚠️ 本模块**不含 UI**，因此不依赖 core:ui —— 它是被其他上下文查询的判定服务。
//
// Q-016 已定案（内置数据文件 + 每年手动更新，见 ADR-0011），所以数据层可以动工。
// ⚠️ 本卡交付的是**机制**：真实的法定节假日日期不在这里填 ——
// 那是现实世界的事实，凭记忆编就是猜（P5）。填数据是每年一次的手工步骤。
//
// 本模块不得依赖任何其他 :feature:*（R2）。
