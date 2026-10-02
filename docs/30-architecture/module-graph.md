# 模块依赖图与依赖规则

- 最后更新:

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
| R12 | 每个 feature 可独立构建与测试 | CI | ⬜ |

> 「强制手段」为「架构断言测试」的规则，需要在接入 Konsist 或自定义 Gradle 任务后才能标记为 ✅。

## 模块清单

| 模块 | 类型 | namespace | 依赖 | 负责的上下文 |
|---|---|---|---|---|
| `:app` | Android Application | | 全部 feature | — |
| `:core:domain` | **Kotlin JVM** | — | 无 | — |
| `:core:ui` | Android Library | | core:domain | — |
| `:core:data` | Android Library | | core:domain | — |
| `:core:common` | Android Library | | 无 | — |
| `:core:testing` | Android Library | | core:domain | — |
| `:feature:<x>` | Android Library | | core:domain, core:ui | <x> |

## 校验命令

```bash
./gradlew checkModuleDependencies      # 模块依赖断言
./gradlew verifyDomainPurity           # domain 层框架依赖扫描
./gradlew :app:testDebugUnitTest       # 含架构断言测试
```
