plugins {
    alias(libs.plugins.jizhangbao.android.library)
}

android {
    namespace = "com.jizhangbao.core.common"
}

// 说明：这里曾写死 compileSdk / minSdk / compileOptions，现已收敛到
// build-logic 的 jizhangbao.android.library 约定插件 —— 全工程一份配置。
