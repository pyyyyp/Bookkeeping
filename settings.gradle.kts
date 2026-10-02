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
// 与 docs/20-domain/context-map.md 必须一一对应（R2 / AGENTS.md 第 4 节）
// 新增上下文时同步更新：本文件 + context-map.md + module-graph.md + 新增 ADR
// ─────────────────────────────────────────────────────────────

// 共享内核：纯 Kotlin JVM，无 Android 类路径 —— R3 的强制手段（ADR-0001 决策 4）
include(":core:domain")

// Android 基础设施
include(":core:common")

// 其余模块待 T-002 逐步加入（先验证 AGP 9.4.0 可用性，再批量创建）：
//   :core:ui  :core:data  :core:testing  :app
//   :feature:ledger  :feature:worklog  :feature:payroll
//   :feature:calendar  :feature:insight
