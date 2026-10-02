# 模块依赖图与依赖规则

- 最后更新: 2026-10-02（T-003：R2/R3/R7/R8 已机器强制，见 `ADR-0004`）

> 本文与 `settings.gradle.kts` 必须保持一致。新增模块必须同步更新本文并新增 ADR。

## 依赖图

```
                        ┌─────────┐
                        │   app   │  ← 唯一可同时依赖多个 feature 的模块
                        └────┬────┘
             ┌───────────────┼───────────────┐
             ▼               ▼               ▼
      ┌────────────┐  ┌────────────┐  ┌────────────┐
      │ feature:A  │  │ feature:B  │  │ feature:C  │   ← 上下文，互不依赖
      └─────┬──────┘  └─────┬──────┘  └─────┬──────┘
            └───────────────┼───────────────┘
                            ▼
                  ┌──────────────────┐
                  │   core:domain    │  ← 纯 Kotlin JVM，无 Android 依赖
                  │   core:ui        │
                  │   core:common    │
                  │   core:data      │
                  │   core:testing   │
                  └──────────────────┘
```

## 依赖规则

| 规则 | 内容 | 强制手段（执行点） | 状态 |
|---|---|---|---|
| R1 | `feature:*` 可依赖 `core:domain`、`core:ui`、`core:common` 及自身内部分层 | 编译期 | ✅ |
| R2 | `feature:*` **不得**依赖另一个 `feature:*` | **依赖声明**：`checkModuleDependencies`<br>**源码引用**：`ArchitectureTest.R2` | ✅ 双向已接入 |
| R3 | `domain` 层禁止 Android / 框架 / DI / 序列化依赖 | `kotlin("jvm")` 模块类型（`:core:domain`）<br>+ `verifyDomainPurity`（含 feature 内的 `domain` 包）<br>+ `checkModuleDependencies`（`:core:domain` 的依赖面） | ✅ 三层 |
| R4 | `data` 实现 `domain` 声明的接口（依赖倒置） | 编译期 | ✅ |
| R5 | `presentation` 只依赖 `domain`/`application` 抽象 | `ArchitectureTest.R5` | ⚠️ 已接入，**暂无靶子** |
| R6 | 外部模型（DTO/Entity/JSON）不得进入 `domain` | `ArchitectureTest.R6` | ⚠️ 已接入，**部分有靶子** |
| R7 | `core:*` 不得依赖任何 `feature:*` | `checkModuleDependencies` | ✅ |
| R8 | `app` 不含业务规则，只做组装 | `checkModuleDependencies`（只有 `:app` 能依赖 feature）<br>+ `ArchitectureTest.R8`（只有 `:app` 能引用 `com.jizhangbao.app.*`） | ✅ 依赖面 ✅ / 源码面 ✅ |
| R9 | `domain` 不得引用 `application` / `presentation` | 编译期 + `ArchitectureTest.R6` | ✅ |
| R10 | 同上下文内 `data` 只被 `di` 与自身使用 | `ArchitectureTest.R10` | ⚠️ 已接入，**暂无靶子** |
| R11 | 禁止用 `api(...)` 暴露实现依赖 | 代码评审 | ⬜ 无机器手段 |
| R12 | 每个 feature 可独立构建与测试 | CI（T-004） | **✅ 构建**／⬜ 测试（尚无测试） |

> 执行点为什么这样分，见 **`ADR-0004`**。一句话：**模块图的事实源是 Gradle，类型引用的事实源是源码**，
> 两边各自强制一半，合起来才闭合。
>
> **两处诚实说明**：
>
> - **R5 / R10 目前「已接入但没有靶子」**：它们针对 `presentation` 与 `data` 包，而这些包今天还不存在
>   （feature 模块只有构建脚本）。断言的存在经过**反向验证**证明有效（注入违规文件后确实失败），
>   但在真实业务代码落地前，它们是「装好了瞄准镜、还没有目标」的状态。
> - **R12 的诚实状态**：`:feature:<x>:assembleDebug` 已实测可独立构建；
>   但 `:feature:<x>:test` 目前是 `NO-SOURCE` —— **能跑通不等于有验证**。
>
> **R11 仍是纯人工**：它需要判断「这条依赖是不是实现细节」，机器判别容易误报；由代码评审承担。

## 模块清单

> 状态列：⬜ 待建立 / ✅ 已建立（T-002 第二档，2026-10-02 全部构建通过）。
> 上下文清单来自 `docs/20-domain/context-map.md`（**草稿 v0.3，待评审**）。
>
> **「依赖」列是各模块 `build.gradle.kts` 里的真实声明**，不是意图。

| 模块 | 类型 | namespace | 依赖 | 负责的上下文 | 状态 |
|---|---|---|---|---|---|
| `:app` | Android Application | `com.jizhangbao.app` | core:domain, core:common, core:ui, core:data + 全部 5 个 feature | — | ✅ |
| `:core:domain` | **Kotlin JVM** | — | 无 | 共享内核 | ✅ |
| `:core:ui` | Android Library | `com.jizhangbao.core.ui` | core:domain | 设计系统 | ✅ |
| `:core:data` | Android Library | `com.jizhangbao.core.data` | core:domain | 跨上下文**共享**的数据基础设施 | ✅（**暂无源文件**，见下） |
| `:core:common` | Android Library | `com.jizhangbao.core.common` | 无 | 日志/时间/调度器 | ✅ |
| `:core:testing` | Android Library | `com.jizhangbao.core.testing` | core:domain | Fake / 测试数据构造器 | ✅（**暂无源文件**，见下） |
| `:feature:ledger` | Android Library | `com.jizhangbao.ledger` | core:domain, core:ui | **Ledger 账本（核心域）** | ✅ 骨架 |
| `:feature:worklog` | Android Library | `com.jizhangbao.worklog` | core:domain, core:ui | **Worklog 工时（核心域）** | ✅ 骨架 |
| `:feature:payroll` | Android Library | `com.jizhangbao.payroll` | core:domain, core:ui | **Payroll 薪资（核心域）** | ✅ 骨架 |
| `:feature:calendar` | Android Library | `com.jizhangbao.calendar` | core:domain | Calendar 工作日历（支撑域，含法定节假日与调休） | ✅ 骨架 |
| `:feature:insight` | Android Library | `com.jizhangbao.insight` | core:domain, core:ui | Insight 统计（支撑域，读模型） | ✅ 骨架 |

**「✅ 骨架」的含义**：模块存在、依赖方向已声明、能独立构建；**尚未包含聚合、用例与界面**。
之所以现在就建，是因为模块边界 = 上下文边界，**边界越晚划越贵**；
但里面的代码必须由澄清后的领域模型倒推，不在骨架里猜（见各模块 `build.gradle.kts` 里的说明）。

**两个空模块是刻意的**：

- `:core:data` —— Room 拒绝空的 `entities` 列表，而第一个实体必须由真实聚合倒推（R6）。
  为了让编译器闭嘴而编一张「占位表」，等于把猜出来的结构写进唯一事实源的 schema。
  Room 的可用性改在仓库外的验证工程里证明，配方见 `60-runbooks/build.md`。
  ⚠️ **更正（ADR-0007）**：本文此前写着「第一个 `@Entity` 随第一个数据层任务卡一起进本模块」，
  那句话**与 R7 冲突**（本模块不得依赖任何 `feature:*`，而实体必须引用上下文的领域类型）。
  实际归属见下节。
- `:core:testing` —— 它要装的是 Fake 与测试数据构造器，那必须由真实领域类型倒推。

## 数据层的归属（ADR-0007，2026-10-02 落地）

```
feature:ledger/
├── domain/         ← 聚合、值对象、仓储接口
├── data/           ← LedgerEntryEntity（Room）、Dao、Mapper、RepositoryImpl
├── di/             ← 该上下文的 Hilt 绑定
└── presentation/   ← ViewModel 与 Compose
app/…/data/         ← 唯一的 @Database（组合根才看得见全部上下文的实体）
core:data/          ← 跨上下文共享的数据基础设施（TypeConverter 等），**不持有任何实体**
```

| 决定 | 为什么 |
|---|---|
| 实体 / Mapper / 仓储实现在**上下文模块** | R7 禁止 `core:*` 依赖 `feature:*`，而它们必须引用上下文的领域类型 |
| 唯一的 `@Database` 在 **`:app` 的 `data` 包** | 只有组合根能同时看见全部 feature 的实体（R8）；数据库定义不是业务规则 |
| `:core:data` 只放共享基础设施 | 一旦持有实体就必须依赖某个 feature → 直接违反 R7 |

> ⚠️ 数据库类的位置**不能随便挪**：挪出 `data` / `di` 包会触发 Konsist 的 R10
> （非 data/di 的文件不得引用 data 层），挪出 `:app` 会看不见其他上下文的实体。
>
> 这一决定让 **R5 / R6 / R10 第一次有了真实靶子**——此前它们是「装好了瞄准镜、还没有目标」。

> **v0.2**：原计划的 `:feature:allocation` 已删除（Q-003 澄清「按比例」指工资分摊，不是收入分配）。

## 构建约定

10 个 Android 模块的公共配置集中在 **`build-logic/`**（Gradle included build）的两个约定插件里：

| 约定插件 id | 使用方 |
|---|---|
| `jizhangbao.android.library` | `core:common` `core:ui` `core:data` `core:testing` 与 5 个 `feature:*` |
| `jizhangbao.android.application` | `:app` |

因此新增一个上下文只需要三处改动：`settings.gradle.kts` 一行、
一个 3 行的 `build.gradle.kts`、以及本文与 `context-map.md`。
`:core:domain` **不使用**这两个插件（它是纯 JVM 模块，这正是 R3 的强制手段）。
详见 `docs/30-architecture/ADR-0003-agp9-build-baseline.md`。

## 校验命令

```bash
./gradlew verifyDomainPurity checkModuleDependencies   # 两条零依赖校验任务（R2/R3/R7/R8）
./gradlew :core:testing:testDebugUnitTest              # Konsist 架构断言（R2/R5/R6/R8/R10）
./gradlew detekt                                       # 静态分析：代码味道与风格（T-006）
./gradlew build                                        # 上面全部：都已接入各模块的 check
```

> 四条命令都由 `./gradlew build` 自动触发，不必单独记得。
> **注意**：`:core:testing:testDebugUnitTest` 会被 Gradle 的 up-to-date 检查影响——
> 因为它扫描的是**别的模块**的源码，所以 `core/testing/build.gradle.kts` 里
> 显式把这些源码声明成了该测试任务的输入。删掉那段会**静默**让架构断言失效。
> 详见 `ADR-0004` 决策 3。
>
> **detekt 用 light 模式**（不启用类型解析）：AGP 9 内置 Kotlin 下类型解析看不到生成类
> （`BuildConfig` / `R` / KSP 产物，上游 #9402），启用会误报。
> 配置在 `config/detekt/detekt.yml`，**偏离默认规则必须写明理由**
> （目前只有 1 条：Compose 的 `@Composable` 函数必须 PascalCase）。
