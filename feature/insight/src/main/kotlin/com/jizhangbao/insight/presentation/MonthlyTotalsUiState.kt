package com.jizhangbao.insight.presentation

import com.jizhangbao.core.domain.MonthlyTotals
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
            )
        }
    }
}
