package com.jizhangbao.core.testing

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 架构断言（R2 / R5 / R6 / R8 / R10）。
 *
 * ## 为什么还需要它，`checkModuleDependencies` 不是已经管住了 R2/R7/R8 吗
 *
 * 两者测的不是同一件事：
 *  · `checkModuleDependencies` 读的是 **Gradle 的依赖声明**——它管得住「feature 在
 *    build.gradle.kts 里依赖了另一个 feature」，管不住「通过某个传递依赖把对方的类
 *    拉进来」「同一个模块里不同上下文的包互相 import」。
 *  · 这里读的是 **源码文本**——管不住依赖声明，但能抓住包与包之间的实际引用。
 * 两条一起才算闭合。**不要**把其中一条当成另一条的替代品。
 *
 * ## R3 为什么不在这里重复断言
 *
 * R3（domain 不得引用框架）的唯一执行点是 `verifyDomainPurity` Gradle 任务。
 * 故意不在本文件里再写一遍：同一个规则有两处实现时，它们迟早会分叉，
 * 然后你会不知道该信哪一个。
 *
 * ## 关于「现在还没有违规可抓」
 *
 * R5 / R10 针对的是 `presentation` 与 `data` 包，而这些包**今天还不存在**
 * （feature 模块目前只有构建脚本）。也就是说这两条断言现在是「装好了但没靶子」。
 * 按 T-003 任务卡的要求，**反向验证**才是它们有效的证据：注入一个违规文件后
 * 断言必须失败。验证记录见任务卡。
 */
class ArchitectureTest {

    /**
     * 五个限界上下文。与 `docs/20-domain/context-map.md` 必须一致；
     * 新增上下文时这里也要加——否则新上下文不受 R2 保护。
     */
    private val contexts = listOf("ledger", "worklog", "payroll", "calendar", "insight")

    /**
     * 生产源集，且**只保留真正的模块目录**。
     *
     * 为什么要过滤：`scopeFromProduction()` 会把仓库里所有 `src/main/kotlin` 都扫进来，
     * 包括 `build-logic/`（约定插件自己的源码）。它不是业务模块，不该被业务规则检查。
     */
    private val production: List<KoFileDeclaration> = Konsist.scopeFromProduction().files
        .filter { file -> MODULE_PATH_MARKERS.any { marker -> file.path.contains(marker) } }

    // ─────────────────────────────────────────────────────────────
    // 防「静默失效」：规则写得再漂亮，只要 scope 是空的就永远通过
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `架构断言的扫描范围不能为空`() {
        val paths = production.map { it.path }
        assertTrue(
            "架构断言没有扫到任何生产源文件——所有规则都会「通过」，但那是假的。\n" +
                "请检查 Konsist 的项目根探测与 MODULE_PATH_MARKERS 是否还成立。",
            production.isNotEmpty(),
        )
        assertTrue(
            "共享内核 core:domain 的文件不在扫描范围里，说明路径规则失效了。扫到的文件：$paths",
            paths.any { it.contains("core/domain/src/main/kotlin") || it.contains("core\\domain\\src\\main\\kotlin") },
        )
    }

    // ─────────────────────────────────────────────────────────────
    // R2 · 上下文之间不得在源码层面互相引用
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `R2 一个上下文不得 import 另一个上下文的类型`() {
        val violations = production.flatMap { file ->
            val owner = contextOf(file.packagee?.name)
            file.imports.mapNotNull { imported ->
                val target = contextOf(imported.name)
                when {
                    owner == null || target == null || target == owner -> null
                    else -> "${file.path}\n      本文件属于「$owner」，却 import 了「$target」上下文的 ${imported.name}"
                }
            }
        }
        assertNoViolations("R2（feature 之间不得互相依赖）", violations)
    }

    // ─────────────────────────────────────────────────────────────
    // R5 · presentation 只依赖 domain/application 的抽象
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `R5 presentation 不得直接引用 data 层或 DI 装配`() {
        val violations = production.flatMap { file ->
            if (!file.path.isInLayer("presentation")) return@flatMap emptyList()
            file.imports.mapNotNull { imported ->
                // 注意：只拦 data / di。presentation 引用 domain 的抽象是**允许**的，
                // 那正是「只依赖抽象」的意思——写成「任何分层包都拦」会把正确写法判成违规。
                val layer = imported.name.layerOf()
                when (layer) {
                    "data", "di" ->
                        "${file.path}\n      presentation 直接 import 了 ${layer} 层的 ${imported.name}"
                    else -> null
                }
            }
        }
        assertNoViolations("R5（presentation 只依赖抽象）", violations)
    }

    // ─────────────────────────────────────────────────────────────
    // R6 · 外部模型绝不进入领域层
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `R6 domain 不得引用 data 层，也不得用外部模型命名`() {
        val importViolations = production.flatMap { file ->
            if (!file.path.isInLayer("domain")) return@flatMap emptyList()
            file.imports.mapNotNull { imported ->
                // 同样只拦 data / di / presentation。
                // domain 引用 domain 是**合法**的：共享内核 :core:domain 就是要被各上下文
                // 的领域层引用的。把这一条也拦掉等于禁止使用共享内核。
                val layer = imported.name.layerOf()
                when (layer) {
                    "data", "di", "presentation" ->
                        "${file.path}\n      domain 层 import 了 ${layer} 层的 ${imported.name}（必须经 Mapper 转换）"
                    else -> null
                }
            }
        }

        // 命名是启发式：拦不住所有情况，但 DTO/Entity/Dao 这几个后缀出现在 domain 里，
        // 基本就说明外部模型漏进来了。
        val nameViolations = production
            .filter { it.path.isInLayer("domain") }
            .flatMap { file ->
                file.classes().mapNotNull { cls ->
                    EXTERNAL_MODEL_SUFFIXES
                        .firstOrNull { suffix -> cls.name.endsWith(suffix) }
                        ?.let { suffix ->
                            "${file.path}\n      领域类 ${cls.name} 以「$suffix」结尾——听起来像外部模型的形状"
                        }
                }
            }

        assertNoViolations("R6（外部模型不进 domain）", importViolations + nameViolations)
    }

    // ─────────────────────────────────────────────────────────────
    // R10 · 同上下文内 data 只被 di 与自身使用
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `R10 只有 di 与 data 自己可以引用 data 层`() {
        val violations = production.flatMap { file ->
            if (file.path.isInLayer("data") || file.path.isInLayer("di")) return@flatMap emptyList()
            file.imports.mapNotNull { imported ->
                when {
                    imported.name.layerOf() == "data" ->
                        "${file.path}\n      非 di / 非 data 的文件 import 了 data 层的 ${imported.name}"
                    else -> null
                }
            }
        }
        assertNoViolations("R10（data 只被 di 与自身使用）", violations)
    }

    // ─────────────────────────────────────────────────────────────
    // R8 · app 是叶子，且不含业务规则
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `R8 除 app 自己以外不得引用 app 包`() {
        val violations = production.flatMap { file ->
            if (file.path.contains("/app/")) return@flatMap emptyList()
            file.imports.mapNotNull { imported ->
                when {
                    imported.name.startsWith("com.jizhangbao.app.") ->
                        "${file.path}\n      引用了 :app 的 ${imported.name}——依赖方向反了（app 是组装层，叶子）"
                    else -> null
                }
            }
        }
        assertNoViolations("R8（app 是唯一组装层且为叶子）", violations)
    }

    // ─────────────────────────────────────────────────────────────

    private fun assertNoViolations(rule: String, violations: List<String>) {
        assertTrue(
            "违反 $rule，共 ${violations.size} 处：\n" +
                violations.joinToString("\n") { "  · $it" } +
                "\n\n修法见 docs/20-domain/context-map.md 的映射表：跨上下文走领域事件或只读投影，" +
                "不是直接引用对方的类型。若这是规则缺陷，请先新增 ADR。",
            violations.isEmpty(),
        )
    }

    /** 从包名或 import 的完全限定名里认出它属于哪个限界上下文。 */
    private fun contextOf(fqName: String?): String? {
        if (fqName == null) return null
        return contexts.firstOrNull { fqName == "com.jizhangbao.$it" || fqName.startsWith("com.jizhangbao.$it.") }
    }

    /** 从文件路径认出它所在的分层（domain / data / di / presentation）。 */
    private fun String.isInLayer(layer: String): Boolean =
        contains("/$layer/") || contains("\\$layer\\")

    /** 从完全限定名认出它属于哪个分层；不是本项目的分层包则返回 null。 */
    private fun String.layerOf(): String? =
        LAYERS.firstOrNull { this.contains(".$it.") }

    private companion object {
        /** 只扫这些目录下的源码：业务模块。build-logic 不在其中。 */
        val MODULE_PATH_MARKERS = listOf("/core/", "/feature/", "/app/", "\\core\\", "\\feature\\", "\\app\\")

        /** 分层包名。顺序有意义：先匹配到的即为该类型所属分层。 */
        val LAYERS = listOf("domain", "data", "di", "presentation")

        /** 这些后缀出现在 domain 里，说明外部模型可能漏进来了。 */
        val EXTERNAL_MODEL_SUFFIXES = listOf("Dto", "DTO", "Entity", "Dao", "DAO")
    }
}
