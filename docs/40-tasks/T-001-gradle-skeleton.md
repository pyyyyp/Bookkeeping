# T-001 Gradle 骨架与领域层模块（纯 JVM，可离线验证）

- 状态: **进行中**
- 需求: —（工程基础设施，无对应 REQ）
- 上下文: core
- 影响聚合: 无（仅共享内核类型）
- 依赖: 无
- 分支: `feat/T-001-gradle-skeleton`
- 预估: 0.5 天

## 目标

把仓库从「只有文档」变成「有一个能构建、能测试、能跑架构校验的 Gradle 工程」，
并交付 `core:domain` 共享内核。

**范围裁剪说明**：本机无 Android SDK 与 Android Studio（T-001 前置探测结果），
AGP 在配置阶段就会失败，因此本卡**只包含纯 JVM 部分**——它的验收是真实可跑的。
Android 模块骨架拆到 T-002。

## 变更清单

- [ ] 从 `main` 建出 `develop` 分支（修复已知偏差：仓库此前只有 `main`）
- [ ] Gradle Wrapper（`gradlew` / `gradlew.bat` / `gradle/wrapper/*`）
- [ ] `settings.gradle.kts`（仅含 `:core:domain`）
- [ ] `gradle/libs.versions.toml`（版本目录；**逐项核实版本**）
- [ ] `gradle.properties`
- [ ] `build-logic/` + 约定插件 `kotlin.jvm.convention`
- [ ] `core/domain/` 模块
- [ ] 共享内核类型实现
- [ ] 共享内核单元测试

## 领域层交付内容（共享内核）

**这些是纯基础设施类型，不含任何业务规则**，因此不受 Q-012 等阻塞影响。

| 类型 | 说明 |
|---|---|
| `Money` | 以「分」为单位的不可变值对象；`init` 校验非负；`Comparable` |
| `EntryDirection` | `Income` / `Expense`。**不用正负号表达方向** |
| `Outcome<T>` | `Ok` / `Err`，替代异常跨层 |
| `DomainError` | 密封接口：领域错误 + 技术错误的公共父类型 |
| `DomainEvent` | 含 `occurredAt` |
| `AggregateRoot<ID>` | 聚合根接口，含 `pendingEvents` |
| `TimeRange` | 时间区间值对象，用于工时与结算周期 |

## 验收

- [ ] `./gradlew :core:domain:build` 通过
- [ ] `./gradlew :core:domain:test` 全绿
- [ ] `core:domain` 为 `kotlin("jvm")` 模块，**没有 Android 类路径**（R3 天然成立）
- [ ] `libs.versions.toml` 中每个版本都有官方来源依据，并回填到 `tech-baseline.md`
- [ ] `docs/00-charter/tech-baseline.md` 的「待锁定」项更新为已核实

## 测试清单

- [ ] `MoneyTest.\`金额不可为负\``
- [ ] `MoneyTest.\`以分为单位运算不产生浮点误差\``
- [ ] `MoneyTest.\`相同金额相等\``
- [ ] `TimeRangeTest.\`结束早于开始时被拒绝\``
- [ ] `TimeRangeTest.\`零长度区间被拒绝\``

## 实现顺序

```
1. 建 develop 分支
2. 探测并锁定版本（Gradle / Kotlin / 测试库）
3. gradle wrapper
4. settings.gradle.kts + gradle.properties
5. libs.versions.toml
6. build-logic 约定插件
7. core/domain 模块构建文件
8. Money → EntryDirection → Outcome/DomainError → DomainEvent/AggregateRoot → TimeRange
9. 测试
10. 回填 tech-baseline.md
```

## 完成情况

- 提交: 待填
- 未决: 无（本卡不涉及业务规则）
- 备注: 本机 JDK 21，无 Gradle（已下载发行版到 `.tools/`，不入库）

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| 不含 Android 模块 | 本机无 Android SDK，AGP 无法配置 | 用户（已同意拆卡） | T-002 |
