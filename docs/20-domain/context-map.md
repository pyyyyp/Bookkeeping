# 限界上下文与上下文映射

- 状态: **草稿 v0.2 · 待评审**（据 Q-003 答复删除 Allocation 上下文）
- 最后更新: 2026
- 起草人: AndroidDDD-Agent（基于问卷答复，尚未做完整事件风暴）

> ⚠️ **这是草稿，不是结论。** 我尚未与你做事件风暴，本划分基于问卷答复中的
> 「记账 / 定位工时 / 算工资 / 每日收入」四个动作推断。**仍未闭环的是 Q-012**——
> 若「分摊」揭示出一个独立的「资金用途/目标」概念，需要重新引入上下文。
>
> 本文是模块划分的**权威依据**。`settings.gradle.kts` 中的 `feature:<x>` 必须与本文一一对应。
> 划分变更必须新增 ADR。

## 上下文清单（草稿）

| 上下文 | 类型 | 职责（一句话） | 拥有的聚合 | 模块 |
|---|---|---|---|---|
| **Ledger** 账本 | **核心域** | 记录一切钱的进出：自动/手工的收入与支出条目、分类、账户 | `LedgerEntry` | `feature:ledger` |
| **Worklog** 工时 | **核心域** | 基于地理围栏记录工作时段 | `WorkSession`、`Workplace` | `feature:worklog` |
| **Payroll** 薪资 | **核心域** | 把工时按费率结算成工资单，并把工资分摊为每日收入 | `Payslip`、`PayRate` | `feature:payroll` |
| **Insight** 统计 | 支撑域 | 日/周/月汇总与报表（**读模型，无聚合**） | — | `feature:insight` |

> **v0.2 变更（Q-003 澄清）**：原草稿中的 **Allocation 上下文已删除**。
> 用户确认「按比例记录每天收入」指的是「把月工资分摊到每个工作日」，
> 而不是「把收入分配到不同用途/目标」。前者不是一个独立业务能力，
> 而是 Payroll 对工资的时间维度展开，因此归入 Payroll。
> ⚠️ 分摊的**基数与比例**仍未定义（Q-012），若答案揭示出一个独立的
> 「资金用途/目标」概念，Allocation 需要重新引入。

### 为什么这样分

**为什么 Ledger 是核心域，而不是「记账」这个功能本身**

「记账」是通用域——市面上成熟方案极多，我们不该自己发明一套分类法。
但 Ledger 在本项目里承担了更关键的职责：**它是「钱」的唯一记录地**，
工资单最终要变成一笔 `LedgerEntry(Income)`，分配也要作用在收入条目上。
换句话说，Ledger 不是因为「记支出」而核心，而是因为它是其他上下文的汇聚点。

**为什么 Worklog 与 Payroll 分开**

| | Worklog | Payroll |
|---|---|---|
| 守护的不变式 | 时段不重叠、地点有效、时长非负 | 周期不重复结算、费率按生效日期取值、应付金额 = Σ(时长 × 费率) |
| 变化频率 | 每天多次 | 费率几个月才变一次 |
| 失败影响 | 少记一次工时 | 算错钱 |

按 AGENTS.md 第 6.4 节启发式 #3（变化频率差别大的应分开）与 #4（需强一致的放一起），
二者不能强一致地放在一个聚合里，且语言不同。**但这个判断需要你确认**——
如果你觉得「工时和工资本来就是一件事」，我们应当合并为 `Earning` 上下文。

**为什么 Insight 是支撑域且没有聚合**

统计是从 Ledger/Worklog/Payroll 派生出来的读模型。给它聚合会导致同一事实存两份，
违反 SSOT。它以 SQL 查询 / 投影实现，不拥有业务规则。

## 上下文映射（草稿）

| 上游 | 下游 | 模式 | 集成方式 | 说明 |
|---|---|---|---|---|
| Worklog | Payroll | Customer-Supplier | 领域事件 `WorkSessionConfirmed` | 只有被确认（Confirmed）的时段才参与结算；`Running`/`Discarded` 不计入 |
| Payroll | Ledger | Customer-Supplier | 领域事件 `PayslipIssued` | 工资单产生一笔 `LedgerEntry(Income)`；跨聚合走最终一致 |
| Payroll | （内部）| — | `DailyIncome` 由 `Payslip` 派生 | 非跨上下文，是 Payroll 内部的时间维度展开。规则待 Q-012 |
| Ledger / Worklog / Payroll | Insight | Conformist | 直接读本地库（只读投影） | 读模型主动适应上游模型，上游不为其做适配 |
| 系统通知 / 短信 / 银行账单（外部） | Ledger | **ACL（防腐层）** | Mapper 把外部文本/结构化数据翻译成 `LedgerEntry` 草稿 | 🔴 取决于 Q-001；**外部格式的解析细节绝不进入领域层** |
| 定位服务（外部） | Worklog | **ACL** | 把坐标/围栏事件翻译成领域事件 | 系统定位 API 的变化被适配器吸收 |

## 共享内核

| 内容 | 位置 | 使用者 | 变更规则 |
|---|---|---|---|
| `Money`、`EntryDirection` | `core:domain` | Ledger, Payroll, Allocation | 变更需受影响上下文共同确认 + ADR |
| `Outcome`、`DomainError`、`DomainEvent` | `core:domain` | 全部 | 同上 |
| `TimeRange`（时间区间值对象） | `core:domain` | Worklog, Payroll, Ledger | 同上 |

> 共享内核**越小越好**。只放真正的通用原语。
> 特别注意：**`Category` 不放共享内核**——它是 Ledger 的概念，其他上下文引用它只能用 ID。

## 跨上下文协作方式

| 场景 | 推荐方式 | 禁止方式 |
|---|---|---|
| Payroll 需要工时数据 | 订阅 `WorkSessionConfirmed` 事件，本地保存所需快照 | 直接 import `feature:worklog` 的聚合 |
| Ledger 需要工资单金额 | 订阅 `PayslipIssued` 事件 | 同步查询 Payroll 聚合 |
| Insight 需要跨上下文数据 | 只读投影 / SQL | 反向依赖各上下文的领域模型 |
| Worklog 需要知道费率 | **不订阅**。Worklog 只记时长，费率是 Payroll 的事 | 让 Worklog 持有 `PayRate` |

## 关键边界判断

| 判断点 | 结论 | 依据 | 状态 |
|---|---|---|---|
| 「自动捕获的消费」是 Ledger 内的聚合还是独立上下文 | **Ledger 内**，作为条目的一个来源字段 | 它是 `LedgerEntry` 的一种创建方式，不是独立业务能力 | 草稿 |
| 「工作地点」归 Worklog 还是独立 Place 上下文 | **Worklog 内** | 地点只在工时的语境下有业务含义 | 草稿 |
| 「费率」归 Payroll 还是 Worklog | **Payroll** | 费率只在结算时有意义 | 草稿 |
| 「分配目标」是否是一个聚合 | 🔴 待 Q-003 | — | 阻塞 |
| 是否需要独立的「用户/设置」上下文 | **不需要** | 单人使用，设置属于 UI 层 | 草稿 |

## 待决问题

- 影响本文件成立的问题全部列在 `open-questions.md`，其中 **Q-001 / Q-003 / Q-007 直接决定上下文划分**。
