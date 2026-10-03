package com.jizhangbao.core.common

import android.util.Log

/**
 * [AppLogger] 的 Android 实现：写到 logcat（`ADR-0010` 决策的实现侧）。
 *
 * 绑在 `:app`（组合根），与其它基础设施一致。
 *
 * 它**只做转发**，不做格式化、不加时间戳、不做脱敏 ——
 * 脱敏的责任在调用方（不把 PII 传进来），因为这里根本不知道哪个字段是敏感数据。
 */
class LogcatLogger(private val tag: String = "Jizhangbao") : AppLogger {

    override fun warn(message: String, cause: Throwable?) {
        if (cause == null) {
            Log.w(tag, message)
        } else {
            // 把异常交给 Log：它打印类型、消息与调用栈 —— 这正是排查需要的
            Log.w(tag, message, cause)
        }
    }
}
