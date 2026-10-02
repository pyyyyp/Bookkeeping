package com.jizhangbao.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * R3 的源码级强制：domain 层不得出现任何框架 import。
 *
 * 为什么需要它，当 `:core:domain` 已经靠模块类型（`kotlin("jvm")`，无 Android 类路径）
 * 让 R3「写不出来」时？因为 **feature 模块内部的 `domain` 包不享受这个保护**——
 * 它们住在 Android Library 里，写 `import androidx.room.Entity` 是能编译过的。
 * 模块类型只能保护一个模块，保护不了包。
 *
 * ⚠️ 已知局限（不掩盖）：本任务只查 **import 语句**。若有人在 feature 的 domain 包里
 * 写全限定名（`android.os.Build.VERSION.SDK_INT`）而不 import，本任务查不出来。
 * 这是刻意的取舍：全限定名的写法在评审中极其显眼，而为它写一个真正的 Kotlin 解析器
 * 不划算。真正的结构性保护仍然是「能写不出来的地方就别给类路径」。
 */
abstract class VerifyDomainPurityTask : DefaultTask() {

    /** 所有属于 domain 层的 Kotlin 源文件。由约定插件按模块规则收集。 */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val domainSources: ConfigurableFileCollection

    /** 仅用于把绝对路径打印成相对路径。标 @Internal，避免它参与增量判断。 */
    @get:Internal
    abstract val rootDirPath: Property<String>

    /** 允许覆盖禁止清单的入口（默认取 [FORBIDDEN_IMPORT_PREFIXES]）。 */
    @get:Input
    val forbiddenImportPrefixes: List<String> = FORBIDDEN_IMPORT_PREFIXES

    @TaskAction
    fun verify() {
        val root = java.io.File(rootDirPath.get())
        val sources = domainSources.files.filter { it.isFile && it.extension == "kt" }

        // 防「静默失效」：扫描规则一旦写错（比如 domain 包改名），任务会扫到 0 个文件
        // 然后愉快地通过。那比没有这个任务更糟——它给了虚假的安全感。
        if (sources.isEmpty()) {
            throw GradleException(
                """
                |verifyDomainPurity 扫描到的 domain 源文件数为 0。
                |
                |这几乎总是**扫描规则失效**，而不是「项目里没有 domain 代码」：
                |  · :core:domain 的 src/main/kotlin 下应当始终有共享内核类型；
                |  · 其他模块按 `**/domain/**/*.kt` 收集。
                |请检查 ArchitectureVerificationPlugin 里 domainSourceTrees() 的规则是否还成立。
                |把「扫不到」当成通过，等于给架构规则一个虚假的绿灯。
                """.trimMargin(),
            )
        }

        val violations = sources
            .sortedBy { it.path }
            .flatMap { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                file.readLines().withIndex().mapNotNull { (index, line) ->
                    val trimmed = line.trimStart()
                    if (!trimmed.startsWith("import ")) return@mapNotNull null
                    val imported = trimmed.removePrefix("import ").trim()
                    val hit = forbiddenImportPrefixes.firstOrNull { imported.startsWith(it) }
                    hit?.let { "$relative:${index + 1}  $trimmed    ← 命中禁止前缀「$it」" }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("domain 层出现框架依赖（违反 R3）：")
                    violations.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("domain 层必须是纯 Kotlin：业务规则住在领域层，数据库、网络、UI、DI")
                    appendLine("都只是可替换的细节（原则 P2）。把框架类型留在 domain 里，")
                    appendLine("意味着换掉数据库要改业务规则——那正是这套分层要防止的事。")
                    appendLine()
                    appendLine("如果这是规则缺陷而不是违规，请先新增 ADR 并取得确认，不要放宽检查。")
                },
            )
        }

        logger.lifecycle(
            "verifyDomainPurity: 扫描 ${sources.size} 个 domain 源文件，未发现框架依赖（R3 通过）",
        )
    }

    companion object {
        /**
         * 禁止出现在 domain 层的前缀。
         *
         * 刻意**不含** `kotlinx.coroutines`：协程是纯 JVM 库，domain 层可以用它表达异步，
         * 它不把项目绑到任何平台。也**不含** `java.time` / `kotlin.*` —— 那是标准库。
         */
        val FORBIDDEN_IMPORT_PREFIXES = listOf(
            "android.",
            "androidx.",
            "com.google.dagger.",
            "dagger.",
            "javax.inject.",
            "retrofit2.",
            "okhttp3.",
            "com.squareup.",
            "kotlinx.serialization.",
            "io.coil-kt.",
            "org.koin.",
        )
    }
}
