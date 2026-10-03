package com.jizhangbao.insight.application

import com.jizhangbao.core.domain.CategoryBreakdown
import com.jizhangbao.core.domain.LedgerTotalsReader
import com.jizhangbao.core.domain.MonthlyComparison
import com.jizhangbao.core.domain.MonthlyInsight
import com.jizhangbao.core.domain.MonthlyTotals
import com.jizhangbao.core.domain.MonthlyTrend
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.TrendPoint
import com.jizhangbao.core.domain.toTimeRange
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * 一次取齐合计区要的所有读模型（`T-022`）。
 *
 * ## 它取代了四个用例（为什么这是一次**行为不变**的重构）
 *
 * `REQ-002` 以来，这一屏的四块各自有一个用例，各自去问端口。到 `REQ-010` 为止，
 * 一次刷新打 **9 次** `totalsIn`（合计 1 + 环比 2 + 趋势 6），而它们其实只需要
 * **6 段不同的区间** —— "十月"被问了三次、"九月"被问了两次。
 *
 * 那些用例各自都"自足"（不依赖调用方先取好合计），这在**单个**需求里是对的取舍；
 * 但四块并排显示在同一屏上时，自足就变成了浪费。这里把"自足"提升到**这一屏**的层面：
 * 一次用例、一批查询、一份结果。
 *
 * ⚠️ **界面看到的东西一个都不变**：同样的四块、同样的口径、同样的失败行为。
 * 变的只是问几次。
 *
 * ## 口径仍然只有一处
 *
 * 每一段区间都走内核的 `YearMonth.toTimeRange`（`BR-1`）—— 与 Ledger 那边的合计口径
 * 同一个函数。这一点没有因为合并而改变，测试里仍然逐字断言。
 */
class LoadMonthlyInsightUseCase @Inject constructor(
    private val reader: LedgerTotalsReader,
    private val clock: Clock,
) {

    /**
     * @param month 用户当前查看的那个月。
     * @param trendMonths 趋势取几个月（含 [month]）。上个月必须落在窗口里 —— 环比要用它。
     */
    suspend operator fun invoke(
        month: YearMonth = YearMonth.now(clock),
        trendMonths: Int = DEFAULT_TREND_MONTHS,
    ): Outcome<MonthlyInsight> {
        val zone = clock.zone
        // 窗口：本月、上月、再往前…… 正好 trendMonths 段**互不相同**的区间
        val months = (0 until trendMonths).map { back -> month.minusMonths(back.toLong()) }

        return when (val totals = loadTotals(months, zone)) {
            is Outcome.Err -> Outcome.Err(totals.error)
            is Outcome.Ok -> when (val breakdown = loadBreakdown(month, zone)) {
                is Outcome.Err -> Outcome.Err(breakdown.error)
                is Outcome.Ok -> Outcome.Ok(assemble(month, months, totals.value, breakdown.value))
            }
        }
    }

    /**
     * 把窗口里每个月的合计取回来，**每段只问一次**（这就是本卡的全部意义）。
     *
     * 任一段失败就整体失败：拿 0 冒充"那个月没花钱"会让趋势与环比一起撒谎
     * （`REQ-009/BR-4`、`REQ-010/BR-4`）。四块要么一起成功、要么一起失败 ——
     * 同屏的四个数字因此不可能来自不同的刷新。
     */
    private suspend fun loadTotals(
        months: List<YearMonth>,
        zone: ZoneId,
    ): Outcome<Map<YearMonth, MonthlyTotals>> {
        val loaded = mutableMapOf<YearMonth, MonthlyTotals>()
        for (target in months) {
            when (val totals = reader.totalsIn(target.toTimeRange(zone))) {
                is Outcome.Ok -> loaded[target] = totals.value
                is Outcome.Err -> return Outcome.Err(totals.error)
            }
        }
        return Outcome.Ok(loaded)
    }

    /** 占比走的是另一条查询（按分类分组），同样只问一次、也只用当前这个月的区间。 */
    private suspend fun loadBreakdown(month: YearMonth, zone: ZoneId): Outcome<CategoryBreakdown> =
        reader.expensesByCategory(month.toTimeRange(zone))

    /**
     * 把取回来的东西拼成一份快照（纯函数，所以它不会失败）。
     *
     * 上个月在窗口里（`trendMonths ≥ 2` 时必然如此）；只有调用方把窗口裁到 1 个月时才取不到，
     * 那时按"空月"处理 —— 环比与趋势的语义都与"没有记账"一致。
     */
    private fun assemble(
        month: YearMonth,
        months: List<YearMonth>,
        totals: Map<YearMonth, MonthlyTotals>,
        breakdown: CategoryBreakdown,
    ): MonthlyInsight {
        val current = totals.getValue(month)
        val previous = totals[month.minusMonths(1)] ?: MonthlyTotals.ZERO

        return MonthlyInsight(
            totals = current,
            comparison = MonthlyComparison(current = current, previous = previous),
            trend = MonthlyTrend(months.map { TrendPoint(month = it, totals = totals.getValue(it)) }),
            breakdown = breakdown,
        )
    }

    companion object {
        /**
         * 趋势默认看六个月（`REQ-010` 的决定）。
         *
         * 取 6 也顺带保证了**上个月一定在窗口里** —— 环比要用它，窗口太短会让环比失去基线。
         */
        const val DEFAULT_TREND_MONTHS = 6
    }
}
