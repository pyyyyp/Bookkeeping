# Ledger 领域模型

- 上下文: Ledger（**核心域**）
- 模块: `feature:ledger`
- 最后更新: 2026-10-02（据 `REQ-001` 建立）
- 关联: `REQ-001`、`ADR-0005`、`docs/00-charter/glossary.md`

> 本文是代码的**规格说明**。这里写的不变式，代码必须实现、测试必须覆盖。
> 术语必须与 `docs/00-charter/glossary.md` 一致。

## 这个上下文守护什么

一句话：**Ledger 是「钱」的唯一记录地。**

它不解释钱为什么来（那是 Payroll / Worklog 的事），也不判断钱该不该花。
它只保证一件事：**每一笔进出都被如实、无歧义地记下来，并且能被查回来。**

这也是为什么它是核心域——不是因为「记账」这个功能本身有多难
（那是通用域，市面上方案极多），而是因为它是其他上下文最终的汇聚点：
工资单最终要变成一笔 `LedgerEntry(Income)`。

---

## 聚合：`LedgerEntry`（账目条目，聚合根）

### 标识

`LedgerEntryId`（值对象，包装 UUID 字符串）。

**为什么用 UUID 而不是数据库自增 ID**：标识由**领域**在被记录的那一刻分配，
而不是等数据库插入后回填。这样聚合在持久化之前就是完整的、可测试的，
也不必为了拿一个 ID 而依赖数据层——这是 R4（依赖倒置）在标识上的体现。

### 属性

| 属性 | 类型 | 可变 | 说明 |
|---|---|---|---|
| `id` | `LedgerEntryId` | ❌ | 聚合标识 |
| `direction` | `EntryDirection` | ❌ | `Expense` / `Income`，来自共享内核 |
| `amount` | `Money` | ❌ | 以「分」为单位（共享内核），非负 |
| `categoryId` | `CategoryId` | ❌ | **只用 ID 引用分类**，不持有 `Category` 对象 |
| `occurredAt` | `Instant` | ❌ | 这笔钱**实际**发生的时间 |
| `bookedAt` | `Instant` | ❌ | 这笔条目**被录入**的时间（补记时二者不同） |
| `note` | `Note?` | ❌ | 备注，可空（不填是常态；空备注只有 `null` 一种表达） |

> **不可变**是刻意的：本卡不含「编辑条目」。等做编辑时再讨论是
> 「生成新条目 + 作废旧条目」还是「受控变更」——那是一个需要 ADR 的决策，现在不预设。

### 不变式

> 不变式是聚合**存在的理由**。写不出不变式的聚合，很可能不该是聚合。

| 编号 | 不变式 | 违反时返回 | 对应测试 |
|---|---|---|---|
| INV-1 | `amount` 必须 **> 0**（`Money` 保证非负，这里再排除 0） | `LedgerError.AmountNotPositive` | `LedgerEntryTest` |
| INV-2 | `categoryId` 必须非空 | `LedgerError.CategoryRequired` | `LedgerEntryTest` |
| INV-3 | `direction` 只能是 `Expense` / `Income` | 类型系统保证（enum），无需运行时校验 | 编译期 |
| INV-4 | 条目一旦被删除即不存在（物理删除，无「墓碑」状态） | 见 `ADR-0005` | `DeleteLedgerEntryUseCaseTest` |
| INV-5 | `note` 若存在，长度 ≤ 200 **Unicode 码点** | `LedgerError.NoteTooLong` | `NoteTest` |
| INV-6 | `occurredAt` 不得晚于 `bookedAt` | 见下方「待决问题」`Q-021`，**本卡不校验** | — |

> **错误类型是 `LedgerError`，不是内核的 `DomainError.InvalidInput`**：
> 内核那几个泛型分支无法区分「金额不合法」与「没选分类」，
> 而 `AC-3` / `AC-4` 要求给出**各不相同**的提示。
> 让上下文能定义自己的错误，需要先把内核的 `DomainError` 从 `sealed` 改成 `interface`
> —— 那次内核修正记在 `ADR-0006`，起因是编译器直接拒绝跨模块实现 sealed 类型。

### 行为

| 方法 | 语义 | 前置条件 | 发出的领域事件 |
|---|---|---|---|
| `LedgerEntry.record(direction, amount, categoryId, occurredAt, note, bookedAt, id)` | 工厂：记录一笔新条目 | 参数满足 INV-1/2/5 | **不发事件**（理由见下） |
| `LedgerEntry.revise(direction, amount, categoryId, occurredAt, note)` | 用新的字段值**替换**这条记录（`REQ-003`） | 与 `record` **同一套**校验（INV-1/2/5） | 返回**同 id** 的新实例：身份不变（`REQ-003/BR-4`），`bookedAt` 也不变（`BR-3`） |

> `revise` 与 `record` 共用同一个私有构造校验，而不是各写一遍：
> 两条路径各有一份校验，迟早会漂移——其中一条会漏掉后来新增的规则（`REQ-003/BR-6`）。

> **本卡刻意不引入领域事件。** 领域事件是跨上下文解耦的通道，
> 而本卡没有任何订阅方：Insight 是只读投影（直接读库，见 `context-map.md`），
> Payroll → Ledger 的 `PayslipIssued` 要等 Payroll 落地。
> 没有订阅方的事件是投机性抽象——等真实订阅方出现再加。

### 领域事件

| 事件 | 载荷 | 触发时机 | 订阅者 |
|---|---|---|---|
| —（本卡无） | — | — | — |

---

## 值对象清单

| 值对象 | 字段 | 校验规则（init 中强制） | 位置 |
|---|---|---|---|
| `Money` | `cents: Long` | 非负 | ✅ 已在 `core:domain`（14 测试覆盖） |
| `EntryDirection` | `Expense` / `Income` | — | ✅ 已在 `core:domain` |
| `LedgerEntryId` | `value: String`（UUID） | 非空、可解析为 UUID | `feature:ledger` domain |
| `CategoryId` | `value: String`（slug，如 `food`） | 非空、只含小写字母与连字符 | `feature:ledger` domain |
| `Category` | `id`、`displayName`、`direction` | 名称非空、去空白后 ≤ 20 字符 | `feature:ledger` domain |
| `Note` | `text: String?` | 非空白则 ≤ 200 码点 | `feature:ledger` domain |

> 值对象必须**不可变**、`init` 自校验、按值相等。优先用 `@JvmInline value class`。
> 不用裸 `String` / `Int` 传递有业务含义的量 —— 所以有 `LedgerEntryId` 与 `CategoryId`，
> 而不是两个 `String`。

### `Category`：从值对象**升级为聚合根**（`REQ-004` / `ADR-0009`）

`T-007` 时它是值对象，理由写得很清楚：那时分类随应用内置，用户不能增删改，
没有需要原子维护的不变式，也没有生命周期行为。**那句话同时预告了这一节** ——
「当『自定义分类』落地时」它就要升级。

`REQ-004` 落地了，三个条件同时成立：

| 触发条件 | 具体表现 |
|---|---|
| 有**生命周期** | 新建 → 改名 → 归档 → 恢复；归档后不再出现在选择器，但历史仍要能显示它 |
| 有**标识与身份** | 条目存的是 `CategoryId`，所以"改名"不该产生第二个分类（`REQ-004/AC-2`） |
| 有**需要原子维护的状态** | `archived` 与名字必须一起变；不能出现"名字改了但归档状态没跟上" |

派生出的两条规则：

- **唯一性不在聚合内**（`REQ-004/BR-7`）："所有分类里不能重名"是**跨聚合**规则。
  塞进 `Category.init` 会得到一个聚合内无法验证、也测不准的规则 ——
  它属于应用层（用例在创建前查一次）。
- **归档而不是删除**（`ADR-0009`）：条目引用了分类，物理删除要么留悬空引用、
  要么得改写历史。注意这与条目本身的**物理删除**不矛盾：
  条目没有下游引用者，分类有。

`CategoryCatalog.PRESET` 的角色因此收窄：它是**内置分类的定义**，
而"界面该显示哪些分类"由用例给出（预置 ∪ 未归档的自定义）。

### 预置分类（`REQ-001` 范围：只读、随应用内置）

| `CategoryId` | 显示名 | 方向 |
|---|---|---|
| `food` | 餐饮 | Expense |
| `transport` | 交通 | Expense |
| `shopping` | 购物 | Expense |
| `housing` | 居住 | Expense |
| `medical` | 医疗 | Expense |
| `entertainment` | 娱乐 | Expense |
| `salary` | 工资 | Income |
| `other` | 其他 | 两者皆可 |

> 这份清单是**业务内容**，不是技术细节。它被刻意放在领域层（不是 UI 层），
> 因为「有哪些分类」会影响条目的合法性与后续统计口径。
> **用户可以随时要求调整**——改这里只需要改一处。

---

## 仓储

```kotlin
// 接口在 domain，实现在 data（R4：依赖倒置）
interface LedgerEntryRepository {
    suspend fun add(entry: LedgerEntry): Outcome<Unit>
    suspend fun remove(id: LedgerEntryId): Outcome<Unit>
    suspend fun recent(limit: Int): Outcome<List<LedgerEntry>>   // 按 occurredAt 倒序
}
```

> **为什么返回值是 `Outcome` 而不是直接返回数据 / 抛异常**：
> 「不抛异常跨层」是硬规则，而存储失败（磁盘满、数据库损坏）是**可预期**的失败。
> 因此 `data` 层负责把技术异常翻译成 `DomainError.Technical`，
> 上层拿到 `Outcome`，不必写 try/catch。
> 仓储实现在 `feature:ledger` 的 `data/repository`（见 `ADR-0007`）。
>
> **为什么没有 `observeRecent(...): Flow<...>`**（原设计里有）：
> 反应式观察要求领域层依赖 kotlinx-coroutines，而引入新依赖需要单独裁决。
> 本卡的界面只要「改动后重新查询」就能刷新，所以 `suspend` 就够了。

> 接口说**领域语言**（`recent` / `add` / `remove`），不说 SQL 语言
> （不出现 `insert` / `delete` / `query` / `LIMIT`）。
> 只放聚合根——所以没有 `CategoryRepository`：本卡分类是只读内置数据。

---

## 用例（application 层）

| 用例 | 输入 | 输出 | 不变式/边界 |
|---|---|---|---|
| `RecordLedgerEntryUseCase` | `direction, amount, categoryId, occurredAt, note` | `Outcome<LedgerEntryId>` | 走 `LedgerEntry.record()`，不变式全部生效；`bookedAt` 取注入的 `Clock` |
| `LoadRecentEntriesUseCase` | `limit`（默认 200） | `Outcome<List<LedgerEntry>>` | 只读；排序由仓储（SQL）保证 |
| `DeleteLedgerEntryUseCase` | `LedgerEntryId` | `Outcome<Unit>` | 物理删除（`ADR-0005`）；条目不存在时返回 `LedgerError.EntryNotFound` |

> **用例名说明**：叫 `Load...` 而不是 `Observe...`——本卡不做反应式观察，
> **名字必须说明它真的做了什么**，否则调用方会以为界面会自动刷新。

**用例只编排，不含业务规则**：金额是否合法、备注是否过长，都在 `LedgerEntry` 里判定。
用例负责「取分类 → 组装 → 调仓储 → 映射错误」，不负责「判断金额能不能为 0」。

---

## 与其他聚合/上下文的协作

| 协作对象 | 方式 | 一致性 | 说明 |
|---|---|---|---|
| Insight | 只读投影（SQL） | 最终一致 | Insight 直接读 Ledger 的表，Ledger **不为其做适配**（Conformist） |
| Payroll | 领域事件 `PayslipIssued` | 最终一致 | **本卡未实现**；等 Payroll 落地时，工资单在这里变成一笔 `LedgerEntry(Income)` |
| 通知监听（外部） | ACL Mapper | — | `Q-001`；外部通知文本 → 领域命令。**外部格式绝不进入领域层** |

**选择最终一致的理由**：Ledger 与 Payroll 各自独立演进，工资单入账晚几毫秒无业务影响；
强行同步会让 Ledger 依赖 Payroll 的聚合（违反 `context-map.md` 的协作表）。

---

## 待决问题

- `Q-008`：是否需要独立的「记账日」`bookingDate`？本模型**未引入**该概念
- `Q-021`（本轮新增）：是否允许记录**未来时间**的账？本卡不校验，也未写成业务规则
- `Q-020`：视觉设计未定，UI 暂用 Material 3 默认（单点 `TODO(Q-020)`）
- 自定义分类落地时：`Category` 是否升级为聚合（见上文）
