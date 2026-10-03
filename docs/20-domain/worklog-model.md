# Worklog · 工时模型

- 状态: **草稿 v0.1**（`T-027` 落地"手工记录 + 出勤事实"）
- 上下文类型: **核心域**
- 模块: `feature:worklog`
- 相关: `REQ-014`、`ADR-0012`、`Q-012`（工时不参与金额计算）、`Q-024`（围栏）、`Q-025`（半天）、`Q-026`（缺勤）

## 这个上下文回答什么

**这一天工作了多少分钟（已确认的）。** 就这一件事。

它**不**回答：哪天算加班（门槛是钱的事，归 Payroll）、这天值多少钱（Payroll 的事）。
`Q-012` 之后，工时不参与任何金额计算 —— 它只提供**事实**。

## 通用语言

见 `docs/00-charter/glossary.md` 的「Worklog · 工时」一节：
`Workplace` / `WorkSession` / `SessionState` / `AttendedDay`。

## 模型

```kotlin
enum class SessionState { RUNNING, FINISHED, CONFIRMED, DISCARDED }

data class WorkSession(
    val id: WorkSessionId,          // 身份：同一段可以被确认/作废，而不是"另存一段"
    val day: LocalDate,             // **归属日**：它开始的那一天（见 AC-4 的局限）
    val startedAt: Instant,
    val endedAt: Instant?,          // Running 时还没有结束
    val state: SessionState,
) {
    val confirmedMinutes: Int       // 仅 CONFIRMED 时有意义
}

/** Worklog 交给 Payroll 的事实：某天 + 该天已确认的分钟数。 */
data class AttendedDay(val date: LocalDate, val confirmedMinutes: Int)
```

### 只有 `Confirmed` 是事实

| 状态 | 含义 | 计入出勤 |
|---|---|---|
| `RUNNING` | 还在跑 | ❌ **一个还在跑的时段不能变成工资** |
| `FINISHED` | 跑完但未确认 | ❌ |
| `CONFIRMED` | 已确认 | ✅ |
| `DISCARDED` | 作废 | ❌ |

### 段与段**相加**，不合并跨度

上午 `09:00–12:00` + 下午 `13:00–18:00` = **8 小时**，不是"从 9 点到 18 点共 9 小时"——
中间那顿午饭不是工时。这条看起来显然，但"取最外层跨度"是最容易顺手写出来的实现。

### 没有聚合

这里没有需要守住的不变式：一段工时就是一段工时，确认与作废是**状态变化**，
不是"必须一起成立的一组规则"。**唯一的规则（只有 `Confirmed` 算）作用在取出事实的那一刻**，
由测试守。

## v1 的范围（`ADR-0012`）

| 项 | v1 | 为什么 |
|---|---|---|
| 记录方式 | **手工填起止** | 地理围栏要新依赖 + 位置权限（`Q-024`），必须先把这两条问清楚 |
| `Workplace` | **不使用** | 它是围栏的产物；没有围栏就不需要（将来做围栏时再落） |
| 加班门槛 | **不在本上下文** | 它是**钱**的规则，归 Payroll（`ADR-0012` 决策 2）——Worklog 因此**不认识 Calendar** |
| 半天加班 | **不做** | 需要把倍率改成有理数（`Q-025`） |
| 缺勤 | **不做标记** | `Q-026`：没有记录按**正常出勤**处理，不能用"没记录"造出"缺勤"的假事实 |

## 交给 Payroll 的方式

**内核端口**（`ADR-0012` 决策 1）：`AttendedDay` 与端口住 `core:domain`，实现住这里，
装配在 `:app` —— 与 `LedgerTotalsReader`（`ADR-0008`）、`WorkCalendar`（`ADR-0011`）同一手法。

> ⚠️ 上下文地图原写"订阅 `WorkSessionConfirmed` 事件 + 本地快照"。
> 按 §0，**已接受的 ADR 优先**；而且工资单若依赖最终一致，一次未传播的时段就是**少算钱**。
> 该冲突与裁决见 `ADR-0012` 决策 1，地图那一行已同步标注。
