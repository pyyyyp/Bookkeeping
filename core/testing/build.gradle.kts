import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.jizhangbao.android.library)
}

android {
    namespace = "com.jizhangbao.core.testing"
}

dependencies {
    implementation(project(":core:domain"))

    // ── 架构断言（T-003）──
    // 只放在 test 源集：Konsist 会带进一整套 kotlin-compiler-embeddable（约 50 MB），
    // 它绝不能进任何会被打进 APK 的配置。
    testImplementation(libs.konsist)
    testImplementation(libs.junit4)
}

// ⚠️⚠️ 这一段不是优化，是**正确性所必需**，删掉它架构断言会静默失效。
//
// ArchitectureTest 会去扫**其他模块**的源码，但那些文件不在 :core:testing 的
// 任何 source set 里。Gradle 因此认为「本任务的输入没变」，直接跳过测试任务。
// 后果：在别的模块里注入违规后，构建**仍然是 BUILD SUCCESSFUL**。
//
// 这不是假设。T-003 做反向验证时第一次就撞上了：
//   > Task :core:testing:testDebugUnitTest UP-TO-DATE
//   BUILD SUCCESSFUL        ← 而当时正有 4 个违规文件躺在那儿
//
// 修法：把「会被扫描的源码」显式声明为本任务的输入。
// 代价是任一模块的 main 源码变动都会让架构断言重跑（几秒），这个代价必须付。
tasks.withType<Test>().configureEach {
    inputs.files(
        rootProject.fileTree(rootProject.projectDir) {
            // 模块深度 1（app/）与深度 2（core/domain/、feature/ledger/）
            include("*/src/main/kotlin/**/*.kt")
            include("*/*/src/main/kotlin/**/*.kt")
            // build-logic 是构建代码，不是业务模块；ArchitectureTest 里也把它过滤掉了
            exclude("build-logic/**")
            exclude("**/build/**")
        },
    )
        .withPropertyName("scannedArchitectureSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// 本模块的 main 源集**刻意仍然没有源文件**：它要装的是「Fake / 测试数据构造器」，
// 而那些东西必须由真实的领域类型倒推出来。在第一个聚合落地之前先写，
// 只能写出猜的 Fake —— 那是负债而不是基础设施。
//
// test 源集里现在有 ArchitectureTest：架构断言属于「测试基础设施」，
// 放在这里既不进 APK，也不必让 :app 背一个只用于测试的重依赖。
//
// ⚠️ 不要把 Konsist 与 dev.detekt:detekt-test 放在同一个测试 classpath 上：
// 两者各自带一套 Kotlin 编译器，会互相顶版本，Konsist 会直接解析失败。
