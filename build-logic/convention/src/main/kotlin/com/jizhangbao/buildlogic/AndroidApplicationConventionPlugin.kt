package com.jizhangbao.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.util.Properties

/**
 * `:app` 的约定插件。
 *
 * `targetSdk`、版本号与签名配置只在这里设置——AGP 9 的 `targetSdk` 位于应用的
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

            configureReleaseSigning(this@with)
        }
    }
}

/**
 * 有密钥就签名，没有就**照常构建**（`T-024`）。
 *
 * ## 为什么写成"可选"
 *
 * 密钥与密码**都不入库**（`.gitignore` 覆盖 `*.jks` / `keystore.properties`）。
 * 如果这里写成"必须存在"，那么任何一次干净的克隆、以及 CI，都会**构建失败** ——
 * 一个为了发布而加的配置，把日常开发弄坏了，那是净损失。
 *
 * 所以：文件在 → release 包签名；文件不在 → release 包不签名（**明确说一声**，
 * 而不是静默产出一个看起来没问题的包）。日常开发用 debug 包，不受影响。
 *
 * ## ⚠️ 签名身份不可更换
 *
 * 同一个 applicationId 一旦用某个密钥发布出去，后续版本**必须**用同一个密钥签名，
 * 否则应用商店与系统都会拒绝覆盖安装。密钥丢了 = 再也发不了更新 ——
 * 所以它必须进密钥管理器并做离线备份（`docs/60-runbooks/release.md`）。
 */
private fun ApplicationExtension.configureReleaseSigning(project: Project) {
    val propertiesFile = project.rootProject.file("keystore.properties")
    if (!propertiesFile.exists()) {
        project.logger.lifecycle(
            "未找到 keystore.properties：release 包将**不签名**（自用侧载用 debug 包即可；" +
                "要发布请按 docs/60-runbooks/release.md 配置密钥）。",
        )
        return
    }

    val properties = Properties().apply {
        propertiesFile.inputStream().use { load(it) }
    }
    val storePath = requireNotNull(properties.getProperty("storeFile")) {
        "keystore.properties 缺少 storeFile"
    }

    signingConfigs.create("release") {
        storeFile = project.rootProject.file(storePath)
        storePassword = properties.getProperty("storePassword")
        keyAlias = properties.getProperty("keyAlias")
        keyPassword = properties.getProperty("keyPassword")
    }
    buildTypes.getByName("release") {
        signingConfig = signingConfigs.getByName("release")
    }
    project.logger.lifecycle("release 包将使用 keystore.properties 指定的密钥签名。")
}
