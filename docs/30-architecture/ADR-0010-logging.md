# ADR-0010 日志：一个最小接口，不引入框架

- 状态: **已接受**（2026-10-03）
- 决策者: Agent（方案）；用户已授权"按推荐做"
- 关联: `REQ-006`、`ADR-0007`（数据层归属）、`ADR-0006`（`DomainError` 去 sealed）

## 背景

`T-007` 起就挂着一个已知缺口：**存储异常的原因从来没有被记录**。
`LedgerEntryRepositoryImpl.storageOutcome` 把任何技术异常翻成 `DomainError.Technical.Storage`
—— 于是「磁盘满」「数据库损坏」「表不存在」在上层看起来完全一样，用户与开发者都无从判断。

`REQ-006` 要补上它，于是要先回答：**日志用什么？**

## 决策

**在 `:core:common` 定义一个最小接口，Android 实现用 `android.util.Log`。**

```kotlin
interface AppLogger {
    fun warn(message: String, cause: Throwable? = null)
}
```

- 接口住在 `:core:common`（Android Library），因此实现可以用 `android.util.Log`；
- 而 `:core:domain` 是**纯 Kotlin JVM 模块**，它**看不见**这个接口 —— 这正是想要的：
  领域层不该记日志（它不关心"存储"这件事，那是外面的细节）；
- 绑定在 `:app`（组合根），与其它基础设施一致（`ADR-0007`）；
- 测试里用 `NoOpLogger`（不打印），需要断言"记了什么"时用记录型 fake。

## 被否方案

### 引入日志框架（Timber / Kermit / slf4j）

- **成本**：新依赖 + 初始化 + 混淆/裁剪规则；Kermit 还要求多平台配置。
- **收益**：当前只有**三处**调用点（条目仓储、分类仓储、合计读取器），
  需要的功能只有一个：`warn(message, throwable)`。
- 将来真要落盘/上报时再换实现 —— 那时改的是一个接口的绑定，不是几十处调用点。
  **先有接口，后选实现**，这是可逆的；反过来（先引入框架再抽象）则不可逆。

### 直接用 `android.util.Log`（不抽接口）

- 为什么否掉：`:feature:*` 的**单元测试**跑在 JVM 上，`android.util.Log` 是桩实现，
  调用会抛 `RuntimeException("Stub!")`（除非开 `unitTests.returnDefaultValues`）。
  抽一个接口，测试里换成 fake 就完全没有这个问题。
- 而且"往哪记日志"是可替换的基础设施细节，本来就该待在边界上。

### 记在 `DomainError.Technical.Storage` 的载荷里

- 为什么否掉：那会把**异常对象塞进领域错误**，等于让领域层持有技术细节
  （`ADR-0006` 刚把 `DomainError` 从 sealed 解开，正是为了不让内核知道各上下文的东西）。
  日志是**横切关注点**，不该顺着 `Outcome` 往上传。

## 影响

- `:core:common` 从"只有一个平台判定的占位模块"变成有真实内容（`AppLogger` + 实现）——
  与它 `build.gradle.kts` 里的注释一致（内容由真实需要倒推）
- 三处 `storageOutcome` 型的异常翻译点统一变成"记一条 warn + 返回领域错误"
- ⚠️ **日志里不许出现 PII**（`REQ-006/BR-2`）：只记固定文案 + 异常，
  不记金额、备注、分类名。这条写进 KDoc，因为这正是"顺手多记一点"最容易越界的地方

## 复审条件

1. 需要日志落盘 / 上报崩溃时 → 换实现（接口不变）；
2. 调用点超过 ~15 处、或需要分级/结构化字段时 → 那时再评估框架，
   并且**先把需求写清楚**（记什么、给谁看、存多久）。
