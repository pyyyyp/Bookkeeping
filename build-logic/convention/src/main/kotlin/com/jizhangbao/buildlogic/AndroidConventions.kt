package com.jizhangbao.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion

/**
 * 全工程共用的 Android 配置值。改这里 = 改所有模块。
 *
 * 这些值必须与 `docs/00-charter/tech-baseline.md` 一致。
 */
internal object AndroidDefaults {
    /**
     * **必须在 37 及以上**：Compose BOM 2026.09.00 带进来的
     * `androidx.compose.ui:ui-android:1.12.1` 等依赖要求消费者
     * 以 API 37+ 编译，否则 AGP 直接报错。
     *
     * SDK 里 `platforms;android-37.0` 的 `AndroidVersion.ApiLevel` 是字符串
     * `"37.0"`，因此必须同时给出 `compileSdk = 37` 与 `compileSdkMinor = 0`，
     * 只写 37 会找不到平台目录。
     */
    const val COMPILE_SDK = 37
    const val COMPILE_SDK_MINOR = 0

    /** Android 8.0。理由见 tech-baseline.md。 */
    const val MIN_SDK = 26

    /**
     * 刻意**不跟着 compileSdk 升到 37**：
     * compileSdk 只决定「能调用哪些 API」，targetSdk 决定「运行时行为按哪个版本走」。
     * 本机没有真机也没有模拟器，升 targetSdk 等于单方面改变运行时行为却无法验证。
     * 等有真机可测时再升，并在此记录理由。
     */
    const val TARGET_SDK = 36

    /** 首个骨架版本。0.x 表示「还没有可用的业务功能」。 */
    const val VERSION_CODE = 1
    const val VERSION_NAME = "0.1.0"
}

/**
 * 所有 Android 模块共用的配置。
 *
 * ⚠️ 两个 AGP 9 的坑，都通过读 AGP 9.4.0 的 `gradle-api` 字节码实测确认：
 *
 * 1. **`CommonExtension` 在 AGP 9 已是非泛型接口**（AGP 8 是
 *    `CommonExtension<*, *, *, *, *, *>`）。照抄 AGP 8 模板会编译不过。
 * 2. **块式 DSL（`defaultConfig { }` / `compileOptions { }` / `buildFeatures { }`）
 *    只声明在具体接口上**（`LibraryExtension` / `ApplicationExtension`），
 *    `CommonExtension` 上只有属性访问器。因此这个共用函数**必须用属性赋值**
 *    （`defaultConfig.minSdk = ...`），写成块式会找不到方法。
 *
 * 用属性赋值还有一个好处：模块类型无关，Library 与 Application 共用一份。
 */
internal fun CommonExtension.configureJizhangbaoAndroid() {
    compileSdk = AndroidDefaults.COMPILE_SDK
    compileSdkMinor = AndroidDefaults.COMPILE_SDK_MINOR

    // minSdk 在 BaseFlavor 上（DefaultConfig 继承自它）
    defaultConfig.minSdk = AndroidDefaults.MIN_SDK

    // Java 语言级别。Kotlin 侧的 jvmTarget 由 AGP 内置 Kotlin 与其对齐。
    compileOptions.sourceCompatibility = JavaVersion.VERSION_21
    compileOptions.targetCompatibility = JavaVersion.VERSION_21
}
