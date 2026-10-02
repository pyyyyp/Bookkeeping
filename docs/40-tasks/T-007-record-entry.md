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

- [x] `feature:ledger` 的 domain：`LedgerEntry`（聚合根）、`LedgerEntryId`、`CategoryId`、
      `Category`、`Note`、`LedgerEntryRepository` 接口、预置分类清单
- [x] domain 测试：24 个（`LedgerEntryTest` 12 / `CategoryCatalogTest` 8 / `NoteTest` 4）
      —— ⚠️ **不声称「先红后绿」**：实现先写好，因此改用**反向验证**证明测试有效（见下）
- [x] application：`RecordLedgerEntryUseCase`、`LoadRecentEntriesUseCase` + Fake 仓储测试
      （用例名从 `Observe...` 改为 `Load...`：本卡不做反应式观察，**名字要说真话**）
- [x] 数据层 —— ⚠️ **与原计划不同，按 `ADR-0007`**：实体 / DAO / Mapper / 仓储实现
      住在 `feature:ledger` 的 `data` 包，**唯一的 `@Database` 住在 `:app` 的 `data` 包**。
      原计划「第一个 `@Entity` 进 `:core:data`」与 R7 直接冲突（core 不得依赖 feature），
      详见该 ADR
- [x] DI 装配（Hilt）：`feature:ledger/di/LedgerModule`（仓储绑定）+
      `:app` 的 `DatabaseModule`（数据库 / DAO / 时钟）——ViewModel 的绑定随 presentation 一起
- [x] presentation：`LedgerViewModel` + `LedgerUiState` + Compose 记账界面
      （表单 / 列表 / 日期选择拆成三个文件——detekt 的 LongMethod 与 TooManyFunctions
      把「一个文件既管骨架又管细节」这件事直接拦下了）
- [x] 列表界面：Compose 列表（顺序由 SQL 的 `ORDER BY` 保证，界面**不重新排序**）
- [ ] 端到端冒烟：在模拟器上真的记一笔，重启后仍在（截图/日志为证）
      —— ⚠️ **未做**：本轮最后卡在环境问题上（见下），界面只做到「编译通过 + 门禁全绿」

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

- 提交: 见 `90-trace/traceability.md` 的 T-007 行
- 状态: **进行中**——领域层已完成；application / data / presentation / DI / 冒烟未做
- 已完成部分的证据（2026-10-02）：
  - `:feature:ledger:testDebugUnitTest`：**24 个测试 0 失败**（新写的领域测试）
  - 全仓门禁：`detekt` / `lintDebug` / `testDebugUnitTest` / `assembleDebug` /
    `verifyDomainPurity` / `checkModuleDependencies` 全部通过（合计 30 个测试 0 失败）
  - `verifyDomainPurity` 的扫描数从 **5 → 13 个文件**：新写的 8 个 domain 源文件
    **确实进入了 R3 扫描范围**——这条原本只是设计意图，现在被数字证实
  - **反向验证**：故意破坏两处（注释掉 `record` 里的金额守卫；把 `Note` 的码点计数
    改成 UTF-16 码元计数），**恰好对应的 2 个测试失败**，其余 22 个照常通过 → 还原后全绿
- 过程中发现并处理的两个真问题：
  1. **共享内核缺陷**：`DomainError` 是 `sealed`，而 Kotlin 禁止跨模块实现 sealed 类型
     （编译器原话：`Extending sealed classes or interfaces from a different module is prohibited`），
     与它自己「公共父类型」的 KDoc 矛盾 → 改为 `interface`，记 `ADR-0006`（含反向验证：
     还原成 `sealed` 会再次编译失败）
  2. **detekt 抓到真实问题**：`record` 有 3 个 return（上限 2）→ 改为「`?:` 守卫 +
     `if/else`」两个 return；**没有压规则**。中途试图改成校验表时踩到
     「`when` 的分支条件不做智能转换」的编译错误，已修正并把结论写进代码注释
- 第二段（application + 数据层 + DI）的证据（2026-10-02）：
  - `:feature:ledger` **50 个测试 0 失败**（领域 24 + 用例 11 + Mapper 7 + 仓储实现 8）
  - 全仓门禁通过：`detekt` / `lintDebug` / `testDebugUnitTest`（**56 个**，含 6 条架构断言）/
    `assembleDebug` / `verifyDomainPurity`（13 个 domain 文件）/ `checkModuleDependencies`（62 条声明）
  - **R5 / R6 / R10 第一次有了真实靶子**：此前它们针对的 `data` / `presentation` 包还不存在。
    本轮有了 `data` 与 `di` 包，断言**实际执行**（测试结果时间戳可查）且通过
  - Room 导出了第一版 schema：`app/schemas/com.jizhangbao.app.data.JizhangbaoDatabase/1.json`
    （表 `ledger_entry`，version 1），已入库；**没有** `fallbackToDestructiveMigration`
  - `detekt` 又抓到两条真问题：`record` 的 3 个 return（已改）、以及仓储实现的
    `TooGenericExceptionCaught` + `SwallowedException`。后者**没有压规则**：
    改成 `runCatching` + 显式重抛 `CancellationException` 的写法，并写明理由
- **已知缺口（不是「暂时这样」就完了）**：存储异常的原因**没有被记录**——
  `DomainError.Technical.Storage` 不带载荷，项目也还没有日志抽象，
  于是「磁盘满」与「数据库损坏」在上层看起来一样。
  等 `core:common` 引入日志后，应在 `LedgerEntryRepositoryImpl.storageOutcome` 里记下原因（不含 PII）。
  **跟踪：`core:common` 的日志抽象任务（尚未建卡）**
- **测试覆盖的诚实边界**：单元测试用的是内存 fake DAO，因此**测不到 SQL 本身**
  （排序、LIMIT、DELETE 条件由 Room 编译期校验 + 模拟器手工冒烟确认）。
  真要做 DAO 的自动化测试需要 `androidx.test` 系列依赖，而它们**不在版本目录里**——
  引入新依赖需要单独裁决，本卡不擅自加
- 未决: `Q-021`（未来时间的账，本卡不校验）、`Q-020`（视觉暂用 M3 默认）
- 备注: 本卡引入了**第一个 Room `@Entity` 与第一版 schema**——
  提交前已检查 `git status`，确认没有把失败的构建留下的 schema 产物混进去（`T-002` 踩过）
- ⚠️ 给下一轮的提醒：**测试结果 XML 可能是陈旧的**。本轮编译失败时，
  `test-results/` 里仍躺着上一次运行的旧结果，看起来像「测试跑了且有失败」。
  读测试结果前先确认任务真的执行过（或先删掉旧结果目录）

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| | | | |
