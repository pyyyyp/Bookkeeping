package com.jizhangbao.insight.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhangbao.core.domain.CategoryBreakdown
import com.jizhangbao.core.domain.MonthlyTrend
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.insight.application.LoadMonthlyInsightUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/**
 * 合计区的状态持有者。
 *
 * ## 这里没有业务规则
 *
 * 「哪个月的哪些条目算进来」是端口实现的口径，「月份怎么算」是用例的换算，
 * 这一层只做三件事：记住当前月份、把结果放进状态、把用户的翻月意图转成一次加载。
 *
 * ## 为什么翻月是「不能超过当月」而不是「随便翻」
 *
 * `REQ-002/BR-7`。用户会补记过去，但不会预支未来；
 * 允许翻到未来只会让人看到一堆零并怀疑数据丢了。
 */
@HiltViewModel
internal class MonthlyTotalsViewModel @Inject constructor(
    /**
     * `T-022`：**一个**用例取齐四块读模型。
     *
     * 以前这里有四个用例（合计 / 占比 / 环比 / 趋势），各自"自足"地再去问一次端口 ——
     * 到 `REQ-010` 为止一次刷新打 **9 次** `totalsIn`，而它们只需要 **6 段不同的区间**。
     * 合并之后"同一批查询"在类型上成立，重复无从产生。
     *
     * ⚠️ 这是**行为不变**的重构：界面看到的四块、口径、失败行为都一样，变的只是问几次。
     */
    private val loadMonthlyInsight: LoadMonthlyInsightUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonthlyTotalsUiState.initial(clock))
    val uiState: StateFlow<MonthlyTotalsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * 重新加载当前月份的合计。
     *
     * 记账或删除之后由组合根调用（见 `T-009` 卡里的说明）：
     * 本模块**不认识** Ledger，所以它不会去订阅账本的变化，
     * 而是由 `:app` 在数据变动后通知一次。这是 R2 的直接后果，不是偷懒。
     */
    fun refresh() {
        val month = _uiState.value.month
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val insight = loadMonthlyInsight(month)) {
                is Outcome.Ok -> _uiState.update {
                    val value = insight.value
                    // 四块一起写进状态：它们来自**同一个快照**，所以不可能出现
                    // "合计是 10 月、占比是 9 月"（REQ-005/AC-7）这种同屏矛盾
                    it.copy(
                        totals = value.totals,
                        breakdown = value.breakdown,
                        comparison = value.comparison,
                        trend = value.trend,
                        isLoading = false,
                        hasFailure = false,
                    )
                }
                // 任一块读不出来都算"查不到"（显式失败），不是"这个月没花钱"。
                // 失败时的清理与重构前**逐项一致**：占比清空、环比置 null、趋势置空
                // （界面据此隐藏它们），而合计保持原值 —— 那是 T-022 之前的既有行为
                is Outcome.Err -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasFailure = true,
                        breakdown = CategoryBreakdown.EMPTY,
                        comparison = null,
                        trend = MonthlyTrend.EMPTY,
                    )
                }
            }
        }
    }

    fun onPreviousMonth() {
        moveTo(_uiState.value.month.minusMonths(1))
    }

    fun onNextMonth() {
        if (!_uiState.value.canGoToNextMonth) return
        moveTo(_uiState.value.month.plusMonths(1))
    }

    private fun moveTo(month: YearMonth) {
        _uiState.update {
            it.copy(
                month = month,
                // 当月时禁用「下一月」（BR-7）。月份相减由 YearMonth 处理跨年，不必自己算
                canGoToNextMonth = month.isBefore(YearMonth.now(clock)),
                hasFailure = false,
            )
        }
        refresh()
    }
}
