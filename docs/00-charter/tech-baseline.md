# 技术基线

- 状态: **草稿 v0.2**（版本已按 2026-10-02 实测/核实结果回填）
- 最后更新: 2026-10-02
- 版本策略: 用户要求「用最新的」

> 本文件记录**版本与选型的权威值**。`gradle/libs.versions.toml` 是唯一的机器可读版本来源；
> 本文件说明**为什么选它**。更换任何一项都必须新增 ADR。

## 版本状态约定

本表区分两种状态，**不允许混淆**：

| 标记 | 含义 |
|---|---|
| ✅ **已核实** | 已从官方发布页读取确认，附来源链接 |
| ⬜ **待锁定** | 尚未核实。**T-001 建立 `libs.versions.toml` 时必须逐项到官方发布页核实后填入**，并把核实结果与日期回填到本文件 |

> **为什么不全填上**：凭记忆或推测填写版本号会让基线看起来完整但实际不可信。
> 一个错的版本号会导致 T-001 直接构建失败，而排查成本远高于核实成本。

## 版本

| 项 | 版本 | 状态 | 来源 / 备注 |
|---|---|---|---|
| Kotlin | **2.4.20** | ✅ | [Kotlin releases](https://kotlinlang.org/docs/releases.html)；2026-09-07 发布，2.4 release line，安全支持至 2027-12-03。下一版 2.5.0 计划 2026-12 |
| AGP | **9.4.0** | ✅ | [AGP 9.4.0 release notes](https://developer.android.google.cn/build/releases/agp-9-4-0-release-notes?hl=en)；2026-09 发布 |
| Gradle | **9.8.0** | ✅ | 由 `services.gradle.org/versions/current` 解析所得，已实际下载并用于构建。Wrapper 已生成 |
| JDK | **21**（本机 21.0.1 LTS） | ✅ | T-001 实测：Gradle 9.8.0 + Kotlin 2.4.20 + `jvmToolchain(21)` 构建通过 |
| compileSdk | **37**（且 `compileSdkMinor = 0`） | ✅ | 平台目录为 `platforms\android-37.0`（其 `AndroidVersion.ApiLevel` 是字符串 `"37.0"`）。**下限由依赖决定**：Compose BOM 2026.09.00 的 `ui-android:1.12.1` 要求消费者以 API 37+ 编译。T-002 第二档实测 |
| targetSdk | **36** | ✅ | **刻意低于 compileSdk**：compileSdk 只决定「能调哪些 API」，targetSdk 决定「运行时行为按哪个版本走」，而本机只有一台用户真机、没有可复现测试环境。理由与复审条件见 `ADR-0003` 决策 3 |
| minSdk | **26** | 🔸 建议 | Android 8.0。选它的理由：免去大量兼容分支；若 Q-002 采用地理围栏，26 起 `GeofencingClient` 行为一致。**若你的目标设备更旧，请提出** |
| Compose BOM | **2026.09.00** | ✅ | 核实方式：`dl.google.com/.../androidx/compose/compose-bom/maven-metadata.xml` 取最高稳定版。Compose 各库版本由 BOM 统一约束 |
| Hilt | **2.60.1** | ✅ | 版本来源 `repo1.maven.org/.../hilt-android/maven-metadata.xml`。**T-002 第二档实测通过**：`:app` 的 `hiltAggregateDepsDebug` / `transformDebugClassesWithAsm` 正常执行。Hilt Gradle 插件自 **2.59.0** 起要求 AGP 9 |
| Room | **2.8.5** | ✅ | 版本来源 `dl.google.com/.../room-runtime/maven-metadata.xml`；room-compiler 自 2.3.0-beta02 起支持 KSP。**T-002 第二档在仓库外一次性工程实测**：KSP 生成 `<Dao>_Impl.kt` / `<Database>_Impl.kt` 并导出 schema JSON |
| KSP | **2.3.12** | ✅ | **KSP 自 2.3.0 起改为独立版本线，不再带 Kotlin 前缀。** Google 的 AGP 9 升级指南要求内置 Kotlin 下 KSP ≥ **2.3.6**，否则不可用。T-002 第二档实测通过（Room 代码生成见 `build.md` 的验证配方） |
| kotlinx-coroutines | **1.11.0** | ✅ | `repo1.maven.org/.../kotlinx-coroutines-core/maven-metadata.xml`。本轮仅在 `:core:testing` 使用（`coroutines-test`） |
| kotlinx-serialization | **1.11.0**（运行时） | ✅ | 运行时常量；编译器插件版本跟随 Kotlin（2.4.20）。**本轮无消费者，未引入依赖** |
| Retrofit / OkHttp | **3.0.0** / **5.5.0** | ✅ | 已核实版本但**本轮未引入**：v1 无后端（先用 Mock），无消费者的依赖只增加攻击面与体积 |
| Coil | **3.6.3** | ✅ | 已核实版本但**本轮未引入**：尚无图片加载需求 |
| androidx.datastore | **1.2.1** | ✅ | 已核实版本但**本轮未引入**：尚无需要持久化的偏好项 |
| androidx.navigation | **2.10.2** | ✅ | 已核实版本但**本轮未引入**：出现第二个页面时才需要 |
| detekt | **1.23.8**（稳定线） | ⚠️ **未验证兼容性** | 2.x 只有 `2.0.0-alpha.6`（group 换成 `dev.detekt`）。1.23.8 是否兼容 Kotlin 2.4.20 / Gradle 9.8 **未实测**；归 T-003 |
| Konsist | **0.17.3** | ⚠️ **未验证兼容性** | 用于 R2/R5/R6 架构断言。是否兼容 Kotlin 2.4.20 **未实测**；归 T-003 |

> 🔸 **建议** 表示这是我给出的默认值而非官方事实，你随时可以推翻。

## ⚠️ 关于 AGP 9.x 的风险提示

AGP 从 8.x 跨到 9.x 是**大版本变更**，[官方有 DSL/API 迁移时间表](https://developer.android.google.cn/build/releases/gradle-plugin-roadmap)。
风险与对策：

| 风险 | 对策 | 当前状态 |
|---|---|---|
| 大量构建脚本 DSL 已废弃或移除 | T-001 以 AGP 9.4.0 官方文档为准写构建脚本，**不照搬任何 AGP 8 时代的模板** | ⚠️ **已实际发生**：`org.jetbrains.kotlin.android` 插件在 AGP 9.0 起不再需要，加上即构建失败 |
| 生态插件（Hilt/Konsist/detekt）可能尚未适配 9.x | 逐个验证可用性；不兼容的降级并在 ADR 中记录 | ✅ Hilt 2.60.1 / KSP 2.3.12 / Room 2.8.5 已实测通过（T-002 第二档）；⚠️ Konsist / detekt **仍未实测**（T-003） |
| 「用最新」与「生态兼容」冲突 | **以能构建通过为准**。若某插件卡住，退回其支持的 AGP 版本并新增 ADR | — |
| **仓库路径含非 ASCII 字符** | 已迁移至 `D:\code\Android\jizhangbao`，见 `ADR-0002` | ✅ 已解决 |

> 我的建议：**AGP 取 9.x 最新，但若 T-001 发现关键插件不兼容，允许把 AGP 降到该插件支持的最高版本**——
> 这属于「规则缺陷」而非「违规」，走 ADR 记录即可（见 AGENTS.md 第 4 节「发现违规时怎么办」）。

## 选型

| 关注点 | 选型 | 备选 | 决策 ADR |
|---|---|---|---|
| UI | Jetpack Compose | XML View | ADR-0001 |
| 依赖注入 | Hilt | Koin / 手写 | ADR-0001 |
| 网络 | Retrofit + OkHttp | Ktor | ADR-0001（v1 仅 Mock） |
| 本地存储 | Room | SQLDelight / DataStore | ADR-0001 |
| 键值存储 | DataStore | SharedPreferences | ADR-0001 |
| 序列化 | kotlinx.serialization | Moshi | ADR-0001 |
| 图片 | Coil | Glide | ADR-0001 |
| 异步 | Coroutines + Flow | RxJava | ADR-0001 |
| 定位 | 待定（FusedLocationProvider / Geofencing） | — | 见 Q-002，届时新增 ADR |
| 构建约定 | `build-logic` 约定插件 | 直接写在各模块 | ADR-0001 |
| 静态检查 | detekt | ktlint | ADR-0001 |
| 架构校验 | Konsist | 自定义 Gradle 任务 | ADR-0001 |

## 模块命名空间

| 模块 | namespace / 包名 |
|---|---|
| `:app` | `com.jizhangbao.app` |
| `:core:domain` | 纯 JVM，无 namespace |
| `:core:ui` | `com.jizhangbao.core.ui` |
| `:core:data` | `com.jizhangbao.core.data` |
| `:core:common` | `com.jizhangbao.core.common` |
| `:core:testing` | `com.jizhangbao.core.testing` |
| `:feature:<context>` | `com.jizhangbao.<context>` |

- 应用名: **记账宝**
- applicationId: **`com.jizhangbao.app`**（`applicationId` 一旦上架不可更改，若 Q-007 决定上架请先确认）
- 限界上下文清单见 `docs/20-domain/context-map.md`（草稿）

## 依赖准入规则

新增任何第三方依赖前必须确认：

- [ ] 新增 ADR 说明理由与被否方案
- [ ] 检查许可证（是否与项目分发方式兼容）
- [ ] 评估包体积影响（记录 Release 体积变化）
- [ ] 确认是否有可替代的平台原生能力
- [ ] 记录到 `libs.versions.toml`，禁止在模块里写死版本号

## 工具链要求

| 工具 | 版本 | 说明 |
|---|---|---|
| Android Studio | ⬜ | 需支持 AGP 9.4.0 |
| JDK | ⬜ | T-001 核实 |
| Git | 已就绪 | 仓库已初始化 |

## 构建命令速查

> 下列命令自 T-002 第二档起可用（模块骨架已就绪）。

```bash
./gradlew assembleDebug lintDebug testDebugUnitTest         # 提交前门禁
./gradlew verifyDomainPurity checkModuleDependencies        # 架构规则（T-003 建立，尚未可用）
./gradlew :feature:<context>:test                           # 单模块测试
./gradlew :app:assembleRelease                              # 发布构建（签名配置见 release.md）
./gradlew buildEnvironment                                  # 核实生效的 Kotlin 版本
```

## 核实记录

| 日期 | 核实人 | 内容 |
|---|---|---|
| 2026 | Agent | Kotlin 2.4.20、AGP 9.4.0 从官方发布页确认 |
| 2026 | Agent | Gradle 9.8.0 由官方 versions API 解析并实际下载；JDK 21 经 T-001 实测可用 |
| 2026 | Agent | `:core:domain` 构建通过，14 个测试全绿，`compileClasspath` 中 Android 条目数为 **0**（R3 在依赖层面得证） |
| 2026-10-02 | Agent | **T-002 第二档版本核实**：Compose BOM 2026.09.00、Room 2.8.5、Hilt 2.60.1、coroutines 1.11.0、serialization-json 1.11.0、Retrofit 3.0.0、OkHttp 5.5.0、Coil 3.6.3、datastore 1.2.1、navigation 2.10.2、junit 4.13.2。核实方式：读取官方仓库 `maven-metadata.xml` 后取最高稳定版（排除 alpha/beta/rc/dev）。 |
| 2026-10-02 | Agent | **KSP 的关键约束**：KSP 自 `2.3.0` 起改用独立版本线（不再形如 `2.2.21-2.0.5`）；Google 官方 AGP 9 升级指南明确要求内置 Kotlin 下 KSP ≥ `2.3.6`。据此选 **2.3.12** |
| 2026-10-02 | Agent | **detekt / Konsist 兼容性仍未验证**：detekt 2.x 仅到 `2.0.0-alpha.6`（group 改为 `dev.detekt`），稳定线仍是 `1.23.8`。二者是否兼容 Kotlin 2.4.20 + Gradle 9.8 未实测，归 T-003 |
| 2026-10-02 | Agent | **T-002 第二档构建验证**：11 个模块全部构建通过，`:app:assembleDebug` 产出 11.65 MB APK（`versionCode=1` / `versionName=0.1.0` / `minSdk=26` / `targetSdk=36` / `compileSdk=37` / 启动 Activity `com.jizhangbao.app.MainActivity`）；`lintDebug` 0 error / 2 warning；`:core:domain:test` 强制重跑 14 个测试 0 失败 |
| 2026-10-02 | Agent | **生效的 Kotlin 版本实测**：`./gradlew buildEnvironment` 显示 `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10 -> 2.4.20`。AGP 9.4.0 自身依赖 KGP **2.2.10**，是**根构建脚本里 `apply false` 的 Kotlin 插件别名**把它顶到 2.4.20。该声明是承重的，见 ADR-0003 决策 2 |
| 2026-10-02 | Agent | **compileSdk 修正**：T-001/T-002 第一档记录的「SDK 中 platform 最高只有 36」是**当时未安装**而非不存在；`sdkmanager --list` 有 `android-37.0/37.1/37.2`，已装 `platforms;android-37.0` |
