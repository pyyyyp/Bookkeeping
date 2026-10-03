package com.jizhangbao.payroll.testing

import com.jizhangbao.core.domain.AttendedDay
import com.jizhangbao.core.domain.WorklogReader
import java.time.LocalDate

/**
 * 工时端口的测试替身（`REQ-015`）。
 *
 * 只实现**契约**：区间内的那些天，以及"没有记录的日子不出现"。
 * 真实的实现在 `feature:worklog`（本轮还没做持久化）。
 */
class FakeWorklogReader(
    private val attended: List<AttendedDay> = emptyList(),
) : WorklogReader {

    /** 记录下每次被问的区间，方便断言"确实只问了一次、且区间正确"。 */
    val requestedRanges = mutableListOf<Pair<LocalDate, LocalDate>>()

    override suspend fun attendedDays(from: LocalDate, toInclusive: LocalDate): List<AttendedDay> {
        requestedRanges += from to toInclusive
        return attended
            .filter { !it.date.isBefore(from) && !it.date.isAfter(toInclusive) }
            .sortedBy { it.date }
    }
}
