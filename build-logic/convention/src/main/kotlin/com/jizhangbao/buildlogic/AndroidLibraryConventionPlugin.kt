package com.jizhangbao.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * 所有 `com.android.library` 模块的约定插件。
 *
 * ⚠️ 这里**不应用** `org.jetbrains.kotlin.android`：AGP 9.0 起内置 Kotlin，
 * 加上该插件会直接构建失败。见 `docs/60-runbooks/build.md`。
 *
 * `namespace` 刻意留给各模块自己声明——它同时是 R2/R6 架构断言的判断依据，
 * 显式写出来比从模块路径猜出来更不容易错。
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureJizhangbaoAndroid()
        }
    }
}
