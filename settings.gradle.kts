rootProject.name = "jizhangbao"

pluginManagement {
    // 约定插件所在的可独立构建（included build）。
    // 它让「新增一个上下文」变成「一行 include + 一个 3 行的 build.gradle.kts」，
    // 降低架构遵守成本本身就是一种架构保障（ADR-0001 决策 7）。
    includeBuild("build-logic")

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
// 与 docs/20-domain/context-map.md 必须一一对应（R2 / AGENTS.md 第 4 节）
// 新增上下文时同步更新：本文件 + context-map.md + module-graph.md + 新增 ADR
// ─────────────────────────────────────────────────────────────

// 共享内核：纯 Kotlin JVM，无 Android 类路径 —— R3 的强制手段（ADR-0001 决策 4）。
// ⚠️ 它不是 Android Library，因此**不使用** build-logic 的 Android 约定插件。
include(":core:domain")

// Android 基础设施
include(":core:common")
include(":core:ui")
include(":core:data")
include(":core:testing")

// 应用宿主：唯一可同时依赖多个 feature 的模块（R8）
include(":app")

// 限界上下文（core domain）
include(":feature:ledger")
include(":feature:worklog")
include(":feature:payroll")

// 限界上下文（supporting domain）
include(":feature:calendar")
include(":feature:insight")
