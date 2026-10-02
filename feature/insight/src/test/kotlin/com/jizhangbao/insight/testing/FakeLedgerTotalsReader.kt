package com.jizhangbao.insight.testing

import com.jizhangbao.core.domain.CategoryBreakdown
import com.jizhangbao.core.domain.LedgerTotalsReader
import com.jizhangbao.core.domain.MonthlyTotals
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.TimeRange

/**
 * 内存版端口 fake。
 *
 * 它记录**收到的时间范围** —— 这是本卡最需要钉住的东西：
 * 「年月 → 时间范围」的换算错了，界面上的数字就会整体偏一个月，
 * 而那看起来就像"数据不对"，很难归因。
 */
internal class FakeLedgerTotalsReader : LedgerTotalsReader {

    var outcome: Outcome<MonthlyTotals> = Outcome.Ok(MonthlyTotals.ZERO)

    /** `REQ-005`：分类占比的结果，测试按需指定。 */
    var breakdownOutcome: Outcome<CategoryBreakdown> = Outcome.Ok(CategoryBreakdown.EMPTY)

    val requestedRanges = mutableListOf<TimeRange>()

    /** 占比那次调用收到的范围。与 [requestedRanges] 分开记：两者必须**同一个范围**（`BR-5`）。 */
    val requestedBreakdownRanges = mutableListOf<TimeRange>()

    override suspend fun totalsIn(range: TimeRange): Outcome<MonthlyTotals> {
        requestedRanges += range
        return outcome
    }

    override suspend fun expensesByCategory(range: TimeRange): Outcome<CategoryBreakdown> {
        requestedBreakdownRanges += range
        return breakdownOutcome
    }
}
