rootProject.name = "jizhangbao"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// ─────────────────────────────────────────────────────────────
// 模块清单：一个限界上下文 = 一个 feature 模块
// 与 docs/20-domain/context-map.md 必须一一对应（R7/§4）
// 新增上下文时同步更新：本文件 + context-map.md + module-graph.md + 新增 ADR
// ─────────────────────────────────────────────────────────────

// 共享内核（纯 Kotlin JVM，无 Android 类路径 —— 这是 R3 的强制手段，见 ADR-0001）
include(":core:domain")

// Android 侧模块（core:ui / core:data / core:common / core:testing / app）
// 与 feature:ledger / worklog / payroll / insight
// 待 T-002 安装 Android SDK 后加入
