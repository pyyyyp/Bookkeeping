# Calendar · 工作日历模型

- 状态: **草稿 v0.1**（`T-025` 落地中）
- 上下文类型: **支撑域**
- 模块: `feature:calendar`
- 相关: `REQ-012`、`ADR-0011`、`Q-015`、`Q-016`

## 这个上下文回答什么

一个问题：**某一天，在计酬口径下是什么日子？**

Payroll 要拿它算倍数（工作日 1× / 休息日加班 2× / 法定节假日加班 3×），
Worklog 要拿它判"哪天算加班"。**除了 Calendar，没有人有权回答这个问题。**

## 通用语言

见 `docs/00-charter/glossary.md` 的「Calendar · 工作日历」一节：
`DayType` / `WORKDAY` / `REST_DAY` / `STATUTORY_HOLIDAY` / 调休 / `HolidayData` / `missingYear`。

## 模型

### 值对象（住 `core:domain`，因为要跨上下文传递）

```kotlin
enum class DayType { WORKDAY, REST_DAY, STATUTORY_HOLIDAY }

/**
 * 判定结果。它不是"一个枚举值"，而是"一个值 + 一句关于数据的话"。
 */
data class DayTypeResult(
    val date: LocalDate,
    val type: DayType,
    /** true = 这一年的节假日数据还没填，判定退化成周末规则。**必须让调用方知道**。 */
    val missingYear: Boolean,
)
```

### 端口（住 `core:domain`）

```kotlin
interface WorkCalendar {
    fun typeOf(date: LocalDate): DayTypeResult
    fun typeOf(range: TimeRange): List<DayTypeResult>   // 给"算一个月"的调用方
}
```

**为什么是内核**：消费方是 Payroll、实现在 Calendar，R2 不许 feature 互相依赖 ——
读通路住内核、实现住上下文、装配在 `:app`（`ADR-0008` 的同一手法，
与 `LedgerTotalsReader` 完全同构）。**"谁能定义"是所有权问题，不是位置问题**（`ADR-0011` 决策 4）。

### 判定规则（住 `feature:calendar`）

```
1. 该日期在**覆盖表**里 → 用它标的类型          （调休、法定节假日、以及用户覆盖）
2. 否则 周六/周日 → REST_DAY
3. 否则            → WORKDAY
4. 若该年没有数据 → 跳过第 1 步，并把 missingYear 置 true
```

**顺序本身是规则**：覆盖优先于星期几（否则调休那天的判定就错了）。

### 没有聚合、没有领域事件

- **没有聚合**：这里没有"需要守住的不变式"。节假日数据是外部事实，
  判定是**纯函数**（数据进、类型出）——给它造一个聚合根只会多一个可变的壳。
- **没有事件**：这是同步只读查询（`Conformist`，见上下文地图）。
  把"今天是工作日吗"变成异步通知，是把最简单的问题复杂化。

## 外部数据（`HolidayData`）

| 项 | 决定 |
|---|---|
| 格式 | **行式文本**（`feature:calendar` 的 assets，`holiday_data.txt`）—— 不用 JSON，理由见 `ADR-0011` 决策 1（没有 JSON 依赖 / diff 可读 / 可写注释 / 可纯 Kotlin 解析因而能单测） |
| 形态 | `year 2026` + 每天一行 `<yyyy-MM-dd> <holiday\|workday>`，`#` 开头是注释 |
| 谁能读它 | 只有 `feature:calendar` 的 Mapper（`R6`：外部格式**绝不进领域层**） |
| 更新 | **每年手动一次**（国务院发布后）—— `ADR-0011` 决策 1 |
| 缺年 | 退化 + `missingYear = true` + 记一条日志（`ADR-0011` 决策 2） |
| 坏行 | **跳过该行 + 计数 + 记日志**（一行坏不毁掉一整年） |
| 整个文件读不出来 | 当作该年无数据（**不让 App 因为一份附带数据而崩**）+ 记日志 |

> ⚠️ **`T-025` 不填真实日期**：法定节假日是现实世界的事实，
> 凭记忆编日期就是把猜测写进"唯一事实源"（违反 P5）。
> 所以本卡交付的是**机制**：格式、解析、规则、以及"缺数据时明确说出来"的行为；
> 填数据是每年一次的手工步骤，写进 `docs/60-runbooks/`（**下一个任务补 runbook**）。
