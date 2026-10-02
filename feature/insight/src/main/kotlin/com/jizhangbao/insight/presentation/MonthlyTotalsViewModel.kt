package com.jizhangbao.insight.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.insight.application.LoadMonthlyTotalsUseCase
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
    private val loadMonthlyTotals: LoadMonthlyTotalsUseCase,
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
            when (val result = loadMonthlyTotals(month)) {
                is Outcome.Ok -> _uiState.update {
                    it.copy(totals = result.value, isLoading = false, hasFailure = false)
                }
                is Outcome.Err -> _uiState.update {
                    // 查不到与"这个月没花钱"必须区分：前者是错误，后者是零
                    it.copy(isLoading = false, hasFailure = true)
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
