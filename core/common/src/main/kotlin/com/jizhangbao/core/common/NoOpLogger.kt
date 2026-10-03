package com.jizhangbao.core.common

/**
 * 什么都不做的 [AppLogger]（`ADR-0010`）。
 *
 * 用在两个地方：
 * - **单元测试**：被测代码照常调用 `warn`，只是没人听（避免 `android.util.Log` 的桩异常）；
 * - **预览 / 非 Android 环境**：那里没有 logcat。
 *
 * 需要断言"到底记了什么"时，别用它 —— 写一个记录型 fake（见测试里的 `RecordingLogger`）。
 */
object NoOpLogger : AppLogger {
    override fun warn(message: String, cause: Throwable?) = Unit
}
