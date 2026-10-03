package com.jizhangbao.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * 模块图规则的强制：R2、R7，以及 R3 的「依赖面」。
 *
 * 为什么用 Gradle 任务而不是源码断言（Konsist）：**模块图的唯一事实源是 Gradle 自己**。
 * 从源码文本里去猜「谁依赖谁」既慢又会猜错（依赖可以来自约定插件、版本目录、platform、
 * 传递依赖）。直接读 `configurations` 里声明的依赖，是精确且零成本的。
 *
 * 检查的是**声明**而非解析结果：不 resolve configuration，因此不会触发下载，
 * 也不会因为仓库不可达而失败。
 */
abstract class CheckModuleDependenciesTask : DefaultTask() {

    /**
     * 已声明的依赖边，每条形如 `<来自模块> -> <目标坐标>`。
     * 项目依赖的目标坐标写作 `project::<路径>`（如 `project:::core:domain`），
     * 外部依赖写作 `<group>:<name>`。
     */
    @get:Input
    abstract val dependencyEdges: ListProperty<String>

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()

        dependencyEdges.get().forEach { edge ->
            val (from, to) = edge.split(EDGE_SEPARATOR, limit = 2)
                .also { require(it.size == 2) { "非法依赖边格式：$edge" } }

            // ── R2 / R7 / R8 ──
            // 「只有 :app 可以依赖 :feature:*」一条就同时覆盖了三条规则：
            //   R2 feature → feature 被挡（from 是 feature，不是 :app）
            //   R7 core → feature 被挡
            //   任意模块 → feature 被挡，除了 :app（R8 允许 app 组装多个 feature）
            // 用一条判据而不是三条，是为了让违规信息只有一个来源，避免规则之间出现缝隙。
            if (to.startsWith(PROJECT_PREFIX + FEATURE_PREFIX) && from != APP_MODULE) {
                violations += "$from 依赖 $to —— 只有 $APP_MODULE 可以依赖 feature 模块（违反 R2 / R7 / R8）"
            }

            // ── R3 的依赖面 ──
            // 源码 import 干净不代表依赖干净：往 :core:domain 的 dependencies 里加一行
            // androidx 库，源码级检查是看不出来的（真正用上之前不会有 import）。
            if (from == DOMAIN_MODULE && FORBIDDEN_DEPENDENCY_PREFIXES.any { to.startsWith(it) }) {
                violations += "$DOMAIN_MODULE 依赖 $to —— 共享内核必须保持零框架依赖（违反 R3）"
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("模块依赖违反架构规则：")
                    violations.sorted().forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("跨上下文的协作必须走领域事件或只读投影，不是直接 import 对方的聚合：")
                    appendLine("  · feature 之间：订阅领域事件（见 docs/20-domain/context-map.md 的映射表）")
                    appendLine("  · 需要共享的原语：放进 :core:domain（共享内核要尽量小）")
                    appendLine()
                    appendLine("如果这是规则缺陷而不是违规，请先新增 ADR 并取得确认，不要放宽检查。")
                },
            )
        }

        logger.lifecycle(
            "checkModuleDependencies: 已检查 ${dependencyEdges.get().size} 条依赖声明，未发现违规（R2 / R7 / R8 / R3 通过）",
        )
    }

    companion object {
        const val EDGE_SEPARATOR = " -> "
        const val PROJECT_PREFIX = "project::"
        const val FEATURE_PREFIX = ":feature:"
        const val APP_MODULE = ":app"
        const val DOMAIN_MODULE = ":core:domain"

        /** domain 模块不得声明这些 group 的依赖。 */
        val FORBIDDEN_DEPENDENCY_PREFIXES = listOf(
            "android.", "androidx.", "com.android.", "com.google.dagger", "javax.inject",
            "retrofit2", "com.squareup", "com.google.devtools.ksp", "io.coil-kt",
        )
    }
}
