package com.jizhangbao.insight.testing

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

    val requestedRanges = mutableListOf<TimeRange>()

    override suspend fun totalsIn(range: TimeRange): Outcome<MonthlyTotals> {
        requestedRanges += range
        return outcome
    }
}
