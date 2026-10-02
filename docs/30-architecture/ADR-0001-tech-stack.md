# ADR-0001 技术栈与工程结构选型

- 状态: **已接受**（版本细节待 T-001 锁定后修订）
- 日期: 2026
- 决策者: 用户 + AndroidDDD-Agent
- 相关: `docs/00-charter/tech-baseline.md`、Q-007

## 背景

全新项目（无存量代码），单人开发，需要一套能长期演进且**机器可强制**的 Android 工程基线。
用户的明确要求是「技术基线用最新的」，同时要求「离线可用」「先用 Mock」。

关键约束：

1. 单人开发 → 摩擦成本必须低，不能靠人工纪律维持架构
2. 离线优先 → 本地库必须是唯一事实源（SSOT）
3. 文档驱动 + DDD → 领域层必须能脱离 Android 单独测试
4. 「用最新」→ 但最新的大版本常有生态兼容风险

## 决策

### 1. UI：Jetpack Compose

Compose 的 `@Composable` 天然是「输入状态 → 输出界面」的纯函数，与 AGENTS.md 第 13 节的单向数据流
（`Route` 连接 / `Screen` 纯渲染）完全吻合，使「Composable 内不写业务判断」这条规则**可被预览测试验证**。
XML View 的 `findViewById` + 适配器模式会把状态分散到 View 层，与「领域驱动」相悖。

### 2. DI：Hilt

与 `@HiltViewModel`、`SavedStateHandle`、`WorkManager` 的集成是官方路径，
且 `di/` 层集中装配正好落成 AGENTS.md 的 R10（`data` 只被 `di` 使用）。

### 3. 本地存储：Room，且 schema 必须提交进 Git

这是**离线的必要条件**，不是偏好：AGENTS.md 第 12.3 节要求 schema 入库才能写迁移测试。
选 Room 而非 SQLDelight 的理由是 Kotlin/KSP 集成成熟、`MigrationTestHelper` 现成。

### 4. 领域层用纯 Kotlin JVM 模块（`kotlin("jvm")`），而非 Android Library

**这是本 ADR 中最重要的一条。**

AGENTS.md 的 R3 要求 `domain` 层零框架依赖。如果 `domain` 是 Android Library，
这条规则只能靠 lint 事后检查；**做成 `kotlin("jvm")` 模块后，它没有 Android 类路径，
违反 R3 不是「被检查出来」而是「写不出来」**——规则从口头约定变成编译期不可能。

代价：`domain` 模块不能用 `androidx.annotation`、不能用 `Parcelable`。
好处：领域层测试在纯 JVM 上跑，秒级完成，不需要模拟器。

### 5. 一个限界上下文 = 一个 Gradle 模块（`feature:<context>`）

模块边界 = 上下文边界。想让两个上下文互相依赖，必须在 `build.gradle.kts` 里显式写出
`implementation(project(":feature:xxx"))`——而这会被架构断言测试拦截（R2）。
**架构违规因此从「需要自觉」变成「需要主动写一行代码才能做到」**，再被 CI 抓住。

### 6. 序列化：kotlinx.serialization

编译器插件实现，无反射，对 R8 与启动时间友好。且它是**编译期**的，
若误用在 `domain` 层会直接触发 R3 的架构断言。

### 7. 构建约定：`build-logic` 约定插件

避免每个模块重复 30 行样板。约定插件的存在让「新增一个上下文」变成
「加一行 `include(":feature:x")` + 一个 3 行的 `build.gradle.kts`」，
降低架构遵守成本——**降低遵守成本本身就是一种架构保障**。

### 8. 静态检查 detekt + 架构校验 Konsist

detekt 管代码风格；Konsist 管 R2/R5/R6 这类跨模块规则（见 AGENTS.md 第 4.3 节）。
两者都是纯 JVM，不拖慢构建。

## 备选方案

| 方案 | 优点 | 缺点 | 未采用原因 |
|---|---|---|---|
| **XML View + Fragment** | 生态资料多、老手熟悉 | 状态分散在 View 层，与 UDF 相悖，无法用预览做纯渲染测试 | 与领域驱动的分层契约冲突 |
| **Koin** | 无注解处理、编译快、KMP 友好 | 错误在运行时才暴露；本项目不做 KMP | 收益不抵风险 |
| **SQLDelight** | SQL 优先、KMP 友好、类型安全 | 迁移测试生态不如 Room 现成 | 单人项目优先选集成度高的 |
| **`domain` 用 Android Library + lint 检查** | 可用 `Parcelable`、`androidx.annotation` | R3 只能事后发现，且领域测试需要 Robolectric，慢且脆 | **失去了「违规写不出来」这一最强保障** |
| **单体模块 + 包结构分层** | 起步最快，无需 `build-logic` | 架构违规无法被机器检测；上下文边界靠自觉 | 与「机器强制」目标冲突；后期拆分成本极高 |
| **KMP（多平台）** | 未来可复用到 iOS/桌面 | 显著增加复杂度；用户未要求 | YAGNI |
| **AGP 8.x 而非 9.x** | 生态插件适配成熟，风险低 | 不是「最新」 | 用户明确要求最新；风险由 T-001 验证后按需回退（见下） |

## 后果

**正向**

- 架构规则可被机器强制（编译期 + 断言测试双层）
- 领域层测试快（纯 JVM），使「领域测试覆盖率 ≥ 90%」这一目标现实可行
- 新增上下文成本低，鼓励正确的划分而非「先塞进已有模块」

**负向**

- `build-logic` 与约定插件本身有学习与维护成本（一次性，约 0.5 天）
- `domain` 模块不能用 `Parcelable`，跨进程传递领域对象需要额外的 DTO 转换
- 模块数量增长会拉长首次构建时间

**影响面**

| 受影响对象 | 具体影响 |
|---|---|
| 代码 | 所有模块布局、`settings.gradle.kts`、`build-logic/` |
| 文档 | `docs/30-architecture/module-graph.md` 需同步维护 |
| 测试 | 领域测试放纯 JVM；数据层用 Robolectric/内存库；UI 用 Compose 测试 |
| 构建/CI | 需新增 `verifyDomainPurity`、`checkModuleDependencies` 两个任务 |
| 用户 | 无直接影响 |

## 风险与回退路径

**主要风险**：AGP 9.4.0 是大版本，Hilt / Konsist / detekt 可能尚未适配。

**回退规则（预先约定，避免事后争执）**：

1. T-001 按 AGP 9.4.0 尝试；某个插件不兼容时，**允许把 AGP 降到该插件支持的最高版本**。
2. 回退**必须**新增 ADR 记录：哪个插件、什么错误、降到哪个版本、何时可以再升。
3. 只有 `domain` 模块类型、模块划分、Compose、Room 这四项**不允许**在 T-001 中妥协——
   它们是本 ADR 的承重结构。

## 复审条件

- KMP 需求出现（需重评 Koin / SQLDelight / 模块结构）
- 项目从单人变为多人协作（需重评模块粒度与 CI 强度）
- Q-007 决定上架应用商店且权限受限（可能影响定位与通知捕获的实现方案，但不影响本 ADR 的分层结构）
- Android 官方发布新的架构指引与本文冲突
