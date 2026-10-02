# Insight 读模型（v0：本月收支合计）

> 对应需求 `REQ-002`。本文件只写**模型**，配置与界面见任务卡 `T-009`。

## 为什么 Insight 是「读模型」而不是聚合

`ADR-0001` 把 Insight 定为支撑域，职责是"日/周/月汇总（**读模型，无聚合**）"。
这不是偷懒，而是领域判断：

- **合计没有不变量可守**。你没法让一笔"支出合计"违法——它只是一个计算结果。
  给它加聚合根、加 `Outcome` 守卫、加领域事件，都是把简单事情包装复杂。
- **合计的正确性来自源数据**，不来自自己。真正的规则（金额必须为正、必须有分类）在 `LedgerEntry`
  聚合里，已经守住了。读模型只负责**口径**（哪段时间、按哪个时间字段、负号怎么显示）。

所以这里没有 `AggregateRoot`，只有值对象 + 一个用例 + 一个端口。

## 值对象

### `MonthlyTotals`（新增，Insight）

| 字段 | 类型 | 说明 |
|---|---|---|
| `income` | `Money` | 该月收入合计，非负（空月为 `Money.ZERO`） |
| `expense` | `Money` | 该月支出合计，非负 |
| `net` | `SignedMoney` | `income − expense`，**可以为负** |

- 它是 `data class`，因为它就是一组值：两个合计相等即相等（与 `LedgerEntry` 不同，
  条目有身份，合计没有）。
- `net` **不存储**，由 `income` 与 `expense` 算出（单一事实源：存三个数就可能互相矛盾）。
- 空月用 `MonthlyTotals(Money.ZERO, Money.ZERO, SignedMoney.ZERO)`，不用 `null`：
  "没有记账"与"查不到"是两件事，前者是正常的零，后者走 `Outcome.Err`。

### `SignedMoney`（新增，**共享内核**）

```kotlin
@JvmInline
value class SignedMoney private constructor(val cents: Long) {
    companion object {
        val ZERO = SignedMoney(0)
        fun ofCents(cents: Long) = SignedMoney(cents)
    }
}
```

**为什么必须新增一个类型，而不是把 `Money` 改成允许负数**：

- `Money` 的"非负"是**刻意的**（`ADR-0001` / 内核 KDoc）：一笔交易的金额永远不是负数，
  **方向由 `EntryDirection` 表达**。这条不变量撑住了"金额与方向分开"的建模。
- 但**差额**是另一回事：它不是任何一笔交易的金额，而是一个**带符号的派生量**。
  把两种语义压进一个类型，就等于让"支出 ¥50"可以写成"收入 -¥50"——那正是当初要避免的歧义。
- 因此：`Money` 保持非负；差额用 `SignedMoney`。两者都只含 `cents: Long`，
  转换点只有一处（`MonthlyTotals` 的构造），泄漏面很小。

`SignedMoney.toString()` 的负号**必须**可见（`AC-2`）：`-¥70.00`。
这是唯一一个"格式即语义"的地方——少一个负号，用户会把超支看成结余。

## 端口（跨上下文的数据通路）

```kotlin
// 住在 :core:domain（共享内核），不是 :feature:insight
interface LedgerTotalsReader {
    suspend fun totalsIn(range: TimeRange): Outcome<MonthlyTotals>
}
```

**为什么端口住内核**（决策记录见 `ADR-0008`）：

- `:feature:insight` **不能**依赖 `:feature:ledger`（R2：feature 之间禁止依赖），
  所以消费者与生产者之间必须有一个**双方都依赖**的地方放契约——那就是共享内核。
- 端口只用到内核自己的类型（`TimeRange`、`Outcome`、`MonthlyTotals`、`SignedMoney`），
  没有引入任何框架或外部模型（R3）。

**生产者在 `:feature:ledger`**：它拥有 `ledger_entry` 表，由它实现这个端口
（一条 `SUM` 查询，仍走 Room，不经手任何"把整表读进内存再相加"的做法）。
**装配在 `:app`**：组合根把生产者绑给消费者（与 `ADR-0007` 的 DI 分工一致）。

## 用例

| 用例 | 输入 | 输出 | 规则 |
|---|---|---|---|
| `LoadMonthlyTotalsUseCase` | `yearMonth: YearMonth`（默认由 `Clock` 推出的当月） | `Outcome<MonthlyTotals>` | 把月份换算成 `TimeRange`（本机时区、半开区间，`BR-1`），转交端口 |

- 月份 → 时间范围的换算是**这一层唯一的实质逻辑**，也是唯一需要测试的地方：
  月初、月末、闰年 2 月、跨年（12 月 → 次年 1 月）。
- 用 `java.time.YearMonth` 而不是自己拼 (year, month)：闰年与跨年的边界它已经处理了，
  自己写等于重新发明一遍出错的日历。

## 界面口径（`REQ-002` 的 `AC` 映射）

| 界面元素 | 来源 | 对应 AC |
|---|---|---|
| 支出合计 | `MonthlyTotals.expense` | `AC-1` `AC-3` `AC-4` `AC-5` `AC-6` |
| 收入合计 | `MonthlyTotals.income` | `AC-1` `AC-3` |
| 结余 | `MonthlyTotals.net`（负号可见） | `AC-1` `AC-2` |
| 月份切换 | `YearMonth` ± 1 个月；当月时禁用「下一月」 | `AC-6` `BR-7` |
| 空月提示 | `entries.isEmpty()` 且三项为 0 | `AC-3` `BR-6` |

## 明确不做的事

- **不引入缓存**：合计每次重新查询。条目量级在个人记账的规模内，
  缓存会立刻带来"删了一笔但合计没变"的一致性风险（`AC-5` 要求即时）。
- **不做物化视图 / 汇总表**：需要时再说，且那是性能问题不是领域问题。
- **不处理未来日期**：`Q-021` 仍未定案，见 `REQ-002/BR-7`。
