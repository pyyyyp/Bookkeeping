# T-003 架构规则的机器强制（校验任务与断言）

- 状态: **已完成**（2026-10-02）
- 需求: —（工程基础设施）
- 上下文: core
- 依赖: T-002
- 分支: `feat/T-003-architecture-verification`
- 预估: 0.5 天 | 实际: 约 1 轮（含 3 次反向验证与 1 次「断言被静默跳过」的修复）
- 相关决策: **`ADR-0004`**（架构校验的执行方式与工具选型）

## 目标

把 AGENTS.md 第 4 节的 R1–R12 从「文档里的规则」变成「违规就构建失败」。
**没有这一步，整套 DDD 分层会在几周内退化成注释。**

## 变更清单

- [x] `verifyDomainPurity` Gradle 任务：扫描 domain 源集，发现 `android.` / `androidx.` /
      `dagger.` / `javax.inject.` / `retrofit2.` / `okhttp3.` / `com.squareup.` /
      `kotlinx.serialization.` / `io.coil-kt.` / `org.koin.` 即失败
      （**不拦 `kotlinx.coroutines`**：协程是纯 JVM 库，domain 层可以用它表达异步）
- [x] `checkModuleDependencies` Gradle 任务：R2 + R7 + R8 合并为一条判据
      「只有 `:app` 可以依赖 `:feature:*`」，外加 **R3 的依赖面**
      （`:core:domain` 不得声明 androidx / dagger / retrofit 等依赖）
- [x] Konsist 架构断言（R2 / R5 / R6 / R8 / R10），放在 `:core:testing` 的 test 源集
- [x] 把两个任务接入 `check` 生命周期（每个模块的 `check` 都依赖它们）
- [x] `verifyDomainPurity` 增加「扫描到 0 个文件即失败」守卫，防止规则静默失效
- [x] 反向验证（本卡的核心，见下表）
- [ ] 在 CI 中显式调用 —— 归 **T-004**

## 验收

- [x] `./gradlew verifyDomainPurity` 通过 —— **验证: 自动化**，输出
      `扫描 5 个 domain 源文件，未发现框架依赖（R3 通过）`
- [x] `./gradlew checkModuleDependencies` 通过 —— **验证: 自动化**，输出
      `已检查 53 条依赖声明，未发现违规（R2 / R7 / R8 / R3 通过）`
- [x] `./gradlew build` 会触发上述两个任务 —— **验证: 自动化**，实测 `build` 输出里同时出现
      两条任务的执行与结论行，`BUILD SUCCESSFUL in 1m 10s`
- [x] **反向验证**：在 `:core:domain` 注入 `import android.os.Build` → 任务失败 → 已还原
- [x] **反向验证**：让一个 feature 依赖另一个 feature → 任务失败 → 已还原
- [x] **反向验证（额外）**：`feature` 模块内 `domain` 包的违规 → 任务失败 → 已还原
- [x] **反向验证（额外）**：`:core:domain` 声明 androidx 依赖 → 任务失败 → 已还原
- [x] **反向验证**：Konsist 五条断言各自注入违规 → 5 条全部失败 → 已还原

## 反向验证记录（本卡的核心证据）

> 只证明「不违规时能通过」是没有意义的。下面每一条都**真实注入过违规、看到过失败、再还原**。

| # | 注入的违规 | 期望 | 实测输出 |
|---|---|---|---|
| 1 | `core/domain/.../Money.kt` 首行加 `import android.os.Build` | `verifyDomainPurity` 失败 | `Money.kt:1  import android.os.Build    ← 命中禁止前缀「android.」` |
| 2 | `feature/ledger` 追加 `implementation(project(":feature:worklog"))` | `checkModuleDependencies` 失败 | `:feature:ledger 依赖 project:::feature:worklog —— 只有 :app 可以依赖 feature 模块（违反 R2 / R7 / R8）` |
| 3 | 新建 `feature/ledger/.../domain/TempViolation.kt` 并 `import androidx.room.Entity` | `verifyDomainPurity` 失败（**这是本任务存在的理由**：模块类型保护不到 feature 内的 domain 包） | `TempViolation.kt:3  import androidx.room.Entity    ← 命中禁止前缀「androidx.」` |
| 4 | `core/domain/build.gradle.kts` 追加 `implementation("androidx.core:core-ktx:1.19.1")` | `checkModuleDependencies` 失败（R3 依赖面） | `:core:domain 依赖 androidx.core:core-ktx —— 共享内核必须保持零框架依赖（违反 R3）` |
| 5 | 一次性注入 4 个文件：跨上下文 import（worklog→ledger）、presentation→data、domain→data 且类名 `*Entity`、core:ui→app | Konsist 断言失败 | `6 tests completed, 5 failed`：R2 / R5 / R6 / R8 / R10 全部 FAILED（第 6 条是防空转守卫，应通过） |

## 测试清单

- [x] 正向：干净代码构建通过（`assembleDebug lintDebug testDebugUnitTest verifyDomainPurity checkModuleDependencies` 全绿）
- [x] 反向：注入 R3 违规 → 任务失败并打印**文件与行号**
- [x] 反向：注入 R2 违规 → 任务失败并**指名非法依赖**
- [x] Konsist：6 条断言（5 条规则 + 1 条「扫描范围不能为空」守卫）

## 过程中发现并修掉的一个真实缺陷（值得单独记）

第一次做 Konsist 反向验证时，注入 4 个违规文件后构建**仍然是 `BUILD SUCCESSFUL`**：

```
> Task :core:testing:testDebugUnitTest UP-TO-DATE
```

原因：`ArchitectureTest` 扫描的是**别的模块**的源码，而那些文件不在 `:core:testing`
的任何 source set 里，Gradle 因此认为「输入没变」而跳过测试任务。
**断言一条都没跑，却给了绿灯。**

修法：把会被扫描的源码显式声明为该测试任务的输入（见 `core/testing/build.gradle.kts`）。
修完后同样的注入立刻变成 `6 tests completed, 5 failed`。

教训（已进 `docs/60-runbooks/build.md`）：
**任何需要读取自身模块之外文件的校验任务，都必须显式声明那些文件是它的输入。**

## 已知缺口与移交（不掩盖）

| 缺口 | 原因 | 去向 |
|---|---|---|
| `detekt` **完全不可用** | detekt 1.23.8 的 Gradle 插件硬依赖 `kotlin-android` 插件（AGP 9 内置 Kotlin 下不存在且不能应用），因此**静默地**不会注册任何 Android 任务；1.x 已 EOL，唯一能用的是预发布 `2.0.0-alpha.6` | **待用户裁决**，见 `ADR-0004` 决策 4。因此 `AGENTS.md` 阶段 E 的门禁目前**无法全绿**，这是显式记录的缺口 |
| R5 / R10 **暂无靶子** | 它们针对 `presentation` / `data` 包，而这两个包今天还不存在 | 断言已就位并经反向验证；等第一个业务任务落地 `data`/`presentation` 包时首次真正生效 |
| R11 仍是纯人工 | 「这条依赖是不是实现细节」机器判别容易误报 | 代码评审承担，`module-graph.md` 已如实标注 ⬜ |
| R12 的测试面 | 各 feature 尚无测试（`NO-SOURCE`） | 第一个业务任务卡；CI 校验归 T-004 |
| 扫描规则是手工路径模式 | `*/src/main/kotlin/**`、`*/*/src/main/kotlin/**` | 若模块布局变化会退化成「扫不到」；已有守卫测试兜底（守卫只看 `core/domain` 是否存在） |

## 完成情况

- 提交: 见 `90-trace/traceability.md` 的 T-003 行
- 未决:
  1. **detekt 方向待用户裁决**（引入 alpha / 等 GA / 用 CLI / 不要）
  2. R5 / R10 在真实业务代码落地后需复核判据宽严
- 环境备注: Konsist 0.17.3 只能用于**测试**源集；其内嵌 Kotlin 编译器为 **2.0.21**，
  **不得**被任何依赖顶到 2.4.x（一旦顶上去，所有断言都会因解析失败而失效）
