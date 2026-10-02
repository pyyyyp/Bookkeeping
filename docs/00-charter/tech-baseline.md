# 技术基线

- 状态: **草稿 v0.1 · 待评审**
- 最后更新: 2026
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
| compileSdk | | ⬜ | 取 AGP 9.4.0 支持的稳定 API level |
| targetSdk | | ⬜ | **受应用商店要求约束**，T-001 核实当年上架要求（取决于 Q-007 是否上架） |
| minSdk | **26** | 🔸 建议 | Android 8.0。选它的理由：免去大量兼容分支；若 Q-002 采用地理围栏，26 起 `GeofencingClient` 行为一致。**若你的目标设备更旧，请提出** |
| Compose BOM | | ⬜ | 核实 [Compose 发布说明](https://developer.android.google.cn/jetpack/androidx/releases/compose) |
| Hilt | | ⬜ | 核实 [Hilt 发布说明](https://developer.android.google.cn/jetpack/androidx/releases/hilt) |
| Room | | ⬜ | 核实 [Room 发布说明](https://developer.android.google.cn/jetpack/androidx/releases/room) |
| kotlinx-coroutines | | ⬜ | 需与 Kotlin 2.4.20 匹配 |
| kotlinx-serialization | | ⬜ | 需与 Kotlin 2.4.20 匹配；注意 2.3.10 曾修复过 serialization 竞态，选 ≥ 该线 |
| Retrofit / OkHttp | | ⬜ | T-001 核实 |
| Coil | | ⬜ | T-001 核实 |
| detekt | | ⬜ | T-001 核实 |
| Konsist | | ⬜ | 用于 R2/R5/R6 架构断言，T-001 核实 |

> 🔸 **建议** 表示这是我给出的默认值而非官方事实，你随时可以推翻。

## ⚠️ 关于 AGP 9.x 的风险提示

AGP 从 8.x 跨到 9.x 是**大版本变更**，[官方有 DSL/API 迁移时间表](https://developer.android.google.cn/build/releases/gradle-plugin-roadmap)。
风险与对策：

| 风险 | 对策 |
|---|---|
| 大量构建脚本 DSL 已废弃或移除 | T-001 以 AGP 9.4.0 官方文档为准写构建脚本，**不照搬任何 AGP 8 时代的模板** |
| 生态插件（Hilt/Konsist/detekt）可能尚未适配 9.x | T-001 逐个验证可用性；不兼容的降级并在 ADR 中记录 |
| 「用最新」与「生态兼容」冲突 | **以能构建通过为准**。若某插件卡住，退回其支持的 AGP 版本并新增 ADR |

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

> 下列命令在 **T-001 建立 Gradle 工程之后**才可用。

```bash
./gradlew detekt lintDebug testDebugUnitTest assembleDebug   # 提交前门禁
./gradlew verifyDomainPurity checkModuleDependencies         # 架构规则（T-002 建立）
./gradlew :feature:<context>:test                            # 单模块测试
./gradlew :app:assembleRelease                               # 发布构建
```

## 核实记录

| 日期 | 核实人 | 内容 |
|---|---|---|
| 2026 | Agent | Kotlin 2.4.20、AGP 9.4.0 从官方发布页确认 |
| 2026 | Agent | Gradle 9.8.0 由官方 versions API 解析并实际下载；JDK 21 经 T-001 实测可用 |
| 2026 | Agent | `:core:domain` 构建通过，14 个测试全绿，`compileClasspath` 中 Android 条目数为 **0**（R3 在依赖层面得证） |
| | | ⬜ 其余版本（Compose BOM / Hilt / Room / compileSdk / targetSdk）待 T-002 核实后追加 |
