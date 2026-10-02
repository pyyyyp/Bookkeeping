# ADR-0003 AGP 9 构建基线落地（内置 Kotlin、约定插件、compileSdk 37）

- 状态: **已接受**
- 日期: 2026-10-02
- 决策者: 用户 + Agent
- 相关: ADR-0001（技术栈与工程结构）、ADR-0002（非 ASCII 路径）、T-002、Q-007、Q-020

> ADR **只追加不改写**。决策变了就新增一条，并在旧的那条上标注「被 ADR-xxxx 取代」。

## 背景

T-002 第二档要把 11 个模块真正建起来并产出 APK。ADR-0001 当时预设的风险是
「AGP 9.4.0 太新，Hilt / Konsist / detekt 可能不兼容，不兼容就把 AGP 降级」。

**实际发生的是另一回事**：没有任何插件不兼容，但 AGP 8 时代的知识几乎全部失效。
下面每一条都是本轮实测（编译通过/失败）或读 AGP 9.4.0 已发布字节码得到的事实：

| # | 事实 | 证据 |
|---|---|---|
| 1 | 生效的 Kotlin 由**根构建脚本的插件别名**决定，而不是 `libs.versions.toml` 里那个数字 | `buildEnvironment` 实测 `kotlin-gradle-plugin:2.2.10 -> 2.4.20`。AGP 9.4.0 自己依赖 KGP **2.2.10**；根 `build.gradle.kts` 里 `apply false` 的 Kotlin 插件（2.4.20）把它顶了上去 |
| 2 | Compose BOM 2026.09.00 要求 **compileSdk ≥ 37**，而文档一直记着「SDK 最高只有 36」 | 构建报错：`Dependency 'androidx.compose.ui:ui-android:1.12.1' requires ... version 37 or later`。`sdkmanager --list` 实际有 `platforms;android-37.0/37.1/37.2` |
| 3 | 应用了 Compose 插件后**不能**再写 `buildFeatures { compose = true }` | AGP 9.4 字节码：`compose = pluginApplied \|\| buildFeatures.compose`，插件已应用时该开关被忽略并告警，提示「Remove android.buildFeatures.compose flag」 |
| 4 | 块式 DSL（`defaultConfig {}` / `compileOptions {}` / `buildFeatures {}`）**只声明在具体接口上**，`CommonExtension` 上只有属性访问器；且 `CommonExtension` 在 AGP 9 已**不再是泛型接口** | 对 AGP 9.4.0 `gradle-api` 做 `javap`：`LibraryExtension`/`ApplicationExtension` 各有 25 个 `Function1` 块式方法，`CommonExtension` 只有 `compileSdk(Function1<CompileSdkSpec>)` 一个 |
| 5 | Room 拒绝空的实体表 | `e: [ksp] @Database annotation must specify list of entities` |
| 6 | KSP 自 `2.3.0` 起改用独立版本线，且内置 Kotlin 下必须 ≥ `2.3.6` | Google 官方 AGP 9 升级指南；`maven-metadata.xml` 版本序列从 `2.2.21-2.0.5` 直接跳到 `2.3.0` |
| 7 | Hilt Gradle 插件要求 **AGP 9**（2.59.0 起）；2.60.1 可用 | `hiltAggregateDepsDebug` / `transformDebugClassesWithAsm` 实测通过 |

约束：本项目自用侧载（Q-007），单人开发，没有真机自动化测试环境；用户要求「用最新的」。

## 决策

### 1. AGP 继续用 9.4.0，**不降级**

ADR-0001 预设的降级路径**不触发**：KSP 2.3.12、Hilt 2.60.1、Room 2.8.5、
Compose 编译器 2.4.20 在 AGP 9.4.0 上全部实测可用。
降级才会真正破坏架构（Compose / Room 是非妥协项），而升级只需要按新规则写脚本。

### 2. Kotlin 版本 = 2.4.20，并**明确规定它由根构建脚本的插件别名承载**

根 `build.gradle.kts` 中 `alias(libs.plugins.kotlin.*) apply false` 的声明是**承重的**：
删掉它们，AGP 内置 Kotlin 会**静默**退回 2.2.10 —— 编译仍然成功，但基线文档说的
2.4.20 就成了假话。因此：

- 该文件里加了醒目注释说明这条依赖关系；
- 不采用「在 `buildscript { classpath(...) }` 里再写一份 Kotlin/KSP 版本」的做法（见备选方案）。

### 3. compileSdk = 37（`compileSdkMinor = 0`），targetSdk 保持 36

- `compileSdk` 只决定「能调用哪些 API」，必须 ≥ 37 才能用当前 Compose。
- `targetSdk` 决定「运行时行为按哪个版本走」。本机只有一台用户的华为真机
  （HBP-AL00 / Android 12 / API 32），**没有可复现的测试环境**，
  单方面升 `targetSdk` 等于改变运行时行为却无法验证。等真机可测再升。
- SDK 里平台目录是 `platforms\android-37.0`、其 `AndroidVersion.ApiLevel` 是字符串
  `"37.0"`，所以必须同时给 `compileSdk = 37` 与 `compileSdkMinor = 0`，只写 37 找不到平台。

### 4. 引入 `build-logic` 约定插件；共享配置一律用**属性赋值**

两个约定插件：`jizhangbao.android.library`、`jizhangbao.android.application`。
理由：现在是 11 个模块，且 5 个 feature 模块的构建文件只有 3 行——这正是 ADR-0001 决策 7
说的「降低遵守成本本身就是一种架构保障」。

共享配置函数签名取 `CommonExtension`（AGP 9 已非泛型），内部**只做属性赋值**
（`defaultConfig.minSdk = ...`、`compileOptions.sourceCompatibility = ...`），
因为块式 DSL 在 `CommonExtension` 上根本不存在（背景表 #4）。

### 5. 注解处理一律 KSP，不用 kapt

内置 Kotlin 与 kapt 不兼容。Room 2.8.5 与 Hilt 2.60.1 都已支持 KSP。

### 6. 不在 `:core:data` 里编造表结构

Room 拒绝空 `entities`，但为了让编译器闭嘴而编一个「占位表」，
等于把猜出来的领域结构写进**唯一事实源**的 schema —— schema 一旦入库，删表就要写迁移。
因此 Room 的可用性在**仓库外的一次性工程**里验证（KSP 生成 `<Dao>_Impl.kt` /
`<Database>_Impl.kt`、导出 `schemas/<Database>/1.json` 全部实测通过），
仓库内保留 Room/KSP 的装配但**一个 `@Entity` 都不写**，第一个实体随第一个数据层任务卡进来。

## 备选方案

| 方案 | 优点 | 缺点 | 未采用原因 |
|---|---|---|---|
| **把 AGP 降到 8.x** | 生态文档、模板、Stack Overflow 答案全部适用 | 要重写全部构建脚本；Compose BOM 2026.09 与 Room 2.8.5 的新特性可能用不了；违背用户「用最新」 | **没有触发降级的条件**——降级是 ADR-0001 为「插件不兼容」预设的路径，而实测没有不兼容。为一个不存在的问题付代价不划算 |
| **在 `buildscript { classpath }` 里显式覆盖 KGP 2.4.20 + KSP 2.3.12** | 版本意图非常直白，不依赖隐式的冲突解析 | 同一个版本号出现在两处（插件别名 + classpath），必然漂移；且实测**不需要** | 根构建脚本的插件别名已经让 Gradle 把 KGP 解析到 2.4.20（实测）。多写一份只是多一个会过期的地方 |
| **约定插件用 `CommonExtension<*, *, *, *, *, *>`（AGP 8 写法）** | 与旧教程一致 | AGP 9 的 `CommonExtension` 不是泛型了，直接编译不过 | 语言层面不可行 |
| **约定插件里用块式 DSL** | 与模块里的写法一致，读起来熟悉 | 块式方法不在 `CommonExtension` 上，只有在具体接口上 | 属性赋值同样清晰，且 Library / Application 共用一份配置 |
| **compileSdk 保持 36，把 Compose BOM 降到支持 36 的版本** | 不动已核实的环境 | 要放弃「用最新」；且 API 37 平台本来就存在，之前只是没装 | 问题的真实原因是「平台没装」，不是「平台不存在」——修环境而不是降依赖 |
| **给 `:core:data` 放一个占位 `@Entity` 让 Room 编译通过** | 一行代码解决问题，CI 立刻变绿 | 猜的领域结构会进入 schema；删表要写迁移；违反 P5 | 用仓外的验证工程达成同样目的，且不留负债 |
| **不引入 build-logic，每个模块各写一份配置** | 少一层间接，新人一眼看懂 | 11 份重复配置；改一次 `minSdk` 要动 11 个文件；与 ADR-0001 决策 7 相反 | 已过 ADR-0001 定的临界点（≥5 模块） |

## 后果

**正向**：

- 架构的「可强制」从文档变成构建事实：11 个模块的边界、namespace、依赖方向都已可编译、可 lint。
- 改全工程 Android 配置只需动 `AndroidConventions.kt` 一处。
- 新增一个上下文 = 一行 `include` + 一个 3 行的 `build.gradle.kts`。
- Room / Hilt / Compose / KSP 四者的 AGP 9 兼容性**已经用构建结果证明过**，不是「应该没问题」。

**负向**（必须写，写不出说明分析不够）：

- **存在一个静默失效点**：删掉根构建脚本里的 Kotlin 插件别名，Kotlin 会悄悄退回
  AGP 自带的 2.2.10。构建不会失败，只有 `buildEnvironment` 看得出来。这是本次留下的最大隐患。
- `build-logic` 是第二套构建，改动它需要理解 Gradle included build 与约定插件替换规则。
- 共享配置只能用属性赋值，与项目里其他地方（模块内）的块式写法不一致，读的时候要记住这个区别。
- `:core:data` 与 `:core:testing` 目前**没有任何源文件**，它们是「声明了但还没长肉」的模块。
- `targetSdk (36) < compileSdk (37)`，是一个刻意的、需要记着还债的不一致。

**影响面**：

| 受影响对象 | 具体影响 |
|---|---|
| 代码 | 新增 `build-logic/`、根 `build.gradle.kts`；10 个模块的构建文件；`:core:ui` 主题、`:app` 宿主 |
| 文档 | `tech-baseline.md`（compileSdk/minor/版本核实）、`build.md`（AGP 9 规则与 Room 验证配方）、`module-graph.md`（模块状态） |
| 测试 | 领域层 14 个测试仍全绿；Android 侧暂无测试（`:app:testDebugUnitTest` 为 NO-SOURCE） |
| 构建/CI | 首次构建时间因 11 模块 + build-logic 变长；`assembleDebug` 产出 11.65 MB debug APK |
| 用户 | 需要装 `platforms;android-37.0`；APK 可侧载到 API ≥ 26 的设备 |

## 复审条件

- 出现真机/模拟器的自动化测试环境 → 重评 `targetSdk` 是否升到 37。
- AGP 内置 Kotlin 改为跟随根插件版本（不再依赖冲突解析）→ 可去掉那条承重注释与隐患。
- 模块数超过 ~25 个，或约定插件出现第二种变体（如多 flavor）→ 重评 `build-logic` 的划分粒度。
- `detekt` / `Konsist`（T-003）不适配 AGP 9.4.0 → 触发 ADR-0001 的降级路径，届时**必须**新增 ADR。
- Compose / Room 任一的非妥协项被迫改动 → 按 ADR-0001「四项不可妥协」处理。
