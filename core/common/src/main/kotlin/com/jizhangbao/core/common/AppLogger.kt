package com.jizhangbao.core.common

/**
 * 项目自己的**最小**日志接口（`REQ-006` / `ADR-0010`）。
 *
 * ## 为什么不是日志框架
 *
 * 当前只有三处调用点（条目仓储、分类仓储、合计读取器），需要的功能只有一个：
 * `warn(message, cause)`。引入 Timber / Kermit / slf4j 要付依赖 + 初始化 + 混淆规则的成本，
 * 而换来的能力一个都用不上。将来真要落盘或上报时，换的是**实现与绑定**，不是几十处调用点。
 *
 * ## 为什么抽接口、而不直接用 `android.util.Log`
 *
 * `:feature:*` 的单元测试跑在 JVM 上，`android.util.Log` 在那里是桩实现 ——
 * 一调用就抛 `RuntimeException("Stub!")`。抽成接口，测试里换成 [NoOpLogger] 或记录型 fake
 * 就完全没有这个问题（也让"到底记了什么"变成可断言的东西）。
 *
 * ## ⚠️ 不许记 PII（`REQ-006/BR-2`）
 *
 * 只记**固定文案 + 异常**。不记金额、备注、分类名，也不记整行数据。
 * 这条写在这里而不是文档里，是因为"顺手多记一点"正是最容易越界的地方 ——
 * 而日志一旦落盘/上报，越界就收不回来了。
 */
interface AppLogger {

    /**
     * 记一条**可恢复**的业务失败（`BR-6`：不用 `error` 级别）。
     *
     * `error` 留给"应用自身出 bug"；存储失败是被处理过的已知路径，用户仍然看到了提示。
     */
    fun warn(message: String, cause: Throwable? = null)
}
