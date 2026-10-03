# ADR-0007 数据层实现的归属（实体与仓储实现在上下文模块，`@Database` 在组合根）

- 状态: **已接受**
- 日期: 2026-10-02
- 决策者: Agent（受硬规则 R7 约束，无其它可行方案；已记录被否方案）
- 相关: `ADR-0001`（模块结构）、`ADR-0005`（Ledger v1 范围）、`REQ-001`、`T-007`、
  `docs/30-architecture/module-graph.md`、`docs/20-domain/context-map.md`

> ADR **只追加不改写**。本 ADR **修正** `module-graph.md` 与 `core/data/build.gradle.kts`
> 里「第一个 `@Entity` 随第一个数据层任务卡一起进 `:core:data`」的说法——
> 那句话与 R7 直接冲突，在真正写数据层之前没人发现。

## 背景

`REQ-001`（T-007）要落地第一个数据层：`LedgerEntry` 的 Room 实体、DAO、Mapper、
仓储实现，以及整个应用**唯一的** Room 数据库。

此前文档写的是「第一个 `@Entity` 随第一个数据层任务卡一起进 `:core:data`」
（`module-graph.md`、`core/data/build.gradle.kts` 的注释）。动手时发现它不可能成立：

| 事实 | 依据 |
|---|---|
| `:core:data` **不得**依赖任何 `feature:*` | **R7**，由 `checkModuleDependencies` 机器强制 |
| Room 实体与 Mapper **必须**引用 `feature:ledger` 的领域类型（`LedgerEntry` / `Money` / `CategoryId`） | R6 要求 Mapper 做转换，转换的两端就是这两头 |
| 因此实体/Mapper/仓储实现**不可能**住在 `:core:data` | 上面两条的推论 |

同一时刻还有第二条约束：整个应用必须有**一个**数据库（`context-map.md`：本机 Room 是唯一事实源），
而 Room 的 `@Database` 类必须在**编译期**列出全部实体——也就是必须能看见所有上下文的实体。
能同时看见所有 feature 的模块只有一个。

## 决策

### 1. 每个上下文的持久化实现住在**自己的 feature 模块**的 `data` 包

```
feature/ledger/src/main/kotlin/com/jizhangbao/ledger/
├── domain/         ← 聚合、值对象、仓储接口（已完成）
├── data/local/     ← LedgerEntryEntity（Room）、LedgerEntryDao、Mapper
├── data/repository/← LedgerEntryRepositoryImpl（实现 domain 的接口，R4）
├── di/             ← 该上下文的 Hilt 绑定
└── presentation/   ← ViewModel 与 Compose
```

### 2. 唯一的 Room `@Database` 住在 `:app` 的 `data` 包

`:app` 是**组合根**：只有它能同时依赖全部 feature（R8），因此只有它能列出全部实体。
数据库定义是**基础设施**，不是业务规则——R8 禁止的是「app 里写业务规则」，不冲突。

### 3. `:core:data` 收敛为**跨上下文共享的数据基础设施**

放共享的 `TypeConverter`、数据库构建帮助之类；**不得持有任何上下文的实体**
（一旦持有就必须依赖对应 feature，直接违反 R7）。

### 4. DI 的分工

| 绑定 | 位置 | 理由 |
|---|---|---|
| `LedgerEntryRepository` → `LedgerEntryRepositoryImpl` | `feature:ledger/di` | 实现细节属于该上下文（R10 允许 `di` 引用 `data`） |
| `JizhangbaoDatabase` → `LedgerEntryDao` | `:app` 的 `data` 包 | 数据库是组合根的东西 |

> R10 的判据是「**非** `data`、**非** `di` 的文件不得引用 `data` 层」，
> 上面两类文件都在豁免范围内 ✓（`app` 的数据库文件位于 `.../app/data/` 路径下）。
> 这也意味着：**将来若把数据库类挪到 `app` 的非 data/di 包下，R10 会立刻报违规**——
> 位置不是随意的。

## 理由

| 方案 | 评价 |
|---|---|
| **实体与仓储实现放 feature 模块，`@Database` 放 app（采纳）** | 同时满足 R7（core 不依赖 feature）、R4（data 实现 domain 接口）、R6（Mapper 转换）、SSOT（单库） |
| 把实体/仓储实现放 `:core:data` | ✗ 必须依赖 `feature:ledger` → 违反 R7（机器会拦） |
| 把实体放 `:core:domain` | ✗ 外部模型进领域层 → 违反 R6 与 R3；领域层连 Room 注解都不该看见 |
| 每个 feature 一个数据库 | ✗ 违反 SSOT：跨上下文的原子性与一致性无从保证，也做不了统一迁移 |
| 新增一个 `:core:database` 模块，让它依赖各 feature | ✗ 与 R7 同类的问题（core 前缀的模块依赖 feature），且等于把组合根拆成两个 |

## 后果

| 后果 | 说明 |
|---|---|
| `module-graph.md` 与 `core/data/build.gradle.kts` 的旧说法必须更正 | 已在同一轮更新，避免下一个 Agent 又按错的说法做 |
| feature 模块变成「垂直切片」：一个上下文从领域到界面都在一个模块里 | 这正是 `context-map.md`「模块边界 = 上下文边界」的落地形态 |
| 数据库类的位置**不能随便挪** | 挪出 `data`/`di` 包会触发 R10；挪出 `:app` 会看不见其他上下文的实体 |
| `:core:data` 的存在感可能长期很低 | 可接受：**宁可它空着，也不要它为了「像个基础设施模块」而持有别人的实体** |

## 验证方式

- `checkModuleDependencies` 会在每个模块的 `check` 上验证 R7（core 不依赖 feature）✓
- Konsist 的 R5 / R6 / R10 会在源码层面验证分层引用 ✓
  ——**这三条断言因此第一次有了真实靶子**（此前是「装好了没目标」的状态）
- 反向验证：故意把数据库类移到 `app` 的非 data/di 包、或让 `:core:data` 依赖 `:feature:ledger`
  → 对应的校验必须失败（记录在 `T-007` 卡）

## 未决

- `:core:data` 将来到底放什么（共享 Converter？迁移帮助？）等第二个上下文落地再定，
  **现在不为它编内容**。
- 若将来上下文的实体数量很大、需要拆分数据库，那是一次新的架构决策，需新增 ADR。
