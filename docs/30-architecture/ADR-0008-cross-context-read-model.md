# ADR-0008 跨上下文读模型的数据通路

- 状态: **已接受**（2026-10-02）
- 决策者: 用户（需求选择）+ Agent（技术方案）
- 关联: `REQ-002`、`ADR-0001`（Insight 是读模型）、`ADR-0007`（数据层归属）、R2 / R5 / R7

## 背景

`REQ-002` 要在记账界面上显示**本月**的支出合计、收入合计与结余。
数据在 `:feature:ledger` 的 `ledger_entry` 表里，而按 `ADR-0001`，
汇总属于 **Insight**（支撑域，读模型）。

于是出现一个真实的架构冲突：

- R2 规定 **`feature:*` 之间不得互相依赖**；
- 但"消费者"（Insight）需要"生产者"（Ledger）的数据。

**这是本项目第一次出现跨上下文的数据读取**，所以需要一个明确的通路，
而不是当场找个能编译的写法。

## 决策

**端口（契约）住在 `:core:domain` 共享内核；实现在生产方 `:feature:ledger`；装配在 `:app`。**

```kotlin
// :core:domain  —— 只用到内核自己的类型
interface LedgerTotalsReader {
    suspend fun totalsIn(range: TimeRange): Outcome<MonthlyTotals>
}
```

| 角色 | 模块 | 做什么 |
|---|---|---|
| 契约 | `:core:domain` | 定义 `LedgerTotalsReader` + `MonthlyTotals` + `SignedMoney` |
| 生产者 | `:feature:ledger` | 用 Room 的一条 `SUM` 查询实现该端口（它拥有那张表） |
| 消费者 | `:feature:insight` | `LoadMonthlyTotalsUseCase` 把"年月"换成 `TimeRange` 后调用端口 |
| 装配 | `:app` | DI 里把生产者的实现绑给端口（与 `ADR-0007` 的分工一致） |

依赖方向：`ledger → core:domain ← insight`，`app → 两者`。**没有任何 feature → feature 的边**，
R2 得以保持；`ledger` 与 `insight` 都不知道对方存在，只知道内核里的一个接口。

## 被否方案

### 方案 A：Insight 直接读同一张 Room 表（`InsightDao` 写 SQL 查 `ledger_entry`）

- **优点**：最省事，不需要新类型，CQRS 里读模型直接读库是常见做法。
- **为什么否掉**：Insight 会**写死 Ledger 的表名与列名**。
  Ledger 改一次 schema（例如金额分单位改名），Insight 静默算错或崩溃，
  而**编译期不会报错**——这正是 `ADR-0007` 已经反对过的那种"跨模块碰别人的表"。
  读模型的"只读"不能保护它免受 schema 变化的影响。

### 方案 B：新建 `:core:readmodel` 模块放端口

- **优点**：内核保持"只有通用值对象"的纯净度。
- **为什么暂不选**：为一个接口新增一个 Gradle 模块，会让模块图与构建脚本复杂化，
  而当前只有**一个**这样的契约。**如果以后出现第二个、第三个跨上下文读契约，就升级到这个方案**——
  升级成本很低（把接口与两个值对象搬过去），所以现在不做不算欠债。

### 方案 C：把合计算在 Ledger 里，Insight 只做展示

- **优点**：完全没有跨上下文问题。
- **为什么否掉**：那就等于取消 Insight 这个上下文——`ADR-0001` 明确它存在。
  而且"什么算本月"（时区、半开区间、按发生时间）是**汇总口径**，属于统计语义，
  不该由账本上下文决定。

## 影响

- **共享内核变大了**：多了 `MonthlyTotals`、`SignedMoney`、`LedgerTotalsReader`。
  内核的准入标准因此明确为：**被两个以上上下文同时需要的东西**。
- `SignedMoney` 的引入理由（为什么不能把 `Money` 改成允许负数）写在 `insight-model.md`；
  这是内核里第一个"带符号"的钱类型，未来要做"账户余额"时可以直接复用。
- 端口是 `suspend` 且返回 `Outcome`：与既有仓储接口一致（不抛异常跨层）。
- **未做**：数据缓存、汇总表、事件驱动的增量更新。读数一致性按"每次重新查"保证
  （条目量级是个人记账，见 `insight-model.md` 的说明）。

## 复审条件

出现下列任一情况时重新审视本决策：

1. 出现**第二个**跨上下文读契约 → 升级到方案 B（`:core:readmodel`）；
2. 汇总性能成为真实问题（实测而非感觉）→ 那时再谈缓存/物化视图；
3. 跨上下文**写**的需求出现（本 ADR 只谈读）→ 那是另一个决策，很可能要用领域事件。
