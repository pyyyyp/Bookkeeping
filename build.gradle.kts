// 根构建脚本。
//
// 这里**只声明插件、不应用插件**（apply false）。原因有两个：
// 1. 约定插件（build-logic）内部用 pluginManager.apply("<id>") 按 id 应用插件，
//    被应用的插件必须已经在本构建的插件类路径上——这里的声明提供它。
// 2. 各模块自己不写 plugins 版本号，版本统一来自 gradle/libs.versions.toml。
//
// ⚠️ 不要添加 'org.jetbrains.kotlin.android'：AGP 9.0 起内置 Kotlin，加上即构建失败。
//    见 docs/60-runbooks/build.md
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false

    // 本插件**要应用**（没有 apply false）：它是全工程聚合校验任务的定义处。
    // 它注册 verifyDomainPurity / checkModuleDependencies，并把它们接入每个模块的 check。
    // 见 docs/30-architecture/module-graph.md 与 docs/40-tasks/T-003-architecture-verification.md
    alias(libs.plugins.jizhangbao.architecture)
}
