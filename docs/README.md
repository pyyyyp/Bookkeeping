# docs/ · 事实源目录

> 本目录是项目**唯一权威**的信息来源。`AGENTS.md` 规定行为规则，本目录承载项目事实。
> 代码与文档不一致时，**以本目录为准**，并立即修正代码。

## 目录职责

| 目录 | 内容 | 谁维护 |
|---|---|---|
| `00-charter/` | 愿景、范围、非目标、通用语言术语表、技术基线 | 人 + Agent（首次启动时建立） |
| `10-requirements/` | 需求条目（用户故事 + GWT 验收标准）、非功能需求 | Agent 写，人确认 |
| `20-domain/` | 限界上下文、上下文映射、聚合与不变式、领域事件、未决问题 | Agent 写，人确认 |
| `30-architecture/` | ADR（架构决策记录）、模块依赖图 | Agent 写，人确认 |
| `40-tasks/` | 任务卡（= 一个分支 = 一组内聚提交） | Agent |
| `50-contracts/` | 外部接口契约（OpenAPI / Proto） | 后端提供 |
| `60-runbooks/` | 构建、签名、发布流程 | 人 + Agent |
| `90-trace/` | 追溯矩阵（需求 ↔ 领域 ↔ 任务 ↔ 提交 ↔ 测试）+ `history.md`（逐卡历史归档，从容器交接件搬来） | Agent |

## 编号规则（只增不减、不复用）

| 前缀 | 含义 | 格式 | 文件命名 |
|---|---|---|---|
| `REQ-` | 需求 | `REQ-004` | `REQ-004-<kebab-slug>.md` |
| `AC-` | 验收标准 | `REQ-004/AC-2` | 写在需求文件内 |
| `Q-` | 未决问题 | `Q-003` | 全部记在 `20-domain/open-questions.md` |
| `ADR-` | 架构决策 | `ADR-0007` | `ADR-0007-<kebab-slug>.md` |
| `T-` | 任务 | `T-012` | `T-012-<kebab-slug>.md` |
| `E-` | 领域事件 | `E-OrderPlaced` | 记在 `<context>-events.md` |

废弃的编号标注 `状态: 已废弃（由 xxx 取代）`，**不删除文件**。

## 模板用法

以 `_TEMPLATE-` 开头的文件是模板，**不要直接改名使用**，而是复制内容到新文件：

```bash
# Git Bash / WSL / macOS / Linux
cp docs/10-requirements/_TEMPLATE-REQ.md docs/10-requirements/REQ-001-member-registration.md
cp docs/30-architecture/_TEMPLATE-ADR.md docs/30-architecture/ADR-0001-tech-stack.md
cp docs/40-tasks/_TEMPLATE-TASK.md docs/40-tasks/T-001-init-project.md
```

```powershell
# Windows PowerShell
Copy-Item docs\10-requirements\_TEMPLATE-REQ.md  docs\10-requirements\REQ-001-member-registration.md
Copy-Item docs\30-architecture\_TEMPLATE-ADR.md  docs\30-architecture\ADR-0001-tech-stack.md
Copy-Item docs\40-tasks\_TEMPLATE-TASK.md        docs\40-tasks\T-001-init-project.md
```

> 新建文件后**必须**把模板里的 `XXX` / `<占位符>` 全部替换为真实编号与术语，
> 并删除「示例（…后删除本块）」注释块。留下未替换的占位符等于文档没写。

## 文档更新触发矩阵

| 变更类型 | 必须同时更新 |
|---|---|
| 新增/修改业务规则 | `glossary.md`（若有新词）+ `<context>-model.md` + 对应 `REQ-*.md` |
| 新增业务术语 | `glossary.md` |
| 修改聚合边界或不变式 | `<context>-model.md` + 新增 `ADR` + 检查 `context-map.md` |
| 新增/修改限界上下文 | `context-map.md` + `settings.gradle.kts` + `module-graph.md` + 新增 ADR |
| 引入/更换第三方库 | 新增 `ADR` + `tech-baseline.md` + `libs.versions.toml` |
| 修改外部接口契约 | `50-contracts/` + 受影响上下文的 Mapper + 新增 ADR |
| 新增模块 | `settings.gradle.kts` + `module-graph.md` + 新增 ADR |
| 新增非功能约束 | `10-requirements/nfr.md` |
| 完成任务 | `90-trace/traceability.md` + 任务卡勾选 |
| 发现未决问题 | `20-domain/open-questions.md` |
| 修改发布流程 | `60-runbooks/release.md` |
| **引入权限 / 数据收集 / 上传 / 新的本地存储** | **`PRIVACY.md`（同一提交内）** + 受影响上下文的 `REQ-*.md` + 新增 `ADR` |
| **改变数据存放位置或导出方式** | **`PRIVACY.md`（同一提交内）** + 更新 `open-questions.md` 的 Q-006 |

> `PRIVACY.md` 那一行是**硬要求**：它是「与代码对应的说明书」，不是愿景宣言。
> 只在功能落地时才补写隐私说明，等于让使用者在不知情的情况下先跑了一段时间。

**规则**：任何提交都必须至少触及一行文档。纯代码提交说明跳过了阶段 B。
