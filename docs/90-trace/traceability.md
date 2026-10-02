# 追溯矩阵

- 最后更新: 2026-10-02（T-002 第二档：11 模块落地 + ADR-0003）

> 本文件是整套范式的**收口处**：任意一条需求都能查到它落在哪个聚合、哪张任务卡、
> 哪个提交、哪个测试上。
> **维护时机**：任务完成的同一个提交内（`docs(trace): 更新 T-xxx 追溯矩阵`）。

## 需求 → 实现

| 需求 | AC | 上下文 | 聚合 | 任务 | 提交 | 测试 | 状态 |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

**状态图例**：✅ 已完成 / 🚧 进行中 / ⏳ 待开始 / ❌ 已废弃

<!-- 示例（填写真实数据后删除本块）
| REQ-004 | AC-1 | Ordering | Order | T-012 | d4e5f6a | OrderTest.`积分充足时抵扣成功` | ✅ |
| REQ-004 | AC-3 | Ordering | Order | T-018 | - | - | ⏳ 待设计稿 |
-->

## 未决问题 → 影响面

| 问题 | 影响需求 | 影响代码位置 | 状态 |
|---|---|---|---|
| Q-015 缺勤与法定节假日带薪 | 待建 REQ-00x | `feature:payroll` 的 `DailyIncome` 公式 | 🔴 阻塞需求 |
| Q-016 节假日数据来源 | 待建 REQ-00x | `feature:calendar` 的数据层（ACL） | 🔴 阻塞需求 |
| Q-017 加班时长门槛 | 待建 REQ-00x | `feature:payroll` 与 `feature:worklog` 的接口 | 🟡 |
| Q-018 日薪取整 | — | `DailyRate` 计算 | 🟡 |
| Q-019 月薪生效日期 | — | `MonthlySalary` 聚合 | 🟡 |
| Q-004 结算周期 | 待建 REQ-00x | `Payslip` 不变式 | 🟡 |
| Q-005 多账户 / 多币种 | — | `Account` 聚合、`Money` 值对象 | 🟡 |
| Q-006 备份与导出 | — | `core:data` | 🟡 |
| Q-010 去重策略 | — | `LedgerEntry` 唯一性 | 🟡 |
| Q-011 识别失败处理 | — | 待确认队列 UI | 🟡 |
| Q-013 通知权限降级 | — | 权限引导 UI | 🟡 |
| Q-020 视觉设计（色板/排版/图标） | 全部 UI | `:core:ui` 的 `JizhangbaoTheme`（现为 M3 默认值 + `TODO(Q-020)`） | 🟡 |
| Q-008 记账日 | — | `LedgerEntry.bookingDate` | 🟢 |
| Q-009 固定班次 | — | `WorkSession` 不变式 | 🟢 |
| Q-014 围栏提醒未响应 | — | `WorkSession` 生命周期 | 🟢 |

> **注意**：上表问题**均不阻塞 T-002 ~ T-005**（工程基础设施）。
> 它们阻塞的是业务需求与领域模型（REQ / `*-model.md`）。

## ADR → 影响面

| ADR | 决策 | 影响模块 |
|---|---|---|
| ADR-0001 | 技术栈与工程结构选型（Compose/Hilt/Room；domain 用纯 Kotlin JVM；一上下文一模块） | 全部 |
| ADR-0002 | 仓库迁移到纯 ASCII 路径 | 全部（构建） |
| ADR-0003 | AGP 9 构建基线落地（内置 Kotlin 版本承载、约定插件、compileSdk 37 / targetSdk 36） | 全部（构建） |
| ADR-0004 | 架构校验的执行方式与工具选型（规则分两处执行；接受 Konsist 并钉死其编译器；detekt 待裁决） | 全部（构建与校验） |

## 契约 → 使用方

| 契约 | 版本 | 使用方模块 |
|---|---|---|
| | | |

## 统计

| 指标 | 数值 |
|---|---|
| 需求总数 | 0（待 Q-015 / Q-004 澄清后建立） |
| 已完成的 AC 数 | 0 |
| 未决问题数 | 15（2 红 / 10 黄 / 3 绿） |
| 已接受的 ADR 数 | 4 |
| 限界上下文数 | 5（Ledger / Worklog / Payroll / Calendar / Insight） |
| 工程任务数 | 5（T-001 / T-002 / T-003 完成；T-004 / T-005 待开始） |
| 已建工程模块数 | **11**（1 个 Kotlin JVM + 9 个 Android Library + 1 个 Application） |
| 领域层测试数 | 14（全绿，2026-10-02 强制重跑核实） |
| Android 侧测试数 | **6**（全部是架构断言；尚无业务测试） |
| 可交付产物 | `app-debug.apk`（11.65 MB，已在模拟器上启动验证） |

## 工程任务进度

> 工程任务无对应 REQ，故不进入上表。它们的验收记录在各任务卡内。

| 任务 | 内容 | 状态 | 提交 |
|---|---|---|---|
| T-001 | Gradle 骨架与共享内核领域层（纯 JVM） | ✅ 已完成 | `09ac5df` `96f4027` |
| T-002 | Android SDK 环境与 Android 模块骨架 | ✅ **已完成**（两档全达成） | `0d844b3` `3616f3a` `d4fdb23` `d6040b1` `32c71be` `5896ae4` `9e25711` |
| T-003 | 架构规则的机器强制（校验任务与断言） | ✅ **已完成**（含 4 + 5 条反向验证） | 见本轮提交 |
| T-004 | CI 流水线与追溯校验脚本 | ⏳ 待开始 | — |
| T-005 | 开源配套（LICENSE / README / 隐私声明） | ⏳ 待开始 | — |

**T-001 验收证据**：`:core:domain:build` BUILD SUCCESSFUL；
14 个测试 0 失败；`compileClasspath` 中 Android 条目数为 **0**（R3 在依赖层面得证）。
**T-001 已知偏差**：提交 `09ac5df` 粒度混合（构建骨架 + 领域层），详见任务卡。

**T-002 验收证据（2026-10-02）**：

- `./gradlew assembleDebug` BUILD SUCCESSFUL，产出 `app-debug.apk` **11.65 MB**；
  `aapt2 dump badging`：`com.jizhangbao.app`、`versionCode=1`、`versionName=0.1.0`、
  `minSdk=26`、`targetSdk=36`、`compileSdk=37`、`launchable-activity=...MainActivity`、`label=记账宝`
- `./gradlew lintDebug` **0 error / 2 warning**（`MissingApplicationIcon`、`DataExtractionRules`）
- `./gradlew :core:domain:test --rerun` **14 tests / 0 failures / 0 errors**
- 11 个模块全部可独立构建；`:app:testDebugUnitTest` 为 `NO-SOURCE`（**无测试，不等于通过**）
- `adb install -r` → 启动 `MainActivity` → 截图确认占位首页渲染正常，logcat 无 FATAL
  （**设备是 MuMu 模拟器**，伪装成 HUAWEI HBP-AL00；Android 12 / API 32。真机未验证）
- **已知隐患**：生效的 Kotlin 版本靠根构建脚本的插件别名承载，删掉会静默退回 2.2.10（ADR-0003）
- **移交 T-003**：detekt / Konsist 兼容性；**待定**：Q-020 视觉设计

**T-003 验收证据（2026-10-02）**：

- `verifyDomainPurity` 通过：`扫描 5 个 domain 源文件，未发现框架依赖（R3 通过）`
- `checkModuleDependencies` 通过：`已检查 53 条依赖声明，未发现违规（R2 / R7 / R8 / R3 通过）`
- `./gradlew build` 会触发两者（实测）；完整门禁
  （`assembleDebug lintDebug testDebugUnitTest verifyDomainPurity checkModuleDependencies`）BUILD SUCCESSFUL
- Konsist 架构断言 6 条（R2 / R5 / R6 / R8 / R10 + 1 条防空洞守卫）：
  `tests=6 failures=0 errors=0`
- **反向验证**：4 条 Gradle 任务违规（R3 源码 / R2 依赖 / feature 内 domain 包 / R3 依赖面）
  + 5 条 Konsist 断言违规**全部按预期失败**，每条都能指到文件与行号
- **过程中修掉一个真实缺陷**：架构断言曾因 Gradle up-to-date 检查而**根本没跑**
  （`testDebugUnitTest UP-TO-DATE`，同时有 4 个违规文件存在）→ 已通过声明跨模块输入修复
- **已知缺口**：`detekt` 在 AGP 9 内置 Kotlin 下**无可用版本**（待用户裁决）；R5 / R10 暂无靶子

## 校验（可选，建议接入 CI）

```bash
# 检查矩阵中引用的 REQ / T / ADR 编号是否都存在于 docs/
./scripts/verify-traceability.sh
```

**校验规则**
1. 矩阵中出现的每个 `REQ-xxx` 必须存在对应文件。
2. 每个 `REQ-*.md` 中的每条 `AC-x` 必须在矩阵中出现。
3. 标记为 ✅ 的行必须有非空的「提交」与「测试」列。
