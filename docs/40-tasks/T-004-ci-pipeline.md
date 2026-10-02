# T-004 CI 流水线与追溯校验脚本

- 状态: **已完成**（真实 CI 已跑通并全绿，见文末调试记录）
- 需求: —（工程基础设施）
- 上下文: core
- 依赖: T-003、T-006
- 分支: `feat/T-004-ci-pipeline`（已合并到 develop）
- 预估: 0.5 天 | 实际: 约 2 轮（其中一半花在真实 CI 的排障上）

## 目标

让「提交前门禁」与「追溯矩阵完整性」在每次推送时自动执行——
把 P4（每轮可验证）从人的自觉变成机器的强制。

## 变更清单

- [x] `.github/workflows/ci.yml`：detekt → Lint → 架构规则 → 单测 → 构建 → 追溯校验
- [x] `scripts/verify-traceability.ps1`（选 `.ps1` 而非 `.sh`，理由见下）：
  - 矩阵中引用的每个 `REQ-xxx` / `T-xxx` / `ADR-xxxx` 都存在对应文件
  - `docs/10-requirements/` 中每个 `AC-n` 都在矩阵里出现
  - 标记为 ✅ 的行必须有非空的「提交」与「测试」列
  - **额外**：一条编号都解析不到即判失败（防校验脚本自己静默失效）
- [x] `CONTRIBUTING.md`：说明本地如何跑同一组门禁
- [x] 核对 `AGENTS.md` 第 6 节的门禁命令与 CI 一致 —— **一致，无需改动**
      （CI 逐条对应：`detekt` / `lintDebug` / `verifyDomainPurity checkModuleDependencies` /
      `testDebugUnitTest` / `assembleDebug`，顺序也相同）
- [x] 修掉一个 CI 上的真实拦路石：`gradlew` 在 Git 里的模式是 `100644`（**没有执行位**），
      Linux runner 上 `./gradlew` 会 `Permission denied` → 已 `git update-index --chmod=+x`

> **为什么是 `.ps1` 而不是 `.sh`**：本机开发环境是 Windows（PowerShell 5.1），
> 而 GitHub 的 ubuntu runner 预装 PowerShell 7。**一个脚本两边都能跑**；
> 写两份实现（`.sh` + `.ps1`）迟早会漂移，那比只支持一种更糟。
>
> **注（T-006 之后）**：`detekt` 现已可用（`2.0.0-alpha.6`，见 `ADR-0004` 决策 4 与 T-006），
> 因此 CI 的第一道门禁就是它。阶段 E 的完整门禁：
> `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`
> 外加 `verifyDomainPurity checkModuleDependencies`（已由 `build` 自动触发，CI 里显式列出便于定位失败类别）。

## 验收

- [x] 推送一个故意破坏追溯矩阵的提交，CI **必须**失败
      —— ⚠️ **无远端，无法真的推送**。改为**本地完整模拟**：5 个失败场景逐条注入并确认退出码为 1
      （见下方反向验证记录）。**「CI 在 runner 上失败」这一条仍未验证**，见豁免项
- [x] 推送一个干净提交，CI 全绿 —— ⚠️ 同上，本地等价验证：干净状态退出码 0
- [x] CI 中失败时能在日志里直接看到「哪个文件、哪一行、违反了哪条规则」
      —— 实测输出形如 `规则3 docs/90-trace/traceability.md:13 —— 标了 ✅ 但「提交」列是空的...`
- [x] `gradlew` 执行位问题已修（否则 CI 第一步就失败）

## 反向验证记录（本卡的核心证据）

> 只证明「不违规时能通过」没有意义。下面每一条都**真实注入过违规、看到过失败、再还原**。

| # | 注入的违规 | 期望 | 实测 |
|---|---|---|---|
| 1 | 矩阵追加 `临时行：REQ-999` | 规则1 失败 | `规则1 .../traceability.md —— 矩阵引用了 REQ-999，但 docs/10-requirements/ 下没有对应文件`（exit=1） |
| 2 | 矩阵追加 `| T-099 | 假任务 | ⏳ |` | 规则4 失败 | `规则4 ... 矩阵引用了 T-099，但 docs/40-tasks/ 下没有对应文件`（exit=1） |
| 3 | 新建 `REQ-001-temp-probe.md`，含未登记的 `### AC-1` | 规则2 失败 | `规则2 docs/10-requirements/REQ-001-temp-probe.md —— REQ-001 的 AC-1 没有出现在矩阵的「需求 → 实现」表里`（exit=1） |
| 4 | 把「需求 → 实现」表的占位行换成 ✅ 且提交/测试列为空的行 | 规则3 失败（两条） | `规则3 .../traceability.md:13 —— 标了 ✅ 但「提交」列是空的…` 与 `…「测试」列是空的…`（exit=1） |
| 5 | 用 `-RepoRoot` 指向一个只有表头、无任何编号的临时矩阵 | 规则0 守卫失败 | `规则0 ... 没有从矩阵里解析出任何 REQ / T / ADR 编号——矩阵结构或本脚本的正则已经失效`（exit=1） |

**过程中修掉的三个真实缺陷**（都不是猜的，是撞出来的）：

1. **假违规**：矩阵顶部 HTML 注释里的**格式示例**（`REQ-004`/`T-012`/`T-018`）与占位写法
   `REQ-00x` 被当成真引用 → 已剥离注释块与代码围栏（**保持行号不变**），并给正则加
   `(?![0-9A-Za-z])` 以排除占位标记。
2. **脚本自己不能静默失效**：剥离逻辑一旦写错，校验会永远通过 → 加规则 0 守卫。
3. **输出进不了 stdout**：脚本原先用 `Write-Host`，输出无法被重定向或捕获
   （`> log.txt` 会得到空文件）→ 改为 `Write-Output`。

另外两个 PowerShell 5.1 的坑（本机是 5.1，CI 是 7，脚本必须两边都能跑）：

- **`.ps1` 必须存为 UTF-8 *with BOM***：5.1 把无 BOM 的脚本按 ANSI 读，
  中文与 emoji 会让脚本**解析失败**（实测首次运行直接报「缺少右 }」）。
- **`Get-ChildItem` 只匹配到一项时返回标量**，`Set-StrictMode` 下取 `.Count` 会抛错
  （实测）→ 一律用 `@(...)` 包住。

## 真实 CI 调试记录（2026-10-02，共 5 次运行）

仓库 `https://github.com/pyyyyp/Bookkeeping` 建好后第一次真实推送，
把「本地绿灯」变成了「runner 上真的绿灯」——中间 4 次失败，全部记录如下。

| # | 结果 | 失败点 | 学到的 |
|---|---|---|---|
| 1 | ❌ | `android-actions/setup-android@v3` | 第三方 action 失败，**根因未证实**（job 日志需鉴权） |
| 2 | ❌ | 同上 | 可复现，不是偶发 |
| 3 | ❌ | 我自己写的 SDK 步骤 | **exit 127 = 命令未找到**：runner 上 `sdkmanager` 不在 PATH |
| 4 | ❌ | 同上（安装子步骤） | **exit 1**，但 `set -e` 不告诉你断在哪条命令 |
| 5 | ✅ | — | 全绿，耗时约 **305 秒** |

**关键技术发现：公开仓库的 job 日志要鉴权（匿名 API 返回 403），但 check-run 注解可以匿名读。**
所以本项目在 CI 里把关键诊断信息用 `::warning::` 发出去——失败时能直接读到，
不必再猜一轮。这是这轮排障能收敛的全部原因。

**四个真实的坑**（都不是猜的）：

1. **`android-actions/setup-android@v3` 在 runner 上失败。** 注解显示它属于
   「target 到 Node 20、被强制跑在 Node 24 上」的那批 action，与该仓库的 Node 24 迁移
   issue 吻合；但**日志读不到，根因仍未证实**。处置：不再依赖它，自己准备 SDK。
2. **`sdkmanager` 不在 PATH。** 镜像其实预装了 SDK（`/usr/local/lib/android/sdk`），
   只是 cmdline-tools 没进 PATH。处置：PATH → 镜像内查找 → 从 Google 的
   repository 清单**推导**文件名下载（不写死 build 号，否则迟早 404）。
3. **`set -e` 让失败不可诊断。** 它只给一个退出码。处置：改成每条关键命令显式
   `|| { echo "::error::..."; exit 1; }` + `::warning::` 面包屑。
4. **`yes | sdkmanager --licenses` 在 `pipefail` 下必然判失败**——`yes` 收到 SIGPIPE
   （实测注解：`licenses 返回非零（可能是许可早已接受）`）。处置：显式容忍它，
   真正的门是后面的 `--install`。

**顺带纠正一个本机印象**：runner 镜像里**本来就有 `android-37.0`**
（注解列出 `android-34 … android-37.0 android-37.1 android-37.2`），
所以显式安装那一步在镜像上其实是空操作。保留它仍然正确——不能指望镜像版本不变，
但「镜像里没有 API 37」这个担心被证伪了。

**已知技术债（未处理，目标版本已核实）**：`actions/checkout@v4`、`actions/upload-artifact@v4`、
`gradle/actions/setup-gradle@v4` 仍 target Node 20（最新主版本分别是 **v7 / v7 / v6**，
经 GitHub API 核实）。它们目前能用，只是持续产生弃用告警；下次一并升级即可。

## 完成情况

- 提交: 见 `90-trace/traceability.md` 的 T-004 行
- 未决: 无（原先的「真实 CI 运行未验证」豁免项**已关闭**）
- 备注: 已核对 `AGENTS.md` 第 6 节门禁与 CI 一致，**未改动** `AGENTS.md`
  （它是规则文件，规则变更需要 ADR + 人工确认；本次只是核对）

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| ~~真实 CI 运行验证~~ | **已关闭**：第 5 次运行全绿（[run #5](https://github.com/pyyyyp/Bookkeeping/actions/runs/37008580857)，约 305 秒） | — | — |
| `android-actions/setup-android@v3` 失败的根因 | job 日志需鉴权；已改成不依赖它，故不再阻塞 | — | 若将来重新引入该 action，须先确认 Node 24 兼容性 |
| Node 20 系 action 的版本升级 | 只是弃用告警，当前可用；一次只改一个变量，不与新修复混在一起 | — | 下一个基础设施轮次 |
