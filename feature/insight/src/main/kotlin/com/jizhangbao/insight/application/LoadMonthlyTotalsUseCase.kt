package com.jizhangbao.insight.application

import com.jizhangbao.core.domain.LedgerTotalsReader
import com.jizhangbao.core.domain.MonthlyTotals
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.toTimeRange
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/**
 * 取某个月的收支合计（`REQ-002`）。
 *
 * ## 它唯一的实质逻辑：年月 → 时间范围
 *
 * 其余的（怎么查、口径是什么）已经在端口实现与领域层里了。
 * 而这一步换算是**最容易悄悄错的地方**：
 *
 * - 用**本机时区**而不是 UTC（`BR-1`）：否则东八区用户在月初/月末会看到错的合计；
 * - 半开区间 `[月初 00:00, 次月初 00:00)`：闭区间会让跨月那一毫秒被算两次；
 * - 跨年与闰年交给 `YearMonth.plusMonths(1)` —— 自己拼 `month + 1` 是重新发明日历。
 *
 * 用 `java.time.YearMonth` 而不是自定义的 (year, month) 对：闰年与跨年它已经处理了。
 */
class LoadMonthlyTotalsUseCase @Inject constructor(
    private val reader: LedgerTotalsReader,
    private val clock: Clock,
) {

    /**
     * @param month 要统计的自然月；默认是**时钟当下所在的那个月**。
     *   默认值走 `clock`，所以测试里注入固定时钟就能得到确定结果。
     */
    suspend operator fun invoke(month: YearMonth = YearMonth.now(clock)): Outcome<MonthlyTotals> =
        reader.totalsIn(month.toTimeRange(clock.zone))
}
