# T-017 分类下钻（`REQ-007`）

- 状态: **已完成**（`AC-1`/`AC-2`/`AC-4` 真机验证；⚠️ `AC-3` 的**空清单分支**没在真机构造出来 —— 见追溯矩阵那一行的说明）
- 需求: `REQ-007`（4 条 AC）
- 上下文: Ledger（新查询/用例/界面）+ Insight（占比行可点）+ `:app`（拼装）
- 影响聚合: 无（只读；不碰 `LedgerEntry` 的任何不变式）
- 依赖: `REQ-005`（占比清单）、`REQ-006`（只读清单的失败提示沿用 `SaveFailure.LoadFailed` 的思路）
- 分支: `feat/T-017-category-drilldown`
- 预估: 1 轮 | 实际:

> **一个任务 = 一个分支 = 一组内聚提交**，且必须能独立构建通过。

## 目标

点「支出构成」里的一行 → 看到该分类在该月的条目清单（只读），且**金额与占比行一致**。

## 变更清单

- [ ] 内核：`CategoryAmount` 增加 `categoryKey: String`（**不透明**，见 `BR-4`），
      并在 KDoc 里写清"消费方不得解释它"；`LedgerTotalsReaderImpl` 构造占比时填上它
- [ ] Ledger 数据层：`LedgerEntryDao` 新增按分类 + 半开区间的查询；仓储新增 `inCategory(...)`，
      复用 `RecentEntries`（含"坏行跳过并计数"，`REQ-006/AC-3`）
- [ ] Ledger 用例：`LoadCategoryEntriesUseCase`（`BR-5`：**新用例，不是新端口**）
- [ ] Ledger 界面：`CategoryEntriesViewModel` + 状态 + **只读**清单（`BR-3`）+ 空态（`AC-3`）
- [ ] Ledger 对外入口：一个公开的 `LedgerCategoryEntriesDialog(categoryKey, month, onDismiss)`
      —— 内部自己拿 ViewModel，组合根不必知道 Ledger 的 DI 细节
- [ ] Insight：占比行变成可点，回调把（标识 + 月份）交给组合根；**Insight 不解释标识**
- [ ] `:app`：持有"当前下钻的是哪一行"，渲染 Ledger 的清单（R2 要求它来做）
- [ ] 追溯矩阵：`REQ-007` 的 4 条 AC 各一行

## 验收

- [ ] `REQ-007/AC-1` 点一行 → 清单标题说清分类与月份，内容是那几笔
- [ ] `REQ-007/AC-2` 清单金额之和 == 占比行金额（**用真机数据人工核一遍**：
      娱乐 40000.00 + 20.00 应等于占比行的那个数）
- [ ] `REQ-007/AC-3` 空态说清楚
- [ ] `REQ-007/AC-4` 关闭后合计/占比/列表都没有变化
- [ ] 门禁：`testDebugUnitTest` / `detekt` / `lintDebug` / `assembleDebug` / 两条架构校验 / 追溯校验

## 实现顺序

```
1. 内核 CategoryAmount 加不透明标识（先跑内核测试，确认旧断言都跟着改）
2. Ledger 数据层 + 仓储 + 用例（+ 单测；SQL 的正确性另加真库用例）
3. Ledger 界面（VM + 只读清单 + 空态）
4. Insight 让行可点 + :app 拼装
5. 构建 + 门禁
6. 真机冒烟（重点：AC-2 的数字一致性、AC-4 关闭后不变）
```

## 完成情况

- 提交: `feat/T-017-category-drilldown` → `develop`（见本轮提交）
- 未决: 无
- 备注: ⚠️ 三条边界 —— **口径必须与占比同源**（`BR-1`，否则同一分类两个数）；
  **清单只读**（`BR-3`，不要顺手加编辑按钮）；**Insight 不得解释那个标识**（`BR-4`，
  它一旦认出"这是分类 id"，跨上下文的契约就漏了）
- ✅ 期间发现并修掉一个**真的口径隐患**：`YearMonth.toTimeRange` 原本是 Insight 的 `internal`，
  Ledger 做下钻只能**照抄一遍** —— 那就是两套口径的开始。已搬进 `:core:domain`，
  现在合计、占比、下钻调的是同一个函数，**不一致在结构上不可能**
- ⚠️ 过程记录：这轮我**先改了代码才建分支**（违反「一张卡 = 一个分支」的顺序），
  且中途在 `develop` 上留下过编译不过的状态（detekt 的 `ReturnCount` 抓到我的重复早返回，
  编译期抓到缺 import 与一个多余的 `}`）。已全部修正后才提交 —— 见交接件教训 27

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| | | | |
