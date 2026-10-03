# T-005 开源配套（LICENSE / README / 隐私声明）

- 状态: **已完成**（已推送；clone 构建与真实 CI 均验证通过）
- 需求: —（Q-007 的衍生：自用 + 后续开源）
- 上下文: core
- 依赖: T-002、T-004
- 分支: `docs/T-005-open-source`（已合并到 develop）
- 预估: 0.5 天 | 实际: 约 1 轮
- 用户决策（2026-10-02）: 许可证 = **MIT**；署名 = **`pyyyyp`**（最初按当时的远端账号
  写成 `pythonyunpeng-maker`，换成该账号名下的远端后已更正）
- 远端: `https://github.com/pyyyyp/Bookkeeping.git`（用户创建；**公开**仓库）
  —— 首次推送因凭据账号不匹配失败，换成该账号名下的仓库后成功，过程见文末

## 目标

让仓库达到「别人 clone 下来能理解、能构建、知道数据去哪了」的程度。
这不是锦上添花——**通知监听在技术上可读取全部通知内容**，
开源项目若不说清数据边界，是对使用者的不负责任。

## 变更清单

- [x] `LICENSE` —— 用户选定 **MIT**（待补署名后落盘）
- [x] `README.md`：项目是什么、**当前状态（如实）**、截图、构建前置条件、模块表、架构约束、文档地图
- [x] `PRIVACY.md`：**与代码对应的说明书**，含
  - 当前版本的四条结论（无权限 / 不联网 / 无存储 / 无分析 SDK）+ **每条的核对命令**
  - 计划中功能（通知监听、定位、节假日数据）的数据边界——**明确标注尚未实现**
  - 长期设计约束：无任何上传、数据只在本机、日志不含 PII、权限按需申请
  - ⚠️ 「数据存放在何处 / 如何导出与删除」的**具体做法仍待 Q-006**，已如实写明而不是编一个答案
- [x] `CONTRIBUTING.md` —— T-004 已创建，本卡只补充「CI 与本地一致」的说明已在其中
- [x] 复核 `.gitignore`：`local.properties` / `*.jks` / `keystore.properties` / `google-services.json` / `.tools/` **均已忽略**（逐项用 `git check-ignore -v` 验证）
- [x] 复核 git 历史无泄漏：`git log --all --diff-filter=A --name-only` 列出历史中新增过的
      **78 个文件**，逐个匹配 `local.properties|*.jks|*.keystore|keystore.properties|google-services.json|.env|*.p12|*.pem|password|secret|token|.tools/`
      → **无任何命中**
- [x] 把「引入权限/数据收集/上传/新存储 → 必须同提交更新 `PRIVACY.md`」写进
      `docs/README.md` 的文档更新触发矩阵（否则这条约束没有落点）

## 验收

- [x] `LICENSE` 已选定并加入（**用户选定 MIT**）
- [x] `PRIVACY.md` 与**实际实现一致**——这一条是本卡最容易被敷衍过去的，因此做了两层保证：
      1. 结论全部基于实测事实：Manifest **零 `uses-permission`**、源码与依赖里**无网络代码**、
         `:core:data` **无 `src` 目录**、依赖清单**无分析与上报 SDK**（26 个库逐个过了一遍）；
      2. `PRIVACY.md` 里给出的 **5 条核对命令全部实测过**，确认真的没有输出。
         （其中一条最初写错了：它扫到了 `build-logic` 里架构校验的**禁止清单**，
         看起来像有网络依赖 → 已把范围收紧到 `app core feature` 并写明为什么。）
- [x] 复核 git 历史无密钥文件（78 个历史文件，无命中）
- [x] 在干净目录 `git clone` 后按 README 步骤能构建通过 —— **已验证**：
      克隆到 `%TEMP%\jzb-clone-verify`（默认分支 `develop`），按 README 建 `local.properties`，
      `.\gradlew.bat assembleDebug` → **BUILD SUCCESSFUL in 10s**
      （顺带发现 README 两处「照着做会失败」的问题：clone 地址还是旧远端、
      `./gradlew` 在 PowerShell 里跑不了 → 已修并单独提交）
- [x] 推送后确认 CI 在真实 runner 上跑通 —— **已验证**：
      [run #5](https://github.com/pyyyyp/Bookkeeping/actions/runs/37008580857) 全部步骤 success
      （约 305 秒）。排障过程见 `T-004` 卡的「真实 CI 调试记录」

## 待用户决策（已决）

| 事项 | 结论 | 决定时间 |
|---|---|---|
| 许可证类型 | **MIT** | 2026-10-02 |
| 版权署名 | **`pyyyyp`**（用户指定用 GitHub 用户名）。⚠️ 最初写成 `pythonyunpeng-maker`——那是**换远端之前**那个仓库的账号；换成 `pyyyyp` 名下的远端后已更正 `LICENSE` | 2026-10-02 |
| 是否现在就公开 | 公开。⚠️ 已提醒：内容一推上去即公开、git 历史难以收回 | 2026-10-02 |

## 推送过程（2026-10-02）——先失败一次，换远端后成功

**第一次尝试（失败）**：`origin` 指向用户最初给的
`https://github.com/pythonyunpeng-maker/Bookkeeping.git`，推送被拒：

```
remote: Permission to pythonyunpeng-maker/Bookkeeping.git denied to pyyyyp.
fatal: unable to access '...': The requested URL returned error: 403
```

**原因**：本机 Git 凭据管理器缓存的是**另一个 GitHub 账号（`pyyyyp`）**的凭据，
它对那个仓库没有写权限。本地提交完好、远端仍为空——**没有任何东西丢失**。

**Agent 刻意没做的事**：不修改或清除用户机器上的凭据、不尝试切换到其他账号、
不做任何强制推送。这类操作涉及用户的账号与授权，必须由用户决定。
（用户随后选择「换远端 URL」，换成其缓存凭据对应的账号下的仓库。）

**第二次尝试（成功）**：`origin` 改为 `https://github.com/pyyyyp/Bookkeeping.git`，
空仓库、写权限经 `--dry-run` 确认后推送成功。**远端默认分支自动成为 `develop`**
（空仓库首次推送的分支），因此访客在仓库首页看到的就是含 README 的当前状态；
`main` 仍停在初始骨架，按项目规则留到发布时再接受来自 develop 的合并。

**⚠️ 可见性**：该仓库是**公开**的。此前已就此提醒过用户（内容一推上去就是公开的，
且 git 历史难以收回）。

## 完成情况

- 提交: 见 `90-trace/traceability.md` 的 T-005 行
- 未决:
  1. `main` 落后于 `develop`（初始骨架），等发布时再合并——这是刻意的，不是遗漏
  2. `PRIVACY.md` 的「导出与删除」具体做法待 Q-006（当前也确实没有任何数据可导出）
- 已关闭: 推送阻塞、clone 构建验证、CI 真实运行验证
- **署名更正（用户 2026-10-02 要求）**：`LICENSE` 的版权权利人由
  `pythonyunpeng-maker` 改为 **`pyyyyp`**。原因：最初给出署名时远端还是
  `pythonyunpeng-maker/Bookkeeping`，后来换成了 `pyyyyp` 名下的仓库，署名没跟着改。
  ⚠️ 上文「推送过程」一节里**保留**了旧账号名与 GitHub 的原话报错——
  那是历史事实的引用，改掉就变成篡改记录，不能为了整齐而改

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| ~~干净目录 clone 后构建~~ | **已关闭**：实测 `BUILD SUCCESSFUL in 10s` | — | — |
| ~~CI 在真实 runner 上跑通~~ | **已关闭**：run #5 全绿 | — | — |
| `PRIVACY.md` 里「导出与删除」的具体做法 | Q-006 未定案；当前版本也确实没有任何数据可导出 | — | Q-006 |
| `main` 未同步到 `develop` | 项目规则：`main` 只在发布时接受 develop 的合并 | — | 首次发布时 |
