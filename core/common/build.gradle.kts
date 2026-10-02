plugins {
    // ⚠️ AGP 9.0+ 内置 Kotlin 支持，**不再需要** 'org.jetbrains.kotlin.android' 插件。
    // 若加上它，构建会直接失败：
    //   "The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin
    //    support since AGP 9.0."
    // 这是 AGP 8 → 9 的破坏性变更之一，AGP 8 时代的模板不能照抄。
    // 见 https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.jizhangbao.core.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
