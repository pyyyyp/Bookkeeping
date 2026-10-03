package com.jizhangbao.payroll.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.payroll.application.LoadPayslipUseCase
import com.jizhangbao.payroll.domain.MonthlySalary
import com.jizhangbao.payroll.domain.Payslip
import com.jizhangbao.payroll.domain.repository.MonthlySalaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * 工资单页的逻辑（`REQ-017`）。
 *
 * ## 界面不做算术（`BR-1`）
 *
 * 日薪、倍率、合计、差额全部由 [LoadPayslipUseCase] / `Payslip` 给出 ——
 * 这里只负责"取哪个月的、算到哪一天、显示什么"。在界面里做减法就是第二处口径。
 *
 * ## 算到哪一天（`BR-3`）
 *
 * 当月算到**今天**（`REQ-015/AC-5`），已过去的月份算到**月末**。
 * 未来月份**根本不算** —— 用例会拒绝（`require`），所以这里显式挡在前面。
 */
@HiltViewModel
class PayslipViewModel @Inject constructor(
    private val salaries: MonthlySalaryRepository,
    private val loadPayslip: LoadPayslipUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PayslipUiState(month = YearMonth.now(clock)),
    )
    val state: StateFlow<PayslipUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onPreviousMonth() {
        moveTo(_state.value.month.minusMonths(1))
    }

    fun onNextMonth() {
        // 界面已经把它禁用了；这里再挡一次，因为状态也可能被别处改（防御，不是重复逻辑）
        val next = _state.value.month.plusMonths(1)
        if (!next.isAfter(YearMonth.now(clock))) moveTo(next)
    }

    /**
     * 保存月薪（`AC-1`）。
     *
     * ⚠️ 这是**新增一份**，不是改旧的（`Q-019`）：涨薪不改历史月份的工资。
     *
     * @param amountText 用户输入的金额（元）。解析不出或 ≤ 0 时**什么都不做**
     *   （界面会显示"只支持正数"这种提示由界面决定，这里不猜文案）。
     */
    fun saveSalary(amountText: String, effectiveFromText: String) {
        val cents = parseYuanToCents(amountText) ?: return
        val effectiveFrom = runCatching { LocalDate.parse(effectiveFromText.trim()) }.getOrNull() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, notice = null) }
            val saved = salaries.save(
                MonthlySalary(amount = Money.ofCents(cents), effectiveFrom = effectiveFrom),
            )
            _state.update {
                it.copy(isBusy = false, notice = if (saved is Outcome.Err) PayslipNotice.StorageFailed else null)
            }
            refresh()
        }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun moveTo(month: YearMonth) {
        _state.update { it.copy(month = month) }
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val month = _state.value.month
            val thisMonth = YearMonth.now(clock)
            val salaryResult = salaries.effectiveAt(month.atDay(1))
            val everResult = salaries.anyConfigured()
            val salary = (salaryResult as? Outcome.Ok)?.value
            _state.update { current ->
                current.copy(
                    salary = salary,
                    // 「这个月没有月薪」有两种，给用户的话完全不同（T-033 的真机缺口）
                    salaryEverConfigured = (everResult as? Outcome.Ok)?.value
                        ?: current.salaryEverConfigured,
                    // ⚠️ 未来月份**不调用**用例：它会 require 失败（那是对调用方的保护），
                    // 而这里在它之前就挡住了
                    payslip = if (salary != null && !month.isAfter(thisMonth)) {
                        loadPayslip(month, salary, upTo = upToFor(month, thisMonth))
                    } else {
                        null
                    },
                    canGoToNextMonth = month.isBefore(thisMonth),
                    notice = if (salaryResult is Outcome.Err) PayslipNotice.StorageFailed else current.notice,
                )
            }
        }
    }

    /** 当月算到今天，已过去的月份算到月末（`BR-3`）。 */
    private fun upToFor(month: YearMonth, thisMonth: YearMonth): LocalDate =
        if (month == thisMonth) LocalDate.now(clock) else month.atEndOfMonth()

    companion object {
        /**
         * "8000" / "8000.5" / "8000.00" → 分。解析不出或非正数 → `null`。
         *
         * 用 `BigDecimal` 而不是 `Double`：`0.1` 在二进制里是无限循环，
         * 用它解析金额会出现 `7999.999999999999` 这种值（与 `REQ-005` 占比、
         * `REQ-013` 日薪同一条理由：**钱不经过浮点**）。
         */
        fun parseYuanToCents(text: String): Long? {
            val trimmed = text.trim()
            val value = if (trimmed.isEmpty()) null else trimmed.toBigDecimalOrNull()
            val cents = value?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()
            // 非正数一律拒绝：月薪 0 或负数会让工资单看起来正常却全错
            return cents?.takeIf { it > 0 }
        }
    }
}
