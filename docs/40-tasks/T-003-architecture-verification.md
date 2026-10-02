# T-003 架构规则的机器强制（校验任务与断言）

- 状态: 待开始
- 需求: —（工程基础设施）
- 上下文: core
- 依赖: T-002
- 分支: `feat/T-003-architecture-verification`
- 预估: 0.5 天

## 目标

把 AGENTS.md 第 4 节的 R1–R12 从「文档里的规则」变成「违规就构建失败」。
**没有这一步，整套 DDD 分层会在几周内退化成注释。**

## 变更清单

- [ ] `verifyDomainPurity` Gradle 任务：扫描 `domain` 源集，发现 `import android.` / `androidx.` / `retrofit2.` / `dagger.` / `kotlinx.serialization.` 即失败
- [ ] `checkModuleDependencies` Gradle 任务：校验 R2（feature 互不依赖）、R7（core 不依赖 feature）
- [ ] Konsist 架构断言测试（R2 / R5 / R6 / R8 / R10）
- [ ] 把两个任务接入 `check` 生命周期，使其在 `./gradlew build` 时自动执行
- [ ] 在 CI 中显式调用（T-004）

## 验收

- [ ] `./gradlew verifyDomainPurity` 通过
- [ ] `./gradlew checkModuleDependencies` 通过
- [ ] `./gradlew build` 会触发上述两个任务
- [ ] **反向验证**：故意在 `core:domain` 中加入一行 `import android.os.Build`，`verifyDomainPurity` **必须失败**；验证后还原
- [ ] **反向验证**：故意在 `settings.gradle.kts` 中让一个 feature 依赖另一个 feature，`checkModuleDependencies` **必须失败**；验证后还原

> 反向验证是这一卡的核心：只证明「不违规时能通过」是没有意义的，
> **必须证明「违规时真的会失败」**，否则校验任务是摆设。

## 测试清单

- [ ] 正向：干净代码构建通过
- [ ] 反向：注入 R3 违规 → 任务失败并打印违规文件与行号
- [ ] 反向：注入 R2 违规 → 任务失败并指名非法依赖

## 完成情况

- 提交:
- 未决:
