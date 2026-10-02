# ADR-0004 架构校验的执行方式与工具选型（Konsist 接受、detekt 待裁决）

- 状态: **已接受**（其中「detekt 版本」一节**待用户裁决**，其余已生效）
- 日期: 2026-10-02
- 决策者: 用户 + Agent
- 相关: **ADR-0001**（原定 Konsist + detekt）、T-003、T-004、`docs/30-architecture/module-graph.md`

> ADR **只追加不改写**。本文不推翻 ADR-0001 的「用机器强制架构」目标，
> 只修正其中**用什么工具、在哪个层面强制**这两点。

## 背景

T-003 的目标是把 R1–R12 从「文档里的规则」变成「违规就构建失败」。
动手前先核实工具，结果与 ADR-0001 写下的假设有三处不符，每一处都有实测证据：

| # | 事实 | 证据 |
|---|---|---|
| 1 | **detekt 1.23.8 在 AGP 9 内置 Kotlin 下等于没装** | 其 Gradle 插件注册 Android 任务的唯一入口是 `plugins.withId("kotlin-android") { DetektAndroid(...) }`；而 AGP 9 内置 Kotlin 时该插件**不存在也不能应用**（应用即构建失败）。后果是**静默**的：构建照常成功，只是 `detekt<Variant>` / `detektMain` / `detektTest` 任务从来没被创建 |
| 2 | **detekt 稳定线不支持 Gradle 9 / Kotlin 2.4** | 1.23.8 对应 Gradle 8.12.1 / Kotlin 2.0.21 / AGP 8.8.1；1.x 已 EOL（维护者明确不再发 1.x）。唯一覆盖 Gradle 9 + Kotlin 2.4 + AGP 9 内置 Kotlin 的是 **`2.0.0-alpha.6`**（构建于 Kotlin 2.4.10 / Gradle 9.6.1 / AGP 9.3.1），且带已知未修问题：类型解析看不到生成类（#9402）、Gradle 9.7+ 弃用告警（#9742） |
| 3 | **Konsist 0.17.3 可用，但吃自己的编译器** | 无 Gradle 插件、不需要 Kotlin Gradle 插件（纯 `testImplementation`），实测在本仓库解析出 18 文件 / 15 类。内嵌 `kotlin-compiler-embeddable:2.0.21`；**一旦被顶到 2.4.x，对任何文件都报 `Failed to parse Kotlin file`**，没有「升级编译器」这条路 |
| 4 | **模块图规则的唯一事实源是 Gradle** | 「谁依赖谁」可以来自约定插件、版本目录、platform、变体规则。从源码文本里猜既慢又会错；直接读 `configuration.dependencies` 精确且零成本 |

## 决策

### 1. 规则分两处执行，各管各的，不重复

| 规则 | 执行点 | 为什么是它 |
|---|---|---|
| R2 / R7 / R8 | `checkModuleDependencies`（Gradle 任务） | 管的是**依赖声明**：`feature` 互不依赖、`core` 不依赖 `feature`、只有 `:app` 能组装 feature。这是模块图事实，只有 Gradle 知道 |
| R3（源码） | `verifyDomainPurity`（Gradle 任务） | `:core:domain` 靠模块类型天然免疫，但 **feature 模块内部的 `domain` 包住在 Android Library 里**，`import androidx.room.Entity` 是能编译过的——必须逐文件扫 |
| R3（依赖面） | `checkModuleDependencies` | 源码 import 干净不等于依赖干净：往 `:core:domain` 的 dependencies 里加一行 androidx，在真正用上之前不会有任何 import |
| R2 / R5 / R6 / R8 / R10 的**源码引用**面 | Konsist 断言（`ArchitectureTest`） | 管的是**类型引用**：跨上下文 import、presentation 碰 data、domain 被外部模型污染。Gradle 看不到「通过传递依赖把对方的类拉进来」这类问题 |

**R3 只有一处实现**（`verifyDomainPurity`），刻意不在 Konsist 里再写一遍：
同一规则两处实现必然分叉，然后你会不知道该信哪一个。

### 2. 接受 Konsist 0.17.3，并把它的编译器版本钉死

- 依赖只加在 `:core:testing` 的 **test** 源集（它带来约 50 MB 的 `kotlin-compiler-embeddable`，绝不能进任何会打进 APK 的配置）。
- **禁止**任何依赖或约束把 `org.jetbrains.kotlin:kotlin-compiler-embeddable` 顶到 2.4.x；
  **禁止**把 `dev.detekt:detekt-test` 放到同一个测试 classpath（它带另一套编译器）。
- **不许用 Konsist 的 `assertTrue/assertFalse` 作为唯一防线**：空作用域会**静默通过**。
  本项目改为「自己收集违规 → JUnit 断言列表为空」，并额外写一条**扫描范围不能为空**的守卫测试。

### 3. 架构断言必须声明跨模块输入（否则会被静默跳过）

`ArchitectureTest` 读的是别的模块的源码，而那些文件不在 `:core:testing` 的任何 source set 里。
不声明的话 Gradle 认为「输入没变」→ **`testDebugUnitTest UP-TO-DATE` → 断言根本没跑**。

因此 `core/testing/build.gradle.kts` 里把「会被扫描的源码」显式声明为该测试任务的输入。
**这不是优化，是正确性所必需**：删掉它，注入违规后构建仍然是 BUILD SUCCESSFUL。

（这条是实测撞出来的，不是推演：反向验证第一次就出现了
`> Task :core:testing:testDebugUnitTest UP-TO-DATE` / `BUILD SUCCESSFUL`，而当时正有 4 个违规文件躺在别的模块里。）

### 4. **暂不引入 detekt** —— 待用户裁决

- `1.23.8`（group `io.gitlab.arturbosch.detekt`）**不可用**：见背景表 #1、#2。
- `2.0.0-alpha.6`（group `dev.detekt`）**技术上可用但是预发布**，且：
  - 类型解析在 AGP 9 内置 Kotlin 下看不到生成类（`BuildConfig` / `R` / KSP 产物，#9402 未修）；
  - 在依赖普通 Kotlin/JVM 模块的 Android 模块上有 Gradle 9.7+ 弃用（#9742）——本项目**正好**是这种结构（`:app` 依赖 `:core:domain`）；
  - 厂商覆盖的是 Gradle 9.6.1 / AGP 9.3.1 / Kotlin 2.4.10，而我们是 9.8.0 / 9.4.0 / 2.4.20。
- **引入新的第三方依赖、以及采用预发布版本，属于必须问用户的决定**（`AGENTS.md` 第 10 节）。
  因此本轮**不引入**，把选项与代价摆出来让用户选；在此之前，
  `AGENTS.md` 阶段 E 门禁里的 `detekt` 一项**无法满足**，这一点必须显式记录而不是假装门禁是绿的。

## 备选方案

| 方案 | 优点 | 缺点 | 未采用原因 |
|---|---|---|---|
| **只靠 Konsist 做全部规则（含 R2/R7/R8）** | 一处实现，工具统一 | 模块图要从源码/路径反推，会漏掉依赖声明层面的违规；Konsist 的模块 API 还需要它自己的插件或 `konsist-modules.json` | 模块图的权威数据在 Gradle，不在源码文本里 |
| **只靠自定义 Gradle 任务，不引入 Konsist** | 零第三方风险，全部规则可反向验证 | 包与包之间的引用规则要自己写文本解析，且拿不到 PSI；Konsist 是 ADR-0001 已选定的工具 | 放弃了类型级分析能力，且与 ADR-0001 冲突 |
| **把 detekt 钉在 1.23.8 并降 AGP 到 8.x** | detekt 生态完整、稳定 | 直接违反「Compose / Room 不可妥协」与用户「用最新」；且 1.x 已 EOL | ADR-0001 的降级路径是为「插件不兼容」准备的，但这里**不需要**降级也能达到同样目标（架构强制已由任务 + Konsist 完成） |
| **用 `dev.detekt:detekt-cli:2.0.0-alpha.6` 通过 `JavaExec` 跑** | 绕开 Gradle 插件的兼容问题，官方文档化的用法 | 仍然用预发布；默认 light 模式（无类型解析）；需要自己接 `check`、报告与缓存 | 不解决「预发布」这个真正的风险，只绕开了插件层；作为**待裁决选项**保留 |
| **暂不引入任何静态风格检查** | 零风险，本轮范围最小 | `AGENTS.md` 的门禁里列了 detekt，缺口必须显式记录 | 这就是本轮的现状，但它是**显式记录的缺口**，不是被忽略的东西 |
| **依赖 Konsist 自带的 assert 帮助函数** | 代码更短，自带声明位置与超链接 | 空作用域静默通过；`strict=true` 的空列表还会抛两种不同异常 | 自己收集违规 + JUnit 断言给出更可控的失败信息，并显式加空范围守卫 |

## 后果

**正向**

- R2 / R3 / R7 / R8 从「口头约定」变成**构建失败**，且每条都用反向验证证明过会失败。
- 两条 Gradle 任务零第三方依赖、不 resolve 配置（不联网）、不依赖源码文本解析，因此快且不会因网络失败。
- 源码引用面（R2/R5/R6/R8/R10）由 Konsist 覆盖，补上了 Gradle 看不到的那一半。
- 首次构建出 `.kt` 违规时，报错信息里直接给**文件路径 + 行号 + 命中的前缀/规则**。

**负向**（必须写）

- **Konsist 处于维护模式**：最后一次发布 2024-12，对 Kotlin 2.4 语法没有前向兼容承诺。
  当前能跑通，但它是这套校验里最可能先坏的一环。
- **`kotlin-compiler-embeddable` 被钉死在 2.0.21** 是一个隐形约束：将来任何人为了「统一版本」
  加一条 resolution strategy，架构断言会**整体**失效（而且报错很难懂：`Failed to parse Kotlin file`）。
- **`detekt` 缺口仍在**：`AGENTS.md` 阶段 E 的门禁目前无法全绿，只有显式记录。
- 跨模块输入声明是**手工维护的路径模式**（`*/src/main/kotlin/**`、`*/*/src/main/kotlin/**`）：
  将来若有模块布局变化（例如把模块移到更深一层），断言会退化成「扫不到文件」。
  已用「扫描范围不能为空」的守卫测试兜底，但守卫只看 `core/domain` 是否存在。
- `checkModuleDependencies` 的输入集仍有一条不稳定项：Kotlin 插件会给 `:core:domain` 的 `api`
  桶自动补一条 `kotlin-stdlib`，补与不补取决于本次请求了哪些任务（实测 51 vs 52 条边）。
  它不影响任何判定（既不是 feature 也不是框架依赖），但日志里的条数会变。

**影响面**

| 受影响对象 | 具体影响 |
|---|---|
| 代码 | 新增 `build-logic/.../VerifyDomainPurityTask.kt`、`CheckModuleDependenciesTask.kt`、`ArchitectureVerificationPlugin.kt`、`core/testing/src/test/.../ArchitectureTest.kt` |
| 文档 | `module-graph.md`（规则状态）、`60-runbooks/build.md`（校验命令与陷阱）、`tech-baseline.md`（Konsist / detekt 行） |
| 测试 | 新增 6 个架构断言（含 1 个防空洞守卫）；它们对**所有**业务模块的源码变化敏感 |
| 构建/CI | 每个模块的 `check` 依赖两条校验任务；`./gradlew build` 自动执行；CI 在 T-004 显式调用 |
| 用户 | 无直接影响；但 `detekt` 缺口需要用户选一个方向 |

## 复审条件

- **用户对 detekt 作出裁决**（引入 alpha / 等 GA / 用 CLI / 不要）→ 更新本文并去掉缺口记录。
- detekt 2.0.0 发布 GA 且支持 Gradle 9.8 + Kotlin 2.4.20 → 重新评估，可能恢复 ADR-0001 的原计划。
- Konsist 发布新版或明确支持 Kotlin 2.4 → 重新评估钉死的编译器版本与残余风险。
- 出现模块布局变化导致扫描范围失效 → 本条与守卫测试一起重写。
- R5 / R6 / R10 的断言在真实业务代码落地后**首次**被触发 → 复核它们的判据是否过宽（误报）或过窄（漏报）。
