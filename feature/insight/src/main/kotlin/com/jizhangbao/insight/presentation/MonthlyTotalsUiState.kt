package com.jizhangbao.insight.presentation

import com.jizhangbao.core.domain.CategoryBreakdown
import com.jizhangbao.core.domain.MonthlyComparison
import com.jizhangbao.core.domain.MonthlyTotals
import com.jizhangbao.core.domain.MonthlyTrend
import java.time.Clock
import java.time.YearMonth

/**
 * 合计区的界面状态。
 *
 * 所有字段都是不可变数据：界面只读，ViewModel 只产出新副本。
 *
 * `failure` 是**布尔而不是文案**——用户可见字符串必须来自资源文件
 * （AGENTS.md 第 7 节第 12 条），与 Ledger 那边的做法一致。
 */
internal data class MonthlyTotalsUiState(
    /** 当前统计的月份。 */
    val month: YearMonth,
    /** 该月的合计；加载完成前是 [MonthlyTotals.ZERO]（界面显示零而不是闪烁）。 */
    val totals: MonthlyTotals,
    val isLoading: Boolean,
    val hasFailure: Boolean,
    /**
     * 是否允许翻到下一月。
     *
     * `REQ-002/BR-7`：未来月份没有意义（用户不会"预支未来"），
     * 所以当月时「下一月」是禁用的——**不是**点了没反应，而是明确禁用。
     */
    val canGoToNextMonth: Boolean,
    /**
     * 该月支出按分类的占比（`REQ-005`）。
     *
     * 与 [totals] 放在**同一个状态**里是刻意的：两者必须来自同一次刷新、同一个月，
     * 否则会出现"合计是 10 月的、占比是 9 月的"这种同屏矛盾（`AC-7`）。
     * 加载完成前是 [CategoryBreakdown.EMPTY]。
     */
    val breakdown: CategoryBreakdown,
    /**
     * 与上月的支出环比（`REQ-009`）。
     *
     * `null` 表示**不显示**，三种情况共用它：还在加载、读失败（`BR-4`：绝不拿 0 冒充
     * "没有变化"）、以及本月没有记账（`AC-4`：没有数据的月份谈不上比较）。
     */
    val comparison: MonthlyComparison?,
    /**
     * 最近几个月的支出趋势（`REQ-010`）。
     *
     * 与 [comparison] 同一个立场：读不出来时是 [MonthlyTrend.EMPTY]（**不显示**），
     * 而不是"六个月都是零" —— 那会让趋势撒谎。
     */
    val trend: MonthlyTrend,
) {

    val canGoToPreviousMonth: Boolean get() = true

    companion object {
        /**
         * 初始状态：**当月**、零合计。
         *
         * 「现在」由调用方（`Clock`）给出，不在这里 `YearMonth.now()`——
         * 否则状态构造就不纯，也没法稳定测试。
         */
        fun initial(clock: Clock): MonthlyTotalsUiState {
            val thisMonth = YearMonth.now(clock)
            return MonthlyTotalsUiState(
                month = thisMonth,
                totals = MonthlyTotals.ZERO,
                isLoading = true,
                hasFailure = false,
                canGoToNextMonth = false,
                breakdown = CategoryBreakdown.EMPTY,
                comparison = null,
                trend = MonthlyTrend.EMPTY,
            )
        }
    }
}
