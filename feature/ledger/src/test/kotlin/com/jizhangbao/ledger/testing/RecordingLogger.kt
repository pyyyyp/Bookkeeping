package com.jizhangbao.ledger.testing

import com.jizhangbao.core.common.AppLogger

/**
 * 记录型 [AppLogger]（`REQ-006`）。
 *
 * 比 `NoOpLogger` 多一件事：**把记下的内容留下来**，于是"原因有没有被记录"
 * （`AC-1`）与"记录里有没有 PII"（`BR-2`）都成了可断言的东西 ——
 * 而不是靠人去 logcat 里翻。
 */
internal class RecordingLogger : AppLogger {

    /** (message, cause) 对，按调用顺序。 */
    val warnings = mutableListOf<Pair<String, Throwable?>>()

    val messages: List<String> get() = warnings.map { it.first }

    override fun warn(message: String, cause: Throwable?) {
        warnings += message to cause
    }
}
