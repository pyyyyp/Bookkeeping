# Payroll · 薪资模型

- 状态: **草稿 v0.1**（`T-026` 落地"日收入 → 工资单"的纯计算）
- 上下文类型: **核心域**
- 模块: `feature:payroll`
- 相关: `REQ-013`、`Q-012`（算法）、`Q-015`（带薪规则，已定案）、`Q-017`/`Q-018`/`Q-019`（已定案）、`REQ-012`（日期类型）

## 这个上下文回答什么

**我这个月该拿多少？** 而且要能**逐日解释** —— 每一天为什么是 1×、2×、3× 还是 0。

## 通用语言

见 `docs/00-charter/glossary.md` 的「Payroll · 薪资」一节（`MonthlySalary` / `DailyRate` /
`PAID_DAYS_PER_MONTH` / `PayMultiplier` / `DailyIncome` / `Payslip` / `amountDue`）。
**`PayRate`（时薪）已废弃，禁止再出现。**

## 算法（Q-012 + Q-015 定案）

```
日薪 = 月薪 ÷ 21.75     ← 整数：月薪（分）× 100 ÷ 2175，四舍五入到分
每日收入 = 日薪 × 倍率(日期类型, 是否出勤)
```

| 日期类型 | 出勤 | 倍率 | 说明 |
|---|---|---|---|
| 工作日 | 出勤 | `1 ×` | 正常上班 |
| 工作日 | **缺勤** | `0` | `Q-015` 定案：缺勤当天不计 |
| 休息日 | 加班 | `2 ×` | `Q-012` |
| 休息日 | 不加班 | `0` | `Q-012`："不加班的周末没有" |
| 法定节假日 | 加班 | `3 ×` | `Q-012` |
| 法定节假日 | 休息 | **`1 ×`** | `Q-015` 定案：法定节假日休息**带薪** |

### ⚠️「日收入之和 ≠ 月薪」是预期的

`21.75 = (365−104)÷12` 是**法定月计薪天数**（年平均口径），而任意单月的实际
工作/休息天数都不可能正好是 21.75。所以：

- 工资单**显示**与月薪的差额（`Payslip.differenceFromSalary`），
- **不硬凑**成月薪 —— 硬凑会让"这个月为什么多了 3 块钱"变成一个无法解释的数字。

这不是妥协，是这类**平均值口径**的固有性质。测试里专门有一条盯着它。

## 模型

```kotlin
// 值对象（住 feature:payroll/domain —— 目前只有 Payroll 用得到，不急着进内核）
data class MonthlySalary(val amount: Money, val effectiveFrom: LocalDate)
data class DailyIncome(val date: LocalDate, val dayType: DayType, val multiplier: PayMultiplier, val amount: Money)
data class Payslip(
    val month: YearMonth,
    val salary: MonthlySalary,
    val dailyRate: Money,
    val days: List<DailyIncome>,
    val amountDue: Money,                  // = Σ days.amount
    val differenceFromSalary: SignedMoney, // 不硬凑，但要说清楚差多少
)

enum class PayMultiplier(val factor: Int) { NONE(0), NORMAL(1), REST_DAY_OVERTIME(2), HOLIDAY_OVERTIME(3) }
```

### 没有聚合

`Payslip` 是**算出来的读模型**：它没有生命周期、没有要守住的不变式，
重算一遍就能得到同一个结果（纯函数）。给它造聚合根，只会多一个可变的壳。
**唯一需要"守"的东西是算法的正确性 —— 那由测试守，不由聚合守。**

### 输入：加班与缺勤**从外面给**

判定"某天是否出勤"是 **Worklog** 的事，而 Worklog 还没实现。
所以本轮的日收入计算把 `worked` / `absent` 当**参数**收进来 ——
**Payroll 不为它造一个假的数据源**，也不去猜。

将来接线时，Worklog 通过内核端口（`ADR-0008` 的读通路）提供"哪些天出勤"，
Payroll 消费；`Q-017`（时长门槛）在那时生效。

## 未决 / 未做

- `Q-004`（结算周期与多份工作是否合并出单）：本轮按**按月**出单；多份工作**未实现**
- 涨薪：`MonthlySalary` 已有 `effectiveFrom`，但"某个月该用哪份月薪"的历史查询**未实现**
- 界面：本轮只交付计算与测试，没有页面
