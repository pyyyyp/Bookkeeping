# T-006 接入 detekt 静态分析

- 状态: **已完成**（2026-10-02）
- 需求: —（工程基础设施）
- 上下文: core
- 依赖: T-003
- 分支: `feat/T-006-detekt-static-analysis`
- 预估: 0.5 天 | 实际: 约 1 轮
- 相关决策: **`ADR-0004` 决策 4**（原写成「暂不引入」，已由用户裁决覆盖为「引入」）

> **一个任务 = 一个分支 = 一组内聚提交**，且必须能独立构建通过。

## 背景（为什么单独一张卡）

T-003 落地架构强制时发现：`AGENTS.md` 阶段 E 的门禁里列着 `./gradlew detekt`，
但 **detekt 在 AGP 9 内置 Kotlin 下没有可用版本**：

- `io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8` 注册 Android 任务的唯一入口是
  `plugins.withId("kotlin-android")`；而 AGP 9 内置 Kotlin 时该插件不存在也不能应用
  → 它**静默地**什么都不做（构建照常成功）。1.x 已 EOL，维护者明确不再发新版。
- 唯一覆盖 Gradle 9 + Kotlin 2.4 + AGP 9 内置 Kotlin 的是 **`dev.detekt:2.0.0-alpha.6`**，
  且是预发布。

引入预发布依赖属于「必须问用户」的决定（`AGENTS.md` 第 10 节）。
**用户已于 2026-10-02 明确选择「引入 2.0.0-alpha.6」**，本卡据此执行。

## 目标

让「代码风格与代码味道」也有机器信号：`./gradlew detekt` 能跑、能失败、且接进门禁，
把 `AGENTS.md` 阶段 E 的门禁补全。

## 变更清单

- [x] 版本目录加入 `dev.detekt` 插件（`detekt = "2.0.0-alpha.6"`）与 `detekt-gradle-plugin`（供 build-logic `compileOnly`）
- [x] 通过 `jizhangbao.architecture` 约定插件把 detekt 应用到**所有**模块（不按 src 目录过滤，理由见下）
- [x] **不启用类型解析**（light 模式）——理由见「已知取舍」
- [x] `config/detekt/detekt.yml`：以默认规则为基线，只写**有理由的**偏离（目前仅 1 条）
- [x] 接进 `check` 生命周期（detekt 插件自带；已用 `check --dry-run` 核实）
- [x] 反向验证：注入 `MagicNumber` 违规 → detekt 失败并指名文件/行列/规则 → 已还原
- [x] 更新 `build.md` / `module-graph.md` / `tech-baseline.md` / 交接件门禁说明

## 验收

- [x] `./gradlew detekt` 通过（**0 issues**）—— 验证: 自动化
- [x] `./gradlew build` 会触发 detekt —— 验证: `check --dry-run` 列出全部 `:x:detekt`，
      实跑 `detekt lintDebug testDebugUnitTest assembleDebug` 时每个模块都出现 `detekt` 任务
- [x] **反向验证**：注入违规 → 失败并指名文件、行号、规则名 → 验证后还原
      ```
      e: .../TempDetektViolation.kt:4:26 This expression contains a magic number. ... [MagicNumber]
      e: .../TempDetektViolation.kt:4:31 This expression contains a magic number. ... [MagicNumber]
      > Analysis failed with 2 issues.
      ```
- [x] 构建时间：全工程首次 `detekt` 约 **20 秒**，增量约 **1 秒**；完整门禁
      （detekt + lint + test + assemble + 两条架构任务）本次 **58 秒**
- [x] `AGENTS.md` 阶段 E 的门禁**四项全部可满足**（这是本卡存在的直接目的）

## 测试清单

> 本卡是工程基础设施，无业务测试；证据是「反向验证」。

- [x] 正向：干净代码 `./gradlew detekt` 通过（0 issues）
- [x] 反向：注入违规 → 失败且信息可定位（文件:行:列 + 规则名）

## 首轮发现与处置（真实收益，不是走过场）

第一次跑就给出一条**真问题**（不是误报）：

| 位置 | 规则 | 处置 |
|---|---|---|
| `core/domain/.../Money.kt:36,43` | `MagicNumber` | **改代码**：「一元 = 100 分」这个换算散在格式化（除、取余）与 `ofYuan`（乘）共三处，提取为具名常量 `CENTS_PER_YUAN`。见提交 `fix(domain)` |
| `app/.../MainActivity.kt:43,62` | `FunctionNaming` | **改配置**（误报）：Compose 的 `@Composable` 函数**必须** PascalCase，这是官方约定；detekt 默认规则不知道，配置 `ignoreAnnotated: ['Composable']`。豁免只对带该注解的函数生效 |

两类处置的方向是相反的，但判据是同一条：**默认规则在这里是对的还是错的**。
对就改代码，错就改配置并写明理由。

## 已知取舍（写下来，避免日后被当成疏漏）

| 取舍 | 原因 |
|---|---|
| **不启用类型解析** | AGP 9 内置 Kotlin 下 detekt 的类型解析看不到生成类（`BuildConfig` / `R` / KSP 产物，上游 issue #9402 未修），启用会产生误报。代价是失去一小部分需要类型信息的规则 |
| 接受**预发布**版本 | alpha 之间可能有破坏性变更，升级时需重新核对配置 |
| 接受 Gradle 9.7+ 的弃用告警 | 上游 issue #9742：Android 模块依赖普通 Kotlin/JVM 模块时告警。本项目正好是这种结构（`:app` → `:core:domain`）。目前只是告警，Gradle 10 才可能移除该 API |
| **不按 `src/` 目录过滤模块** | 一开始写了 `if (file("src").exists())`，但那让行为取决于**未入库的空目录**（git 不跟踪空目录），本地与全新 clone 的应用范围会不一致。改为统一应用，空模块报 `NO-SOURCE`，换来确定性 |
| 配置里只有 1 条偏离 | 偏离必须有理由；没有理由的偏离等于悄悄关掉检查 |
| 厂商覆盖的版本低于本机 | detekt 2.0.0-alpha.6 构建于 Gradle 9.6.1 / AGP 9.3.1 / Kotlin 2.4.10，本机是 9.8.0 / 9.4.0 / 2.4.20 → 已用冒烟验证可用；升级时仍需重验 |

## 完成情况

- 提交: 见 `90-trace/traceability.md` 的 T-006 行
- 未决: 无
- 备注: 本卡是「用户在 T-003 之后追加的决定」，编号顺延为 T-006（T-004 / T-005 仍是 CI 与开源配套）

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| 类型解析相关的规则 | AGP 9 内置 Kotlin 下不可用（上游 #9402） | 用户（选择引入 alpha 时已知悉代价） | 上游修复后复查 |
