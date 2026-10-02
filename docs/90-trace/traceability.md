# 追溯矩阵

- 最后更新: 2026-10-02（`REQ-002` 建立：需求 2 条 / AC 17 条 / ADR-0008）

> 本文件是整套范式的**收口处**：任意一条需求都能查到它落在哪个聚合、哪张任务卡、
> 哪个提交、哪个测试上。
> **维护时机**：任务完成的同一个提交内（`docs(trace): 更新 T-xxx 追溯矩阵`）。

## 需求 → 实现

| 需求 | AC | 上下文 | 聚合 | 任务 | 提交 | 测试 | 状态 |
|---|---|---|---|---|---|---|---|
| REQ-001 | AC-1 记一笔支出 | Ledger | `LedgerEntry` | T-007 | f470153…a67cb1f | 冒烟 `t007-4`；`RecordLedgerEntryUseCaseTest` | ✅ |
| REQ-001 | AC-2 记一笔收入 | Ledger | `LedgerEntry` | T-007 | f470153…a67cb1f | 冒烟 `t007-9`；`RecordLedgerEntryUseCaseTest` | ✅ |
| REQ-001 | AC-3 金额必须是正数且 ≤ 两位小数 | Ledger | `LedgerEntry` | T-007 | f470153…a67cb1f | 冒烟 `t007-6` / `t007-7`；`AmountInputTest`（7 条边界） | ✅ |
| REQ-001 | AC-4 必须选择分类 | Ledger | `LedgerEntry` | T-007 | f470153…a67cb1f | 冒烟 `t007-8`；`RecordLedgerEntryUseCaseTest`（未选分类不落库） | ✅ |
| REQ-001 | AC-5 补记（发生时间可改过去） | Ledger | `LedgerEntry` | T-007 | f470153…a67cb1f | 冒烟 `t007-11`；`RecordLedgerEntryUseCaseTest`（补记保持过去时间） | ✅ |
| REQ-001 | AC-6 备注可选 | Ledger | `LedgerEntry` | T-007 | f470153…a67cb1f | `LedgerEntryTest` / `NoteTest`（含 200 码点边界）；冒烟三笔均无备注 | ✅ |
| REQ-001 | AC-7 列表按发生时间倒序 | Ledger | `LedgerEntry` | T-007 | f470153…a67cb1f | 冒烟 `t007-11`：最后录入的 10-01 排在**最下**；顺序由 DAO 的 `ORDER BY` 保证 | ✅ |
| REQ-001 | AC-8 删除需二次确认，取消则不删 | Ledger | `LedgerEntry` | T-008 | 见本轮提交 | 冒烟 `t008-2` / `t008-3`（取消后与点击前**逐像素一致**）；`DeleteLedgerEntryUseCaseTest` | ✅ |
| REQ-001 | AC-9 确认删除后条目不再存在 | Ledger | `LedgerEntry` | T-008 | 见本轮提交 | 冒烟 `t008-4` / `t008-5`（重启后仍不在）+ **数据库直查只剩 2 行**；`DeleteLedgerEntryUseCaseTest` | ✅ |
| REQ-001 | AC-10 条目持久化（本机 SSOT） | Ledger | `LedgerEntry` | T-007 | f470153…a67cb1f | 冒烟 `t007-5`：`am force-stop` 后重启条目仍在 | ✅ |
| REQ-002 | AC-1 三项合计数值正确 | Insight | 无聚合（读模型） | T-009 | - | - | ⏳ 待实现 |
| REQ-002 | AC-2 结余为负时负号可见 | Insight | 无聚合（读模型） | T-009 | - | - | ⏳ 待实现 |
| REQ-002 | AC-3 空月显示 ¥0.00 并提示 | Insight | 无聚合（读模型） | T-009 | - | - | ⏳ 待实现 |
| REQ-002 | AC-4 记一笔后合计立即更新 | Insight | 无聚合（读模型） | T-009 | - | - | ⏳ 待实现 |
| REQ-002 | AC-5 删一笔后合计立即回退 | Insight | 无聚合（读模型） | T-009 | - | - | ⏳ 待实现 |
| REQ-002 | AC-6 可翻看上一个月并回到当月 | Insight | 无聚合（读模型） | T-009 | - | - | ⏳ 待实现 |
| REQ-002 | AC-7 按发生时间归属月份（补记算在发生的那个月） | Insight | 无聚合（读模型） | T-009 | - | - | ⏳ 待实现 |

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
| Q-006 备份与导出 | — | `core:data` | 🟡 |
| Q-021 **是否允许记录未来时间的账**（本轮新增） | REQ-001 | `LedgerEntry` 的 `occurredAt` 校验（**本卡不校验也不写规则**） | 🟡 |
| Q-010 去重策略 | — | `LedgerEntry` 唯一性 | 🟡 |
| Q-011 识别失败处理 | — | 待确认队列 UI | 🟡 |
| Q-013 通知权限降级 | — | 权限引导 UI | 🟡 |
| Q-020 视觉设计（色板/排版/图标） | 全部 UI | `:core:ui` 的 `JizhangbaoTheme`（现为 M3 默认值 + `TODO(Q-020)`） | 🟡 |
| Q-008 记账日 | — | `LedgerEntry.bookingDate` | 🟢 |
| Q-009 固定班次 | — | `WorkSession` 不变式 | 🟢 |
| Q-014 围栏提醒未响应 | — | `WorkSession` 生命周期 | 🟢 |

> **注意**：上表问题**均不阻塞工程任务**。其中 `Q-015` / `Q-016` 阻塞 Payroll 与 Calendar
> 的业务需求；**`REQ-001`（Ledger 手动记账）不被任何一个阻塞**——这正是它被选为
> 第一个业务需求的原因。
> `Q-005`（多账户 / 多币种）已于 2026-10-02 关闭，见 `ADR-0005`。

## ADR → 影响面

| ADR | 决策 | 影响模块 |
|---|---|---|
| ADR-0001 | 技术栈与工程结构选型（Compose/Hilt/Room；domain 用纯 Kotlin JVM；一上下文一模块） | 全部 |
| ADR-0002 | 仓库迁移到纯 ASCII 路径 | 全部（构建） |
| ADR-0003 | AGP 9 构建基线落地（内置 Kotlin 版本承载、约定插件、compileSdk 37 / targetSdk 36） | 全部（构建） |
| ADR-0004 | 架构校验的执行方式与工具选型（规则分两处执行；接受 Konsist 并钉死其编译器；detekt 待裁决） | 全部（构建与校验） |
| ADR-0005 | Ledger v1 范围（不做账户与多币种、物理删除、预置只读分类、不引入领域事件） | `feature:ledger`、`core:data` |
| ADR-0006 | 共享内核的 `DomainError` 必须可被各上下文扩展（去掉 `sealed`） | `core:domain`、全部上下文 |
| ADR-0007 | 数据层归属（实体/仓储实现在上下文模块；唯一的 `@Database` 在组合根） | 全部 `feature:*`、`:app`、`:core:data` |

## 契约 → 使用方

| 契约 | 版本 | 使用方模块 |
|---|---|---|
| | | |

## 统计

| 指标 | 数值 |
|---|---|
| 需求总数 | **2**（`REQ-001` 手动记录一笔账目条目；`REQ-002` 本月收支合计） |
| 已完成的 AC 数 | **10 / 17**（`REQ-001` 全部 10 条 ✅；`REQ-002` 的 7 条待 `T-009`） |
| 未决问题数 | 15（2 红 / 10 黄 / 3 绿；`Q-005` 已关闭，本轮新增 `Q-021`） |
| 已接受的 ADR 数 | **8**（ADR-0006 跨模块错误类型、ADR-0007 数据层归属、ADR-0008 跨上下文读模型的数据通路） |
| 限界上下文数 | 5（Ledger / Worklog / Payroll / Calendar / Insight） |
| 工程任务数 | 10（**T-001 ~ T-008 全部完成**；T-009 待实现；T-010 进行中） |
| 已建工程模块数 | **11**（1 个 Kotlin JVM + 9 个 Android Library + 1 个 Application） |
| 领域层测试数 | **25**（全绿；`T-009` 第一段新增 `SignedMoney` 6 条 + `MonthlyTotals` 5 条） |
| Android 侧测试数 | **80**（JVM：6 条架构断言 + 74 条 Ledger 业务测试）+ **7** 条仪器化 DAO 测试（真库，经 `am instrument` 运行） |
| 可交付产物 | `app-debug.apk`（12.02 MB，已在模拟器上启动并冒烟验证） |

## 工程任务进度

> 工程任务无对应 REQ，故不进入上表。它们的验收记录在各任务卡内。

| 任务 | 内容 | 状态 | 提交 |
|---|---|---|---|
| T-001 | Gradle 骨架与共享内核领域层（纯 JVM） | ✅ 已完成 | `09ac5df` `96f4027` |
| T-002 | Android SDK 环境与 Android 模块骨架 | ✅ **已完成**（两档全达成） | `0d844b3` `3616f3a` `d4fdb23` `d6040b1` `32c71be` `5896ae4` `9e25711` |
| T-003 | 架构规则的机器强制（校验任务与断言） | ✅ **已完成**（含 4 + 5 条反向验证） | 见本轮提交 |
| T-004 | CI 流水线与追溯校验脚本 | ✅ **已完成**（真实 CI 已跑通，run #5 全绿） | 见本轮提交 |
| T-005 | 开源配套（LICENSE / README / 隐私声明） | ✅ **已完成**（已推送；clone 构建与 CI 均验证通过） | 见本轮提交 |
| T-006 | 接入 detekt 静态分析（用户在 T-003 后追加的决定） | ✅ **已完成** | 见本轮提交 |
| T-007 | 记一笔账目条目（端到端：领域 → 数据 → 用例 → ViewModel → Compose → DI） | ✅ **已完成**（模拟器冒烟 8 条 AC 通过） | 见本轮提交 |
| T-008 | 账本列表与删除条目 | ✅ **已完成**（含数据库旁证） | 见本轮提交 |
| T-009 | 本月收支合计（Insight v0，`REQ-002`） | ⏳ 待开始（阶段 B/C 已完成并合入 develop） | — |
| T-010 | 补界面层与数据层的自动化测试（工程任务） | ✅ **已完成**（界面层 13 条 + 数据层 7 条；见卡的"路线与实测结论"） | 见本轮提交 |

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
- **已知缺口（已由 T-006 关闭）**：`detekt` 在 AGP 9 内置 Kotlin 下无可用版本 → 用户裁决引入
  `2.0.0-alpha.6`，见 `ADR-0004` 决策 4 与 T-006；R5 / R10 仍暂无靶子

**T-006 验收证据（2026-10-02）**：

- **用户裁决**：T-003 报告后，用户明确选择「引入 `dev.detekt:2.0.0-alpha.6`」→ 补记进 `ADR-0004` 决策 4
- `./gradlew detekt` 全工程通过（**0 issues**），每个模块都执行（无源码的模块 `NO-SOURCE`）
- 已接进 `check`（`check --dry-run` 列出全部 `:x:detekt`）→ `AGENTS.md` 阶段 E 的
  **四项门禁现在全部可满足**（这是 T-006 存在的直接目的）
- **反向验证**：注入 `MagicNumber` 违规 → `:core:common:detekt FAILED`，报错含
  `文件:行:列 + 规则名`（`...TempDetektViolation.kt:4:26 ... [MagicNumber]`）→ 已还原
- **首轮抓到一条真问题**（非误报）：`Money.kt` 的「一元 = 100 分」换算散在三处
  → 提取为具名常量 `CENTS_PER_YUAN`；同时把 Compose 的 PascalCase 约定写成
  有理由的配置偏离
- 耗时：全工程首次 `detekt` 约 20 秒，增量约 1 秒；完整门禁 58 秒

**T-004 验收证据（2026-10-02）**：

- `.github/workflows/ci.yml`：13 步，命令顺序与 `AGENTS.md` 第 6 节门禁**逐条一致**
  （detekt → lintDebug → verifyDomainPurity/checkModuleDependencies → testDebugUnitTest
  → assembleDebug → 追溯校验）
- YAML 已静态校验：无 TAB、纯 LF、PyYAML 解析通过、以**字符数断言**确认文件是有效 UTF-8
- `scripts/verify-traceability.ps1`：干净状态 **exit 0**，并打印实际检查量
  （`矩阵 143 行；引用编号 REQ 0 / T 6 / ADR 4；需求文件 0 个；✅ 行 0 条`）
- **反向验证 5 条全部按预期失败**：规则 1 / 2 / 3 / 4 各注入一次真实违规，
  外加规则 0「一条编号都解析不到」守卫；每条都打印规则号 + 文件:行号
- **修掉一个 CI 拦路石**：`gradlew` 在 Git 里是 `100644`（无执行位）→ Linux runner 会
  `Permission denied`；已 `git update-index --chmod=+x`（只改模式，0 行增删）
- **未验证**：真实 runner 上的运行（无远端），以及 `android-actions/setup-android`
  能否提供 `platforms;android-37.0` —— 均记在 T-004 卡的豁免项
- **【2026-10-02 补记】上述两项已闭环**：仓库推送后 CI 真实运行，**前 4 次失败、第 5 次全绿**
  （[run #5](https://github.com/pyyyyp/Bookkeeping/actions/runs/37008580857)，约 305 秒，
  13 个步骤全部 success）。过程中改掉 4 个真实问题：
  `android-actions/setup-android@v3` 不可用（根因未证实，job 日志需鉴权）→ 改为自己准备 SDK；
  runner 上 `sdkmanager` 不在 PATH（**exit 127**）→ 三级降级查找；
  `set -e` 让失败不可诊断 → 改为显式面包屑；
  `yes | sdkmanager --licenses` 在 `pipefail` 下因 SIGPIPE 必然判失败 → 显式容忍。
  **关键技术发现**：公开仓库的 job 日志要鉴权，但 **check-run 注解可以匿名读**，
  所以 CI 里用 `::warning::` 发关键信息——这是这轮排障能收敛的原因。详见 T-004 卡。

**T-005 验收证据（2026-10-02，已完成）**：

- **用户决策**：许可证 = **MIT**，署名 **`pyyyyp`**（换远端后更正过，见下）；远端由用户创建
- `README.md`：如实写明「工程骨架阶段，业务功能尚未实现」，含构建步骤、模块表、
  架构约束的强制手段与文档地图
- `PRIVACY.md`：**与实际实现一致**，四条结论各有**实测过的核对命令**
  （零权限 / 不联网 / 无存储 / 无分析 SDK），并单列「计划中功能的数据边界（尚未实现）」
- **.gitignore 复核**：`local.properties` / `*.jks` / `keystore.properties` /
  `google-services.json` / `.tools/` 逐项用 `git check-ignore -v` 确认已忽略
- **历史泄漏复核**：历史中新增过的 **78 个文件**全部检查，无密钥 / 本机配置命中
- **修正一处自查错误**：`PRIVACY.md` 的网络核对命令最初会扫到 `build-logic` 里
  架构校验的**禁止清单**，看起来像有网络依赖 → 已收紧范围并写明原因
- **推送**：首次因**凭据账号不匹配**被拒（`denied to pyyyyp`，403；本机缓存凭据属于另一个
  账号）——Agent 未触碰用户凭据，改为用户换远端后成功：`https://github.com/pyyyyp/Bookkeeping.git`。
  空仓库、写权限经 `--dry-run` 确认，推送后**远端默认分支自动成为 `develop`**
- **clone 构建验证（按 README 走一遍）**：克隆到临时目录 → 建 `local.properties` →
  构建 **BUILD SUCCESSFUL in 10s**。顺带发现 README 两处「照着做会失败」的问题
  （clone 地址还是旧远端、`./gradlew` 在 PowerShell 跑不了）→ 已修
- **真实 CI 全绿**：[run #5](https://github.com/pyyyyp/Bookkeeping/actions/runs/37008580857)
  13 步全部 success，约 305 秒（排障过程见 T-004 卡）

## 校验（已接入 CI）

```bash
# 检查矩阵与 docs/ 是否自洽（规则见下）
pwsh ./scripts/verify-traceability.ps1
# Windows PowerShell 5.1：powershell -File scripts\verify-traceability.ps1
```

由 `.github/workflows/ci.yml` 的「追溯矩阵校验」步骤执行（见 `T-004`、`CONTRIBUTING.md`）。

**校验规则**

| 规则 | 内容 |
|---|---|
| 1 | 矩阵中出现的每个 `REQ-xxx` 必须存在对应文件 |
| 2 | 每个 `REQ-*.md` 中的每条 `AC-x` 必须在矩阵的「需求 → 实现」表里登记 |
| 3 | 标记为 ✅ 的行必须有非空的「提交」与「测试」列 |
| 4 | 矩阵中出现的每个 `T-xxx` / `ADR-xxxx` 必须存在对应文件（T-004 新增） |
| 0 | **一条编号都没解析到即判失败** —— 防止校验脚本自己悄悄失效 |

**不参与校验的内容**（刻意排除，否则会产生假违规）：

- HTML 注释块 `<!-- ... -->` 内的**格式示例**（本文件上方那段示例就是）；
- `REQ-00x` / `T-00x` 这类**占位写法**（「还没建」的标记不是引用）。

> **本脚本的有效性靠反向验证证明**：规则 1 / 2 / 3 / 4 与规则 0 守卫各注入过一次真实违规，
> 每次都失败并打印了规则号、文件与行号。记录见 `T-004` 卡。
