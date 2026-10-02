plugins {
    alias(libs.plugins.jizhangbao.android.library)
}

android {
    namespace = "com.jizhangbao.insight"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
}

// Insight 统计（支撑域）：日 / 周 / 月汇总。
// **它是读模型，没有聚合、没有不变式** —— 不要在这里放业务规则。
//
// 本模块不得依赖任何其他 :feature:*（R2）。
