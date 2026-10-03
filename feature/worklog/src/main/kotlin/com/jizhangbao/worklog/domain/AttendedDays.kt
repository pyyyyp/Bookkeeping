package com.jizhangbao.worklog.domain

import com.jizhangbao.core.domain.AttendedDay
import java.time.LocalDate

/**
 * 从工时时段取出**出勤事实**（`REQ-014`）。纯函数。
 *
 * ## 只认 `CONFIRMED`
 *
 * `RUNNING`（还在跑）、`FINISHED`（跑完未确认）、`DISCARDED`（作废）都不算 ——
 * **一个还在跑的时段不能变成工资**。
 *
 * ## 同一天多段**相加**，不取最外层跨度
 *
 * 上午 `09:00–12:00` + 下午 `13:00–18:00` 是 `8` 小时，不是 `9` 小时：
 * 中间那顿午饭不是工时。"取最外层跨度"是最容易顺手写出来的实现，所以有一条测试专门盯着它。
 *
 * ## 不足一分钟的时段会被滤掉
 *
 * `AttendedDay` 的不变量是"分钟数为正"（它表示**有**出勤事实）。
 * 一段 30 秒的记录是噪声，不是一天的出勤 —— 所以在这里滤掉，
 * 而不是把不变量放松成"可以为 0"（那会让"有事实"这件事失去意义）。
 */
object AttendedDays {

    fun from(
        sessions: List<WorkSession>,
        from: LocalDate,
        toInclusive: LocalDate,
    ): List<AttendedDay> = sessions
        .filter { it.state == SessionState.CONFIRMED }
        .filter { !it.day.isBefore(from) && !it.day.isAfter(toInclusive) }
        .groupBy { it.day }
        .mapNotNull { (day, ofThatDay) ->
            val minutes = ofThatDay.sumOf { it.confirmedMinutes }
            // 0 分钟的"出勤"不是事实，只是一段太短的记录
            if (minutes > 0) AttendedDay(date = day, confirmedMinutes = minutes) else null
        }
        .sortedBy { it.date }
}
