package com.jizhangbao.core.domain

import java.time.Duration
import java.time.Instant

/**
 * 半开时间区间 `[start, end)`。
 *
 * 用于工时时段（`WorkSession`）与结算周期（`PayPeriod`）。
 *
 * 为什么是半开区间：相邻的两个工作时段 `[9:00, 12:00)` 与 `[12:00, 13:00)`
 * 不应被判为重叠，且不应有任何一个瞬间同时属于两个时段。
 *
 * 见 docs/00-charter/glossary.md#Worklog
 */
data class TimeRange(val start: Instant, val end: Instant) {

    init {
        require(end.isAfter(start)) {
            "结束时间必须晚于开始时间：$start → $end（零长度或倒置区间无业务含义）"
        }
    }

    val duration: Duration get() = Duration.between(start, end)

    /** 是否包含某瞬间。半开区间：含 start，不含 end */
    fun contains(instant: Instant): Boolean =
        !instant.isBefore(start) && instant.isBefore(end)

    /** 是否与另一区间重叠。半开区间下，首尾相接不算重叠 */
    fun overlaps(other: TimeRange): Boolean =
        start.isBefore(other.end) && other.start.isBefore(end)

    /** 是否完整包含另一区间 */
    fun contains(other: TimeRange): Boolean =
        !start.isAfter(other.start) && !end.isBefore(other.end)
}
