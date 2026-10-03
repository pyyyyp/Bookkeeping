package com.jizhangbao.insight.application

import com.jizhangbao.core.domain.LedgerTotalsReader
import com.jizhangbao.core.domain.MonthlyComparison
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.toTimeRange
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/**
 * 本月与上月的支出环比（`REQ-009`）。
 *
 * ## 为什么没有新端口、也没有新方法（`BR-5`）
 *
 * 跨上下文那个端口的方法叫 `totalsIn(range)` —— 它收的是**任意区间**。
 * 所以"上个月"只是"同一件事换个区间"，为它加端口或加方法，
 * 等于把同一件事包装成两层（`ADR-0008` 的推理：端口的价值在于跨上下文，
 * 而这里连"多一次查询"都算不上，只是同一个方法换个参数）。
 *
 * ## 两段区间都走内核的 `toTimeRange`（`BR-1`）
 *
 * 与合计、占比、下钻**同一个函数**。环比和合计并排显示在同一屏上：
 * 一边用这个月的区间、另一边用别的什么口径，用户看到的就是"两个数对不上"。
 */
class LoadMonthlyComparisonUseCase @Inject constructor(
    private val reader: LedgerTotalsReader,
    private val clock: Clock,
) {

    /**
     * @param month 要比的那个月；默认是**时钟当下所在的月**（所以测试注入固定时钟即可确定）。
     */
    suspend operator fun invoke(month: YearMonth = YearMonth.now(clock)): Outcome<MonthlyComparison> {
        val zone = clock.zone
        val current = reader.totalsIn(month.toTimeRange(zone))
        val previous = reader.totalsIn(month.minusMonths(1).toTimeRange(zone))

        return when {
            // 任一段读不出来就整体失败：拿 0 冒充"上月没花钱"比不显示更糟（BR-4）
            current is Outcome.Err -> Outcome.Err(current.error)
            previous is Outcome.Err -> Outcome.Err(previous.error)
            else -> Outcome.Ok(
                MonthlyComparison(
                    current = (current as Outcome.Ok).value,
                    previous = (previous as Outcome.Ok).value,
                ),
            )
        }
    }
}
