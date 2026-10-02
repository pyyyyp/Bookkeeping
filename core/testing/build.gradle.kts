plugins {
    alias(libs.plugins.jizhangbao.android.library)
}

android {
    namespace = "com.jizhangbao.core.testing"
}

dependencies {
    implementation(project(":core:domain"))
}

// 本模块当前**刻意没有任何源文件**：它要装的是「Fake / 测试数据构造器」，
// 而那些东西必须由真实的领域类型倒推出来。在第一个聚合落地之前先写，
// 只能写出猜的 Fake —— 那是负债而不是基础设施。
// 依赖声明现在就位，是为了让 R1/R7 的模块图从第一天起就是真实约束。
