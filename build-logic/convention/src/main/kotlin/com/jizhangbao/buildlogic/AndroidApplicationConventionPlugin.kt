package com.jizhangbao.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `:app` 的约定插件。
 *
 * `targetSdk` 与版本号只在这里设置——AGP 9 的 `targetSdk` 位于应用的
 * `ApplicationDefaultConfig` 上，Library 侧没有该属性（与 AGP 8 一致）。
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureJizhangbaoAndroid()

            defaultConfig {
                targetSdk = AndroidDefaults.TARGET_SDK

                // 必须显式给出版本号：不写的话 AGP 9 产出的 APK 里
                // versionCode / versionName 都是**空字符串**（aapt2 dump badging 实测），
                // 那是一个装不上、也说不清是哪个构建的产物。
                versionCode = AndroidDefaults.VERSION_CODE
                versionName = AndroidDefaults.VERSION_NAME
            }
        }
    }
}
