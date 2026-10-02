# 模块依赖图与依赖规则

- 最后更新: 2026-10-02（T-002 第二档：11 个模块全部落地，见 ADR-0003）

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

| 规则 | 内容 | 强制手段 | 状态 |
|---|---|---|---|
| R1 | `feature:*` 可依赖 `core:domain`、`core:ui`、`core:common` 及自身内部分层 | 编译期 | ✅ |
| R2 | `feature:*` **不得**依赖另一个 `feature:*` | 架构断言测试 | ⬜ 待接入 |
| R3 | `domain` 层禁止 Android / 框架 / DI / 序列化依赖 | `kotlin("jvm")` 模块类型 | ✅ |
| R4 | `data` 实现 `domain` 声明的接口（依赖倒置） | 编译期 | ✅ |
| R5 | `presentation` 只依赖 `domain`/`application` 抽象 | 架构断言测试 | ⬜ |
| R6 | 外部模型（DTO/Entity/JSON）不得进入 `domain` | 架构断言测试 | ⬜ |
| R7 | `core:*` 不得依赖任何 `feature:*` | 编译期 | ✅ |
| R8 | `app` 不含业务规则，只做组装 | 代码评审 | ✅ |
| R9 | `domain` 不得引用 `application` / `presentation` | 编译期 | ✅ |
| R10 | 同上下文内 `data` 只被 `di` 与自身使用 | 架构断言测试 | ⬜ |
| R11 | 禁止用 `api(...)` 暴露实现依赖 | 代码评审 | ✅ |
| R12 | 每个 feature 可独立构建与测试 | CI | **✅ 构建**（11 个模块均可独立 `assembleDebug`）／⬜ 测试（尚无测试） |

> 「强制手段」为「架构断言测试」的规则，需要在接入 Konsist 或自定义 Gradle 任务后才能标记为 ✅。
>
> **R12 的诚实状态**：`:feature:<x>:assembleDebug` 已实测可独立构建；
> 但 `:feature:<x>:test` 目前是 `NO-SOURCE` —— **能跑通不等于有验证**，
> 等第一个 feature 有领域测试时才能标 ✅。

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
| `:core:data` | Android Library | `com.jizhangbao.core.data` | core:domain | 网络/数据库基础设施 | ✅（**暂无源文件**，见下） |
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
- `:core:testing` —— 它要装的是 Fake 与测试数据构造器，那必须由真实领域类型倒推。

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
./gradlew checkModuleDependencies      # 模块依赖断言
./gradlew verifyDomainPurity           # domain 层框架依赖扫描
./gradlew :app:testDebugUnitTest       # 含架构断言测试
```
