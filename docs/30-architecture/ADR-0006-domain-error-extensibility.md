# ADR-0006 共享内核的 `DomainError` 必须可被各上下文扩展（去掉 sealed）

- 状态: **已接受**
- 日期: 2026-10-02
- 决策者: Agent（受影响的上下文只有 Ledger，即本轮的 `REQ-001`；内核变更按 `context-map.md` 的规则记 ADR）
- 相关: `ADR-0001`（共享内核的建立）、`REQ-001`、`ADR-0005`、`docs/20-domain/context-map.md`、`docs/20-domain/ledger-model.md`

> ADR **只追加不改写**。本 ADR 不改变 ADR-0001 关于「`Money` / `Outcome` / `DomainEvent`
> 放共享内核、内核越小越好」的结论，只修正其中 `DomainError` 的**可扩展性**。

## 背景

写 `REQ-001` 的领域层时，Ledger 需要区分两类失败：
「金额不合法」与「没选分类」——因为 `AC-3` 与 `AC-4` 要求给用户**各不相同**的提示，
而校验本身发生在领域层（业务规则住在聚合里）。

内核提供的 `DomainError` 只有 `NotFound` / `InvalidInput` / `InvariantViolated` / `Technical.*`
四个泛型分支，**无法区分**上述两种情况。

于是 Ledger 按内核 KDoc 的措辞（「领域错误的公共父类型」「data 层把异常翻译成**这里的子类型**」）
定义了 `LedgerError : DomainError`。**编译失败**：

```
e: LedgerError.kt:19:32 Extending sealed classes or interfaces from a different module is prohibited.
e: LedgerError.kt:19:32 A class can only extend a sealed class or interface declared in the same package.
```

**这是一个真实的设计缺陷，不是猜测**：KDoc 的语义（可扩展的父类型）
与修饰符的语义（封闭集合）互相矛盾，且直到第一个业务上下文落地才暴露。

## 决策

把 `core:domain` 的 `DomainError` 由 `sealed interface` 改为 **`interface`**。

- 内核自己的分支（`NotFound` / `InvalidInput` / `InvariantViolated` / `Technical.*`）**全部保留**，
  但它们不再是唯一可能的子类型。
- 嵌套的 `sealed interface Technical : DomainError` **保持 sealed**：
  技术性错误是一个封闭集合（网络 / 存储 / 未知），这一点没有改变。
- 各上下文定义自己的错误类型（如 `LedgerError`）并实现 `DomainError`，
  从而能继续使用 `Outcome<T>`，不必各自造一套结果类型。

## 理由

| 方案 | 评价 |
|---|---|
| **改 sealed 为 interface（采纳）** | 改动一行；`Outcome.Err` 继续可用；上下文能给出精确错误；内核仍只放通用原语 |
| 保持 sealed，各上下文压成泛型分支 | ✗ 用户拿不到各自的提示，`AC-3`/`AC-4` 无法满足；错误细节在领域层就丢了 |
| 保持 sealed，各上下文自造结果类型 | ✗ 把 `Outcome` 架空；每个上下文重复一份 Ok/Err，共享内核名存实亡 |
| 把 `Outcome` 的错误类型参数化（`Outcome<T, E>`） | 更侵入：改动所有使用点，且仍然解决不了「`DomainError` 作为公共父类型」的角色 |
| 把上下文错误放进内核 | ✗ 与「共享内核越小越好」直接冲突；`LedgerError` 进内核等于让内核依赖业务概念 |

## 后果

| 后果 | 说明 |
|---|---|
| 跨模块的 `when (error)` 不再具备穷尽性 | 开放继承体系固有的代价。当前没有任何代码对 `DomainError` 做穷尽 `when`；将来若需要，应在**具体上下文内部**做分支 |
| `DomainError` 的语义变为「可扩展的公共父类型」 | 与它的 KDoc 一致了；KDoc 已同步说明与 `ADR-0006` |
| 内核仍然很小 | 只改了一个修饰符，没有新增任何业务概念 |
| 各上下文必须自己保证错误可控 | 新增约定：上下文错误类型必须放在**本模块**的 `domain/error/`，不得互相引用（`R2`/`R6` 已由架构校验兜底） |

## 验证

- `:feature:ledger:compileDebugKotlin` 在改动后通过（原先失败）。
- 该改动**不改变**任何既有行为：`Outcome` 的用法、内核自身的错误分支、
  `Money` / `TimeRange` 的测试均不受影响（`core:domain` 的 14 个测试保持全绿）。
- 反向验证：把改动词还原为 `sealed`，编译应当再次失败——用它证明这条 ADR 的必要性，
  而不是「改了也没事」。

## 未决

- 当第二个业务上下文（Worklog 或 Payroll）落地时，应复核「上下文错误类型」的命名与分层约定
  是否仍然合适；若出现重复的通用错误（多个上下文都需要同一语义的错误），
  再评估是否上提到内核——**而不是现在就投机地放进去**。
