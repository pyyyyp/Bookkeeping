package com.jizhangbao.core.domain

import java.time.YearMonth
import java.time.ZoneId

/**
 * 自然月 → 该月在本机时区下的**半开区间** `[月初 00:00, 次月初 00:00)`（`REQ-002/BR-1`）。
 *
 * ## 为什么它住内核，而不是 Insight 内部（`REQ-007/BR-1`）
 *
 * **口径只能有一份**。合计、占比、分类下钻三处都必须按同一段区间取数 ——
 * 它们会被用户并排看到（"占比说娱乐 40000，点开清单加起来却不是"）。
 * 这个函数原先在 `:feature:insight` 里是 `internal`，于是 Ledger 做下钻时
 * **只能照抄一遍** —— 那就是两套口径的开始。
 *
 * 搬到内核之后，两个上下文调的是同一个函数：**不一致在结构上就不可能**。
 *
 * 抽成独立函数（而不是塞进用例）是为了能被单独测试：边界（跨年、闰年 2 月、
 * 非 UTC 时区）是这里唯一会出错的地方。
 */
fun YearMonth.toTimeRange(zone: ZoneId): TimeRange = TimeRange(
    start = atDay(1).atStartOfDay(zone).toInstant(),
    end = plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
)
