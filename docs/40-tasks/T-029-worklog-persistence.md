# T-029 工时持久化（`REQ-014/AC-6`、`AC-7`）

- 状态: 待开始
- 需求: `REQ-014`（补上它当初推迟的持久化）+ 新增 AC-6 / AC-7
- 上下文: Worklog（数据层）+ `:app`（数据库与迁移）
- 影响聚合: 无（加表）
- 依赖: `T-027`（模型）、`ADR-0007`（实体/DAO 住上下文、`@Database` 住 `:app`）
- 分支: `feat/T-029-worklog-persistence`
- 预估: 1 轮 | 实际:

> **一个任务 = 一个分支 = 一组内聚提交**，且必须能独立构建通过。

## 目标

让工时**真的存下来** —— 做完它，"打开 App 就能算出我这个月的工资"只剩**录入界面**这一块。

## 变更清单

- [ ] `WorkSessionEntity`（表 `work_session`）+ `WorkSessionDao` + `WorkSessionMapper`（`R6`）
- [ ] `WorkSessionRepository`（domain 接口 + data 实现）：`record` / `sessionsIn`
- [ ] `WorklogReader` 的**真实实现**（读 DAO）
- [ ] `@Database`：`version = 2 → 3` + `MIGRATION_2_3` + 版本历史注释
- [ ] DI：`WorklogModule`（端口绑定）+ `DatabaseModule`（DAO）
- [ ] 测试：Mapper 单测 + **仪器化 DAO 测试**（真 SQLite）
- [ ] 真机验证 `AC-7`：装上 3 版 → `user_version = 3` + **已有账目一行不少、分毫不差**
- [ ] 追溯矩阵：两条新 AC + `T-029` 行

## ⚠️ 一处必须做的签名改动：端口改成 `suspend`

`WorklogReader.attendedDays` 现在是**同步**的（`T-027` 的取舍：本地纯查询）。
但它的真实实现要**读 Room**，而 Room 的查询是 `suspend` —— 阻塞主线程是被禁止的。

所以**端口改成 `suspend`**。判断规则写下来：

> **端口要不要 `suspend`，取决于实现是否碰 IO。**
> `WorkCalendar`（读 assets、内存里查）**保持同步**；`WorklogReader`（读数据库）**必须 `suspend`**。

`T-027` 里"同步查询没有窗口"那条理由说的是**最终一致性**（否决事件快照），
和"能不能阻塞线程"是两件事 —— 改成 `suspend` 不影响那条理由。
连带改动：`LoadPayslipUseCase`、它的测试、以及两个 fake。

## 验收

- [ ] `REQ-014/AC-6` 存得住、读回来一致（含 `Running` 的空结束时刻）
- [ ] `REQ-014/AC-7` 升级不丢数据（真机：`user_version` 2 → 3，账目分毫不差）
- [ ] 迁移 SQL 与 Room 生成的 `3.json` **逐字对应**
- [ ] 仪器化 DAO 测试通过（真 SQLite）
- [ ] 门禁：`test` / `detekt` / `lintDebug` / `assembleDebug` / `assembleRelease` /
      两条架构校验 / 追溯校验

## 完成情况

- 提交:
- 未决: 录入界面（`P` 的一部分）；`Q-024`/`Q-025`/`Q-026` 仍在
- 备注:

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| 录入界面 | 先让存储真实，再谈怎么填 | — | 下一张卡 |
| 状态转换的写路径（confirm/discard） | v1 的手工录入**直接是已确认**（`BR-3`），围栏流程才需要转换 | 按推荐默认值 | 做围栏时（`Q-024` 定案后） |
