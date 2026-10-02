package com.jizhangbao.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.register

/**
 * 把 R1–R12 里「只能靠自觉」的那几条变成构建失败。
 *
 * 只应用在**根项目**上：两个校验任务是全工程聚合任务，
 * 注册到每个模块会导致同样的扫描跑 11 遍。
 *
 * 覆盖范围（诚实版，不夸大）：
 *
 * | 规则 | 手段 | 本插件负责？ |
 * |---|---|---|
 * | R2 feature 互不依赖 | checkModuleDependencies | ✅ |
 * | R3 domain 无框架（源码） | verifyDomainPurity | ✅ |
 * | R3 domain 无框架（依赖面） | checkModuleDependencies | ✅ |
 * | R7 core 不依赖 feature | checkModuleDependencies | ✅ |
 * | R8 app 才有权组装 feature | checkModuleDependencies | ✅ |
 * | R1 / R9 编译期即可拦 | 编译器 | ❌ 无需 |
 * | R5 / R6 / R10 层与包规则 | Konsist 断言（见 ArchitectureTest） | ❌ 不在本插件 |
 * | R11 禁用 api(...) | 代码评审 | ❌ |
 * | R12 各 feature 可独立构建 | CI（T-004） | ❌ |
 */
class ArchitectureVerificationPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        check(this == rootProject) {
            "jizhangbao.architecture 只能应用在根项目上（当前是 $path）——它是全工程聚合校验。"
        }

        val verifyDomainPurity = tasks.register<VerifyDomainPurityTask>("verifyDomainPurity") {
            group = "verification"
            description = "扫描 domain 层源文件，发现框架 import 即失败（R3）。"
            rootDirPath.set(rootProject.layout.projectDirectory.asFile.absolutePath)
            domainSources.from(domainSourceTrees())
        }

        val checkModuleDependencies = tasks.register<CheckModuleDependenciesTask>("checkModuleDependencies") {
            group = "verification"
            description = "校验模块依赖图：R2 / R7 / R8 与 R3 的依赖面。"
        }

        // 依赖边必须在**所有项目都配置完**之后收集，否则会漏掉后声明的模块。
        // 这里不用执行期读配置的写法：那样在开启配置缓存时会直接失败，
        // 而现在这样只是「与配置缓存不兼容」，留了升级余地。
        gradle.projectsEvaluated {
            checkModuleDependencies.configure {
                dependencyEdges.set(collectDependencyEdges())
            }
        }

        // 接入 check 生命周期：这样 ./gradlew build / check 会自动执行它们。
        // 用 allprojects 而不是 subprojects —— :core:domain 是 :core 的子项目，不在直接子项里。
        allprojects {
            if (this == rootProject) return@allprojects
            tasks.matching { it.name == "check" }.configureEach {
                dependsOn(verifyDomainPurity, checkModuleDependencies)
            }
        }
    }

    /**
     * 收集「属于 domain 层」的源文件树。
     *
     * 两类地方都算 domain：
     *  1. `:core:domain` —— 整个模块都是领域层（共享内核）
     *  2. 其他模块里的 `domain` 包 —— feature 模块内部分层的领域层
     *
     * 第 2 类是这条规则存在的真正理由：那些文件住在 Android Library 里，
     * 模块类型保护不到它们。
     */
    private fun Project.domainSourceTrees() = allprojects
        .filter { it != rootProject }
        .map { module ->
            val kotlinDir = module.file("src/main/kotlin")
            if (module.path == CheckModuleDependenciesTask.DOMAIN_MODULE) {
                module.fileTree(kotlinDir) { include("**/*.kt") }
            } else {
                module.fileTree(kotlinDir) { include("**/domain/**/*.kt") }
            }
        }

    /**
     * 读出所有模块**已声明**的依赖边。
     *
     * 三个关键设计，每条都有实测依据：
     *
     * 1. **只读声明，不 resolve**：`configuration.dependencies` 给的是本配置（含 `extendsFrom`
     *    继承来的）声明的依赖，**不含传递依赖**。因此不需要网络、不会因为仓库不可达而失败，
     *    也不会把「A 依赖 B、B 依赖 C」误报成「A 依赖 C」。
     *
     * 2. **只取「声明桶」配置**（`!canBeResolved && !canBeConsumed`）。
     *    理由：AGP 会生成大量**可解析**的配置（`debugCompileClasspath`、`debugUnitTestRuntimeClasspath`…），
     *    它们的内容依赖「本次请求了哪些任务」，会让输入集不确定——实测同一份代码
     *    「单跑 checkModuleDependencies」是 101 条边，「经 build 触发」是 102 条。
     *    而人写的依赖一律落在声明桶里（实测：`implementation`、`api`、`ksp` 都是
     *    `resolved=false consumed=false`，`ksp("…hilt-android-compiler")` 与
     *    `compose-bom` 都在其中），所以过滤后既不丢东西，也不再受任务图影响。
     *
     *    残留的不确定性：Kotlin 插件会给 `:core:domain` 的 `api` 桶自动补一条
     *    `org.jetbrains.kotlin:kotlin-stdlib`，补与不补取决于编译任务是否被配置。
     *    实测两次调用的差异**仅此一条**，且它既不是 feature 也不是框架依赖，不影响任何判定。
     *
     * 3. **丢弃自引用边**：AGP 会给模块**自己**加一条依赖，让本模块的类对自己的测试可见。
     *    实测出现在 `debugUnitTest*` / `debugAndroidTest*` 的可解析配置里——第 2 条过滤
     *    本已把它们排除，这里再挡一次是防御：万一将来它出现在声明桶里，
     *    会把每个模块都误报成「依赖另一个 feature」。
     */
    private fun Project.collectDependencyEdges(): List<String> = allprojects
        .filter { it != rootProject }
        .flatMap { module ->
            val from = module.path
            module.configurations
                .filter { configuration -> !configuration.isCanBeResolved && !configuration.isCanBeConsumed }
                .flatMap { configuration -> configuration.dependencies }
                .mapNotNull { dependency ->
                    when (dependency) {
                        is ProjectDependency -> dependency.path
                            // 剔除 AGP 为测试变体加的自引用边（见上方说明）
                            .takeIf { target -> target != module.path }
                            ?.let { target ->
                                "$from${CheckModuleDependenciesTask.EDGE_SEPARATOR}" +
                                    "${CheckModuleDependenciesTask.PROJECT_PREFIX}$target"
                            }

                        else -> dependency.group?.let { group ->
                            "$from${CheckModuleDependenciesTask.EDGE_SEPARATOR}$group:${dependency.name}"
                        }
                    }
                }
        }
        .distinct()
        .sorted()
}
