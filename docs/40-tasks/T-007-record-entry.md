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
- [x] 端到端冒烟：在模拟器上真的记一笔，重启后仍在（截图与日志为证，见下）

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

- 状态: **已完成**（领域 → 数据 → 用例 → ViewModel → Compose → DI 全线打通，并在模拟器上冒烟通过）
- 提交: 见 `90-trace/traceability.md` 的 T-007 行
- **端到端冒烟（2026-10-02，MuMu 模拟器 / Android 12）**：用 adb 真实操作界面，每一步都有截图：

  | 验证 | 操作 | 结果 |
  |---|---|---|
  | `AC-1` 记一笔支出 | 输入 12.50 → 选「餐饮」→ 保存 | 列表出现「支出 餐饮 ¥12.50 2026-10-02」✓ |
  | `AC-2` 记一笔收入 | 切「收入」→ 选「工资」→ 输入 8000 → 保存 | 列表出现「收入 工资 ¥8000.00」✓ |
  | `AC-3` 金额校验 | 输入 `12.345` → 保存 | 提示「金额最多两位小数」，**账本未新增条目** ✓ |
  | `AC-4` 分类必选 | 输入合法金额 8000、不选分类 → 保存 | 红色提示「请选择分类」，账本未新增 ✓ |
  | `AC-5` 补记 | 日期选择器改到 **10-01** → 保存 | 该笔记为 10-01 ✓ |
  | `AC-6` 备注可选 | 三笔都不填备注 | 全部保存成功 ✓ |
  | `AC-7` 按发生时间倒序 | 最后录入的那笔是 **10-01** | 它排在列表**最下面**——顺序由发生时间决定，不是录入顺序 ✓ |
  | `AC-10` 持久化 | `am force-stop`（确认进程已结束）→ 重启 | 条目仍在；`run-as … ls databases/` 看到 `jizhangbao.db` ✓ |

  截图证据在仓库外：`D:\code\Android\.screenshots\t007-*.png`（12 张，含日期选择器与三次错误提示）。
  `AC-8` / `AC-9`（删除）属于 `T-008`，本卡不涉及。

- 三段的证据（测试与门禁）：
  1. 领域层：24 个测试；`verifyDomainPurity` 扫描数 5 → 13，证明 feature 内的 domain 包确实被 R3 覆盖
  2. 用例 + 数据层 + DI：新增 26 个测试（合计 50）；**R5 / R6 / R10 第一次有真实靶子**并实际执行通过；
     Room 第一版 schema 导出入库（`ledger_entry` 表，version 1），**没有** `fallbackToDestructiveMigration`
  3. presentation：全仓门禁（`detekt` / `lintDebug` / `testDebugUnitTest` / `assembleDebug` /
     `verifyDomainPurity` / `checkModuleDependencies`）全绿，合计 **56 个测试 0 失败**
- 过程中发现并处理的真问题（**全部没有压规则**）：
  1. **共享内核缺陷**：`DomainError` 是 `sealed`，而 Kotlin 禁止跨模块实现 sealed 类型
     （编译器原话：`Extending sealed classes or interfaces from a different module is prohibited`），
     与它自己「公共父类型」的 KDoc 矛盾 → 改为 `interface`，记 `ADR-0006`
  2. **文档与硬规则矛盾**：`module-graph.md` 与 `core:data` 的注释都写着「第一个 @Entity 进 `:core:data`」，
     而 R7 禁止 core 依赖 feature → 记 `ADR-0007`，实体改住上下文模块、`@Database` 住组合根
  3. detekt 前后共抓到 **7 条**真问题（return 过多 ×2、魔数、函数过长、文件函数过多、
     `TooGenericExceptionCaught`、`SwallowedException`）→ 全部重构修掉
  4. lint 抓到外层 `Scaffold` 的内边距无人使用 → 去掉多余的那层
  5. **环境陷阱**：C 盘剩余空间为 0，导致 Gradle 的 `JdkImageTransform` 里 `jmod` 失败，
     报错看起来像工具链坏了。清掉可再生的 transforms 缓存并停守护进程后恢复（已记入交接件）
- 未决 / 已知缺口（不掩盖）：
  - 存储异常的原因**没有被记录**（`DomainError.Technical.Storage` 不带载荷，项目还没有日志抽象）——
    等 `core:common` 有日志后应在 `LedgerEntryRepositoryImpl.storageOutcome` 里补上
  - 单元测试用内存 fake DAO，**测不到 SQL 本身**；SQL 的正确性由 Room 编译期校验 +
    上面的模拟器冒烟共同保证。DAO 的自动化测试需要 `androidx.test` 系列依赖（不在版本目录里，
    引入需单独裁决）
- 未决: `Q-021`（未来时间的账，本卡不校验）、`Q-020`（视觉暂用 M3 默认）
- **反向验证（领域层）**：故意破坏两处（注释掉 `record` 里的金额守卫；把 `Note` 的码点计数
  改成 UTF-16 码元计数），**恰好对应的 2 个测试失败**，其余 22 个照常通过 → 还原后全绿
- 备注: 本卡引入了**第一个 Room `@Entity` 与第一版 schema**——
  提交前已检查 `git status`，确认没有把失败的构建留下的 schema 产物混进去（`T-002` 踩过）
- ⚠️ 给下一轮的提醒：**测试结果 XML 可能是陈旧的**。本轮编译失败时，
  `test-results/` 里仍躺着上一次运行的旧结果，看起来像「测试跑了且有失败」。
  读测试结果前先确认任务真的执行过（或先删掉旧结果目录）

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| | | | |
