# T-009 本月收支合计（Insight v0）

- 状态: 待开始
- 需求: `REQ-002`
- 上下文: Insight（读模型）+ Ledger（提供端口实现）
- 影响聚合: 无（读模型没有聚合，见 `docs/20-domain/insight-model.md`）
- 依赖: `T-007`（条目数据）、`ADR-0008`（跨上下文读通路）
- 分支: `feat/T-009-monthly-totals`
- 预估: 1 轮 | 实际:

> **一个任务 = 一个分支 = 一组内聚提交**，且必须能独立构建通过。

## 目标

记账界面上一眼看到**当月花了多少、挣了多少、还剩多少**，并能翻看上一个月。
交付后 `REQ-002` 的 7 条验收标准应全部有证据。

> **为什么从 Insight 起手而不是做图表**：合计是"最少但最有用"的统计。
> 没有它，用户要自己把列表加一遍；有了它，后面的分类占比、趋势图才有落点。

## 变更清单

- [x] `:core:domain`：`SignedMoney` 值对象（带符号的差额，**不改 `Money` 的非负性**）
- [x] `:core:domain`：`MonthlyTotals`（income / expense / net，net 由前两者算出）
- [x] `:core:domain`：`LedgerTotalsReader` 端口（`ADR-0008`）
- [x] `:feature:ledger`：端口实现 —— 聚合查询（`COALESCE(SUM(...), 0)` + 半开区间）
      + `LedgerTotalsReaderImpl`（`runCatching` + 取消重抛，与仓储实现同一套）
- [x] `:feature:insight`：`LoadMonthlyTotalsUseCase`（`YearMonth` → `TimeRange`，本机时区半开区间）
- [x] `:feature:insight`：ViewModel + UiState（当前月、三项合计、上/下月；当月禁用「下一月」= `BR-7`）
- [x] `:app`：DI 装配（端口 ← Ledger 的实现）。**编译期即得证**：Hilt 在构建时校验依赖图，
      `assembleDebug` 通过就意味着这个跨模块绑定成立
- [x] 界面：合计区通过记账界面的**通用顶部插槽**放在标题下方。
      ⚠️ 关键约束：Ledger **不能**引用 Insight（R2），所以拼装只能在组合根做 ——
      Ledger 提供插槽 + 「数据变了」的修订号，Insight 提供 `refreshSignal`，两边互不认识
- [x] 保存 / 删除后合计立即刷新（修订号 → 组合根 → `refreshSignal`）
- [x] 冒烟：记一笔 → 合计从 ¥25.00 变 ¥37.50；翻到上一月（9 月 ¥0.00 + 空月提示）
- [x] **顺带修掉一个真缺陷**：`:app` 原本注入 `Clock.systemUTC()`，其 `zone` 是 UTC ——
      那会让「本月」变成 **UTC 的月**，违反 `BR-1`（东八区用户在月初/月末会看到错的合计）。
      已改为 `systemDefaultZone()`。`instant()` 与时区无关，所以**既有行为不变**

## 验收

> 只能**引用** `REQ-002` 里的 AC 编号，不在这里重新定义。

- [ ] `REQ-002/AC-1` 三项合计数值正确 —— 验证：自动化（用例测试 + Fake 端口）
- [ ] `REQ-002/AC-2` 结余为负时负号可见 —— 验证：自动化（`SignedMoney.toString()` 的测试）
- [ ] `REQ-002/AC-3` 空月显示 `¥0.00` 与提示 —— 验证：自动化 + 冒烟
- [ ] `REQ-002/AC-4` 记一笔后合计立即更新 —— 验证：**手工冒烟**（界面状态刷新）
- [ ] `REQ-002/AC-5` 删一笔后合计立即回退 —— 验证：**手工冒烟**
- [ ] `REQ-002/AC-6` 可翻看上一个月并回到当月 —— 验证：自动化（年月换算）+ 冒烟
- [ ] `REQ-002/AC-7` 按**发生时间**归属月份（补记算在发生的那个月）—— 验证：自动化（端口查询的 Fake）+ 冒烟

## 测试清单

- [ ] `MonthlyTotalsTest.`结余为负时是 SignedMoney 而不是 Money``
- [ ] `SignedMoneyTest.`负数的字符串带负号``
- [ ] `LoadMonthlyTotalsUseCaseTest.`月份被换算成本机时区的半开区间``
- [ ] `LoadMonthlyTotalsUseCaseTest.`跨年时上一月是去年 12 月``
- [ ] `LoadMonthlyTotalsUseCaseTest.`空月返回零而不是错误``
- [ ] `LedgerTotalsReaderImplTest.`（Fake DAO）按发生时间汇总，与录入时间无关``

## 实现顺序

```
1. :core:domain 的值对象 + 端口（+ 测试）      ← 先红后绿
2. :feature:ledger 的端口实现（+ Fake DAO 测试）
3. :feature:insight 的用例（+ 测试）
4. ViewModel + UiState
5. Compose：合计区 + 月份切换
6. DI 装配
7. 冒烟（记一笔 / 删一笔 / 翻月）
```

## 完成情况

- 提交:
- 未决: `Q-021`（未来时间的账）本卡不处理，见 `REQ-002/BR-7`
- 备注: **不要**顺手加分类占比、趋势图、预算提醒——那些是各自的新需求。
  也**不要**为了"性能"先加缓存或汇总表（`ADR-0008` 的复审条件里有说明）

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| | | | |
