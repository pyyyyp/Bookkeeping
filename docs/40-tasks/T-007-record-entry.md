# T-007 记一笔账目条目（端到端）

- 状态: 待开始
- 需求: `REQ-001`
- 上下文: Ledger
- 影响聚合: `LedgerEntry`
- 依赖: 无（工程基础设施 T-001 ~ T-006 均已就绪）
- 分支: `feat/T-007-record-entry`
- 预估: 1 天 | 实际:

> **一个任务 = 一个分支 = 一组内聚提交**，且必须能独立构建通过。

## 目标

把 **`REQ-001` 的记账链路第一次打通**：从「没有任何业务代码」变成
**能在界面上记一笔支出或收入，并在列表里看到它、重启后还在**。

这是本项目第一条真正穿过全部层的路径（domain → data → application → presentation → DI）。
它同时也是 `:core:data` 第一个 `@Entity` 的来源——**表结构由 `LedgerEntry` 这个聚合倒推，
不是为了「把 Room 用起来」而建表**。

## 变更清单

> 先列出来，实现时逐项勾选。与预期不符时说明原因。

- [ ] `feature:ledger` 的 domain：`LedgerEntry`（聚合根）、`LedgerEntryId`、`CategoryId`、
      `Category`、`Note`、`LedgerEntryRepository` 接口、预置分类清单
- [ ] domain 测试：不变式正反用例（先红后绿）
- [ ] application：`RecordLedgerEntryUseCase`、`ObserveRecentEntriesUseCase` + Fake 仓储测试
- [ ] `:core:data`：`LedgerEntryEntity`（Room）、`LedgerEntryDao`、Mapper、
      `LedgerEntryRepositoryImpl`、数据库装配（**第一个 `@Database`**）、迁移策略说明
- [ ] presentation：`RecordEntryViewModel` + `RecordEntryUiState` + Compose 记账界面
- [ ] 列表：`LedgerListViewModel` + Compose 列表（按 `occurredAt` 倒序）
- [ ] DI 装配（Hilt：module 绑定仓储与用例）
- [ ] 端到端冒烟：在模拟器上真的记一笔，重启后仍在（截图/日志为证）

## 验收

> 只能**引用** `REQ-001` 里的 AC 编号，不在这里重新定义。

- [ ] `REQ-001/AC-1` 记一笔支出 —— 验证方式：自动化（`LedgerEntryTest` + `RecordLedgerEntryUseCaseTest`）
      + 手工冒烟（模拟器截图）
- [ ] `REQ-001/AC-2` 记一笔收入 —— 验证方式：自动化（用例测试参数化覆盖两种方向）
- [ ] `REQ-001/AC-3` 金额必须是正数且 ≤ 两位小数 —— 验证方式：自动化（`MoneyTest` 已覆盖非负；
      UI 层解析测试覆盖 0 / 负数 / 三位小数）
- [ ] `REQ-001/AC-4` 必须选择分类 —— 验证方式：自动化（`LedgerEntryTest` 断言 `CategoryRequired`）
- [ ] `REQ-001/AC-5` 补记（发生时间可改过去） —— 验证方式：自动化（用例测试：`occurredAt` 为过去时被原样保存）
- [ ] `REQ-001/AC-6` 备注可选 —— 验证方式：自动化（`LedgerEntryTest` 覆盖 null 与 200 字符边界）
- [ ] `REQ-001/AC-7` 列表按发生时间倒序 —— 验证方式：自动化（仓储/DAO 查询测试用乱序录入数据）
- [ ] `REQ-001/AC-10` 条目持久化 —— 验证方式：**手工冒烟**（记一笔 → 杀进程 → 重开 → 仍在）

## 测试清单

> 测试名用**业务语言**，写成完整的行为描述。

- [ ] `LedgerEntryTest.`金额为 0 时返回 AmountNotPositive``
- [ ] `LedgerEntryTest.`未选分类时返回 CategoryRequired``
- [ ] `LedgerEntryTest.`备注超过 200 码点时返回 NoteTooLong``
- [ ] `LedgerEntryTest.`备注为空时条目仍然有效``
- [ ] `RecordLedgerEntryUseCaseTest.`记录支出时账本新增一条支出条目``
- [ ] `RecordLedgerEntryUseCaseTest.`记录收入时方向被如实保存``
- [ ] `RecordLedgerEntryUseCaseTest.`补记过去的日期时发生时间不被改成现在``
- [ ] `RecordLedgerEntryUseCaseTest.`领域错误被映射为可展示的提示而不是崩溃``
- [ ] `ObserveRecentEntriesUseCaseTest.`录入顺序与发生顺序相反时按发生时间倒序返回``
- [ ] `LedgerEntryRepositoryImplTest.`保存后重新读取的条目与保存前等价``（room in-memory 或 fake）

## 实现顺序

```
1. 领域模型（值对象 → 聚合根）           ← 不碰任何 Android 代码
2. 领域测试（先红后绿）
3. 仓储接口（domain）
4. 用例 + 用例测试（Fake 仓储）
5. 数据实现（Entity → DAO → Mapper → RepositoryImpl）+ 测试
6. ViewModel + UiState
7. Compose UI（记账界面 + 列表）
8. DI 装配（Hilt）
9. 端到端冒烟（模拟器：记一笔 → 重启 → 仍在）
```

## 完成情况

- 提交:
- 未决: `Q-021`（未来时间的账，本卡不校验）、`Q-020`（视觉暂用 M3 默认）
- 备注: 本卡会引入**第一个 Room `@Entity` 与第一版 schema**——
  提交前必须检查 `git status`，不要把失败的构建留下的 schema 产物一起提交（`T-002` 踩过）

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| | | | |
