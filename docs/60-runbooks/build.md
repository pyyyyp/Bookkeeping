# 构建手册

- 最后更新:

## 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | | |
| Android Studio | | |
| Android SDK | | |
| Git | | |

## 本机环境现状（T-001 实测）

| 组件 | 状态 | 位置 |
|---|---|---|
| JDK 21 (21.0.1 LTS) | ✅ 已装 | `D:\program files\java\jdk21` |
| Gradle 9.8.0 | ✅ 已下载到仓库 `.tools/`（**不入库**） | `.tools\gradle-9.8.0\` |
| Android SDK | ✅ 已装（**不入库**） | `.tools\android-sdk\` |
| ├ cmdline-tools | ✅ build 14742923 | `cmdline-tools\latest\` |
| ├ platform | ✅ | `platforms\android-36` **与** `platforms\android-37.0`（后者 T-002 第二档补装） |
| ├ build-tools | ✅ | `build-tools\36.0.0`、`build-tools\37.0.0` |
| └ platform-tools (adb) | ✅ | `platform-tools\adb.exe` |
| Android Studio | ❌ 未安装 | — |
| 真机（供侧载验证） | ✅ | 华为 HBP-AL00 / Android 12 / **API 32**，经 adb over TCP 连接 |

> **`compileSdk = 37`（且必须同时给 `compileSdkMinor = 0`）**：
> Compose BOM `2026.09.00` 带进来的 `androidx.compose.ui:ui-android:1.12.1`
> 要求消费者以 API 37+ 编译，否则 AGP 直接报错。
> 平台目录名是 `android-37.0`，其 `AndroidVersion.ApiLevel` 是字符串 `"37.0"`——
> 只写 `compileSdk = 37` 会找不到平台。
>
> **历史修正**：T-001/T-002 第一档曾记「SDK 里 platform 最高只有 36」，
> 那是**当时没装**，不是不存在。`sdkmanager --list` 里有 `android-37.0 / 37.1 / 37.2`。
>
> build-tools 版本通常由 AGP 自行决定，不必手动指定。

## ⚠️ AGP 9.x 的破坏性变更（T-002 实测踩到）

| 变更 | 症状 | 正确做法 | 状态 |
|---|---|---|---|
| **内置 Kotlin 支持** | 加上 `org.jetbrains.kotlin.android` 插件后构建直接失败：<br>`The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0` | **移除该插件**。Android 模块只声明 `com.android.library` / `com.android.application`。见 [AGP built-in Kotlin](https://kotl.in/gradle/agp-built-in-kotlin) | ⚠️ 持续适用 |
| **路径非 ASCII 检查** | 仓库曾在 `D:\code\安卓相关`，AGP 在插件应用阶段直接拒绝应用 | 仓库已迁移至 `D:\code\Android\jizhangbao`，`android.overridePathCheck` 已移除 | ✅ 已解决 |
| **kapt 与内置 Kotlin 不兼容** | 应用 `org.jetbrains.kotlin.kapt` 插件即失败 | 改用 **KSP**；确需 kapt 的依赖用 `com.android.legacy-kapt` 插件 | ⚠️ 持续适用 |
| **KSP 版本要求与版本号格式** | KSP < 2.3.6 在内置 Kotlin 下不可用；且 KSP 自 `2.3.0` 起**不再形如 `2.2.21-2.0.5`**，改为独立版本线 | 使用 KSP ≥ **2.3.6**（本项目 `2.3.12`）。来源：Google 官方 AGP 9 升级指南 | ⚠️ 持续适用 |
| **`kotlin("test")` 简写不可用** | Android 模块不再应用 Kotlin Gradle 插件，`kotlin(...)` 这个 DSL helper 不存在 | 写全坐标 `org.jetbrains.kotlin:kotlin-test`（已进 `libs.versions.toml`） | ⚠️ 预期，待实测确认 |
| **Compose 开关与插件冲突** | 应用了 `org.jetbrains.kotlin.plugin.compose` 之后仍写 `buildFeatures { compose = true }`，AGP 告警：该标志「will be ignored: Compose feature will be turned on」 | **不要写** `buildFeatures.compose`。Compose 由插件开启，标志位只在「不用插件」时才有意义 | ✅ 已按此写法构建通过 |
| **块式 DSL 不在 `CommonExtension` 上** | 在约定插件里对 `CommonExtension` 写 `defaultConfig { }` / `compileOptions { }` 会编译不过 | `CommonExtension` 上只做**属性赋值**（`defaultConfig.minSdk = ...`）；块式方法只在 `LibraryExtension` / `ApplicationExtension` 上 | ✅ 已实测（`javap` AGP 9.4.0 `gradle-api`） |
| **`CommonExtension` 不再是泛型** | 照抄 AGP 8 的 `CommonExtension<*, *, *, *, *, *>` 编译不过 | 直接写 `CommonExtension` | ✅ 已实测 |
| **Room 拒绝空实体表** | `e: [ksp] @Database annotation must specify list of entities` | 在写出第一个真实聚合对应的实体之前，**不要建 `@Database`** | ✅ 已实测（见下方验证配方） |
| **Kotlin 版本由根构建脚本决定** | `libs.versions.toml` 里写 2.4.20，但 AGP 自带的 KGP 是 2.2.10 —— 删掉根 `build.gradle.kts` 里的 Kotlin 插件别名，Kotlin 会**静默**退回 2.2.10 | 保留根构建脚本里的 `alias(libs.plugins.kotlin.*) apply false`。核实方式：`./gradlew buildEnvironment`，应看到 `kotlin-gradle-plugin:2.2.10 -> 2.4.20` | ✅ 已实测 |

> **AGP 8 时代的所有构建模板都不能照抄** —— 这是 ADR-0001 中预设的风险，已实际发生。

## SDK 路径配置

仓库已迁移到纯 ASCII 路径 `D:\code\Android\jizhangbao`，
`local.properties` 恢复正常，与 Android Studio 的默认行为一致：

```properties
sdk.dir=D:/code/Android/jizhangbao/.tools/android-sdk
```

> **为什么用正斜杠**：`.properties` 里反斜杠是转义符，写成 `D:\\code\\...` 也对，
> 但正斜杠更不容易出错，Windows 的 Java 能正确识别。
>
> **历史教训**：迁移前仓库在 `D:\code\安卓相关`，中文路径写进 `local.properties`
> 会因 Java 的 ISO-8859-1 默认编码变成 `?`，Gradle 报 `Directory does not exist`。
> 这是 ADR-0002 建议迁移的实证之一。

环境变量方式同样可用（CI 中更常见）：

```powershell
$env:ANDROID_HOME     = "D:\code\Android\jizhangbao\.tools\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

## Gradle 入口：Wrapper 现在可用（2026-10-02 起）

T-001 期间 Wrapper 首次运行**无法**从 `services.gradle.org` 下载发行版（Java 报 Connect timed out）。
**该问题在 2026-10-02 已不能复现**：`.\gradlew.bat --version` 顺利下载并解压 `gradle-9.8.0-bin.zip`。

因此**优先使用 Wrapper**——它也是 `AGENTS.md` 阶段 E 与 CI 使用的入口：

```powershell
.\gradlew.bat :core:domain:build --console=plain
```

**关于当时失败原因的复盘（假设，未证实）**：本机启用了 WinINET 系统代理
（`HKCU:\...\Internet Settings` 中 `ProxyEnable=1`、`ProxyServer=127.0.0.1:7890`）。
PowerShell 读 WinINET 设置，因此走代理；**JVM 不读 WinINET**，只认
`-Dhttp.proxyHost` / `http_proxy` 环境变量，所以当时 Java 走的是直连。
2026-10-02 实测 JVM 对 `dl.google.com`、`repo1.maven.org`、`plugins.gradle.org`、
`services.gradle.org` 四者**直连均可达**，故当前无需任何代理配置。
若日后再次出现「PowerShell 通、Gradle 不通」，先按此方向排查，**在用户级
`~/.gradle/gradle.properties` 里配 `systemProp.https.proxyHost/Port`，不要改仓库文件**
（否则开源的使用者会被迫走你的代理）。

保留本地发行版作为兜底（`.tools/` 已 gitignore，不入库）：

```powershell
# 兜底：绕过 Wrapper，直接用已下载到 .tools 的本地发行版
.\.tools\gradle-9.8.0\bin\gradle.bat :core:domain:build --console=plain
```

> Wrapper 的 `networkTimeout` 已从 10000ms 提到 60000ms、`retries` 提到 3。
> **这是仓库级配置，不是本机 hack。**

若在公司网络内，建议在 `~/.gradle/init.gradle.kts`（**用户级，不入库**）
配置 Maven 镜像，而不是改项目的 `settings.gradle.kts`——
后者会让开源的使用者也被迫走你的镜像。

## 首次搭建

```bash
git clone <repo>
cd <repo>
./gradlew :app:assembleDebug     # 首次会下载 Gradle 发行版、依赖与 build-logic
```

`local.properties` 需自行创建（**不入库**）：

```properties
sdk.dir=<你的 Android SDK 路径>
```

Android SDK 至少需要：`platforms;android-37.0`、`build-tools;37.0.0`、`platform-tools`。
用命令行安装：

```powershell
$env:ANDROID_HOME = "<repo>\.tools\android-sdk"
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --install "platforms;android-37.0"
# 若提示 license 未接受：& $env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat --licenses
```

## 模块骨架与约定插件

构建约定集中在 **`build-logic/`**（一个 Gradle included build），
它导出**三个**约定插件，全工程 10 个 Android 模块都靠它们，改配置只改一处：

| 约定插件 id | 作用 | 使用它的模块 |
|---|---|---|
| `jizhangbao.android.library` | 设 compileSdk 37（minor 0）/ minSdk 26 / Java 21 | `core:common` `core:ui` `core:data` `core:testing` 及 5 个 `feature:*` |
| `jizhangbao.android.application` | 上面全部 + targetSdk 36 + versionCode/versionName | `:app` |
| `jizhangbao.architecture` | 注册 `verifyDomainPurity` / `checkModuleDependencies`，并接入每个模块的 `check` | **根项目**（唯一） |

```kotlin
// 因此一个 feature 模块的完整构建脚本只有 3 行（如 feature/ledger/build.gradle.kts）
plugins { alias(libs.plugins.jizhangbao.android.library) }
android { namespace = "com.jizhangbao.ledger" }
dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
}
```

**约定插件里只做属性赋值，不写块式 DSL** —— 原因见上面 AGP 9 破坏性变更表。

`namespace` 刻意留给各模块自己声明：它同时是架构断言（R2/R6）的判断依据，
显式写出来比从模块路径猜出来更不容易错。

> ⚠️ `:core:domain` **不用**这两个约定插件：它是 `kotlin("jvm")` 模块，
> 没有 Android 类路径，这正是 R3 的强制手段（ADR-0001 决策 4）。

## 常用命令

```bash
# 提交前门禁（四项，AGENTS.md 阶段 E）
./gradlew detekt lintDebug testDebugUnitTest assembleDebug

# 架构规则（R2 / R3 / R7 / R8，零第三方依赖）
./gradlew verifyDomainPurity checkModuleDependencies
# 架构规则（R2 / R5 / R6 / R8 / R10，Konsist 源码断言）
./gradlew :core:testing:testDebugUnitTest

# 单模块
./gradlew :app:assembleDebug
./gradlew :core:domain:build          # 纯 JVM 领域层 + 14 个测试
./gradlew :core:domain:dependencies --configuration compileClasspath   # 核实 R3：Android 条目应为 0

# 核实「生效的 Kotlin 是不是 2.4.20」（应看到 kotlin-gradle-plugin:2.2.10 -> 2.4.20）
./gradlew buildEnvironment
```

## 架构校验：两条任务 + 一组源码断言

`verifyDomainPurity` 与 `checkModuleDependencies` 是**零第三方依赖**的 Gradle 任务
（实现在 `build-logic/convention/src/main/kotlin/.../`），由 `jizhangbao.architecture`
插件注册在根项目上，并接入每个模块的 `check`——因此 `./gradlew build` 会自动执行它们。
同一个插件还统一应用并配置 **detekt**（见下一节）。

| 现象 | 原因 | 处理 |
|---|---|---|
| `verifyDomainPurity` 报「扫描到的 domain 源文件数为 0」 | 扫描规则失效（比如 `domain` 包改名、模块布局变化），**不是**「项目里没有 domain 代码」 | 修 `ArchitectureVerificationPlugin.domainSourceTrees()` 的规则。这条守卫就是**故意**让「扫不到」变成失败——把空扫描当成通过等于给架构一个假绿灯 |
| `checkModuleDependencies` 报某模块「依赖自己」 | AGP 会给测试变体的**可解析**配置加一条自引用依赖 | 已处理：只收集「声明桶」配置（`!canBeResolved && !canBeConsumed`）并显式剔除自引用边 |
| 往 `:core:domain` 的 dependencies 里加 androidx 没被发现 | 只改了依赖、还没 import，源码扫描看不见 | 已由 `checkModuleDependencies` 的 **R3 依赖面**覆盖 |

### ⚠️ 一个会静默吃掉架构断言的陷阱（实测踩过）

`ArchitectureTest` 扫描的是**别的模块**的源码，那些文件不在 `:core:testing` 的任何 source set 里。
**如果不在 `core/testing/build.gradle.kts` 里把它们声明成该测试任务的输入**，
Gradle 会认为输入没变：

```
> Task :core:testing:testDebugUnitTest UP-TO-DATE
BUILD SUCCESSFUL          ← 而当时正有 4 个架构违规文件躺在别的模块里
```

所以那段 `tasks.withType<Test> { inputs.files(...) }` **不是优化，是正确性所必需**，别删。
排查口诀：**只要「校验任务」需要读它所属模块之外的文件，就必须显式声明这些输入。**

## 静态分析：detekt（T-006）

由 `jizhangbao.architecture` 插件统一应用到**所有**模块并接进 `check`，
因此 `./gradlew build` 会自动执行。配置只有一处：`config/detekt/detekt.yml`。

| 事项 | 现状 | 为什么 |
|---|---|---|
| 版本 | `dev.detekt:2.0.0-alpha.6`（**预发布**） | AGP 9 内置 Kotlin 下只有 2.x 能为 Android 模块注册任务；1.23.8 靠 `kotlin-android` 插件注册，在 AGP 9 下**静默失效**（见 `ADR-0004` 决策 4，用户已批准） |
| 分析模式 | **light（不启用类型解析）** | 内置 Kotlin 下类型解析看不到生成类（`BuildConfig` / `R` / KSP 产物，上游 #9402 未修），启用会误报 |
| 应用范围 | 所有模块，不做 `src/` 存在性过滤 | 过滤会让行为取决于**未入库的空目录**，本地与全新 clone 不一致；空模块报 `NO-SOURCE` 是可接受的代价 |
| 配置偏离 | 只允许**有理由**的偏离 | 目前 1 条：Compose 的 `@Composable` 函数必须 PascalCase（官方约定），用 `ignoreAnnotated: ['Composable']` 豁免 |

**首轮就抓到一条真问题**（不是误报）：`Money.kt` 里「一元 = 100 分」的换算散在三处，
已提取为具名常量 `CENTS_PER_YUAN`。

> 判断标准很简单：**默认规则在这里是对的就改代码，错的就改配置并写明理由**。
> 为了让构建变绿而无理由地关规则，比不装 detekt 更糟——它给的是虚假的安全感。
>
> ⚠️ 已知的上游问题：Android 模块依赖普通 Kotlin/JVM 模块时会触发 Gradle 9.7+ 弃用告警
> （上游 #9742），本项目正是这种结构（`:app` → `:core:domain`）。目前只是告警。

## Room 可用性验证（仓库外一次性工程）

Room 拒绝空的实体列表，而第一个实体必须由真实聚合倒推（R6）。
所以 **`:core:data` 里刻意没有 `@Database`**，Room 的可用性改用下面这个
仓库外的一次性工程验证（不污染项目历史，验证完即删）：

```
.roomprobe/
  settings.gradle.kts        # pluginManagement + dependencyResolutionManagement(google, mavenCentral)
  local.properties           # sdk.dir=<repo>/.tools/android-sdk
  lib/build.gradle.kts       # com.android.library 9.4.0 + com.google.devtools.ksp 2.3.12
                             # compileSdk 37 / compileSdkMinor 0 / minSdk 26
                             # ksp { arg("room.schemaLocation", "$projectDir/schemas") }
                             # room-runtime / room-ktx / ksp(room-compiler) 均 2.8.5
  lib/src/main/kotlin/...    # 一个与业务无关的 @Entity + @Dao + @Database(exportSchema = true)
```

运行（用仓库里的 Gradle 发行版，避免再下一次）：

```powershell
& "<repo>\.tools\gradle-9.8.0\bin\gradle.bat" -p .roomprobe :lib:assembleDebug --console=plain
```

**通过标准**（2026-10-02 全部达成）：

- `:lib:kspDebugKotlin` 与 `:lib:compileDebugKotlin` 成功；
- 生成 `lib/build/generated/ksp/debug/kotlin/**/<Dao>_Impl.kt` 与 `<Database>_Impl.kt`；
- 导出 `lib/schemas/<包名>.<Database>/1.json`（含 `createSql` 与 `identityHash`）。


## 构建变体

| 变体 | 用途 | 说明 |
|---|---|---|
| debug | 日常开发 | 可调试，含日志 |
| release | 发布 | R8 全优化 |

## 构建问题排查

| 现象 | 原因 | 处理 |
|---|---|---|
| `SDK location not found` | 缺 `local.properties` | 创建并填 `sdk.dir` |
| 依赖下载失败 | 网络/镜像 | 配置镜像仓库 |
| 编译内存不足 | 默认堆太小 | `gradle.properties` 调 `org.gradle.jvmargs` |
| 增量构建异常 | 缓存损坏 | `./gradlew clean` 或删 `.gradle/` |

## Mock 服务

<若后端未就绪，此处记录 Mock Server 的启动方式与契约来源。>
