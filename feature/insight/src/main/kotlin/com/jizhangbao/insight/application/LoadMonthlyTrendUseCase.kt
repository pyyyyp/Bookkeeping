package com.jizhangbao.insight.application

import com.jizhangbao.core.domain.LedgerTotalsReader
import com.jizhangbao.core.domain.MonthlyTrend
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.TrendPoint
import com.jizhangbao.core.domain.toTimeRange
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/**
 * 最近几个月的支出趋势（`REQ-010`）。
 *
 * ## 还是不新端口（与 `REQ-009` 同一个理由）
 *
 * 端口的方法叫 `totalsIn(range)` —— 它收的是**任意区间**。所以"最近六个月"只是
 * 同一个问题问六次，**没动 Ledger 一行代码**。为它加一个 `trendIn(...)` 只会把
 * "循环"这件事挪到数据层，而数据层并不知道"最近几个月"是什么意思（那是展示的取舍）。
 *
 * ## 任一个月读不出来就整体失败（`BR-4`）
 *
 * 不能把失败的月份当成 0：一条假的 ¥0.00 会让整条趋势**撒谎** ——
 * 用户会以为"那个月我没花钱"，而事实是"没读到"。这与环比里"不拿 0 冒充没有变化"
 * 是同一条规则。
 *
 * ## 每次刷新会打多少次端口（诚实记账）
 *
 * 一次刷新现在会问：合计 1 次 + 环比 2 次 + 占比 1 次（另一条路径）+ 趋势 6 次。
 * 其中趋势的 6 次**包含**合计那一次与环比的两次 —— 这是"用例自足"的代价
 * （见 `T-019` 卡的说明：不依赖调用方先取好本月合计）。
 * 若将来这变成问题，正确的修法是**一次快照**（一个用例把这四块一起取），
 * 而不是让用例之间互相传值 —— 那会让它们彼此耦合。
 */
class LoadMonthlyTrendUseCase @Inject constructor(
    private val reader: LedgerTotalsReader,
    private val clock: Clock,
) {

    /**
     * @param endMonth 趋势的**最新一个月**；默认是时钟当下所在的那个月。
     * @param months 取几个月（含 [endMonth]）。
     */
    suspend operator fun invoke(
        endMonth: YearMonth = YearMonth.now(clock),
        months: Int = DEFAULT_MONTHS,
    ): Outcome<MonthlyTrend> {
        val zone = clock.zone
        val points = mutableListOf<TrendPoint>()

        // 从新到旧：endMonth, endMonth-1, …
        for (back in 0 until months) {
            val month = endMonth.minusMonths(back.toLong())
            when (val totals = reader.totalsIn(month.toTimeRange(zone))) {
                is Outcome.Ok -> points += TrendPoint(month = month, totals = totals.value)
                // 任一个月读不出来就整体失败（BR-4）：一条假的 0 会让趋势撒谎
                is Outcome.Err -> return Outcome.Err(totals.error)
            }
        }

        return Outcome.Ok(MonthlyTrend(points))
    }

    companion object {
        /**
         * 默认看六个月。
         *
         * 这是**展示的取舍**（一屏能看下、又不至于只剩两三个月没意义），不是业务规则 ——
         * 所以它在这里，而不是在聚合或仓储里。
         */
        const val DEFAULT_MONTHS = 6
    }
}
