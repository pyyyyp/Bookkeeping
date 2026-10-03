package com.jizhangbao.payroll.presentation

import com.jizhangbao.payroll.domain.MonthlySalary
import com.jizhangbao.payroll.domain.Payslip
import java.time.YearMonth

/**
 * 工资单页的状态（`REQ-017`）。
 *
 * ## 三件事必须分开表达
 *
 * 1. **还没配月薪**（[salary] 为 `null`）→ 界面说"先配月薪"，**不是**按 0 算
 *    （`BR-2`：按 0 算会得到一份看起来正常、实际全错的工资单）；
 * 2. **配了但算出来对不上月薪**（[payslip] 的差额 ≠ 0）→ 那是**预期的**，要解释一句
 *    （`21.75` 是年平均值）；
 * 3. **没数据**（`holidayDataMissing`）→ 明说，否则用户以为今年没有节假日。
 *
 * ⚠️ 状态里没有用户可见文案：文案在 Compose 层由 `stringResource` 决定（`R12`）。
 */
data class PayslipUiState(
    val month: YearMonth,
    /** 当月时禁用「下一月」—— 未来的工资单算不出来（`AC-5`）。 */
    val canGoToNextMonth: Boolean = false,
    /** 该月生效的月薪；`null` = **从没配过**（不等于 0）。 */
    val salary: MonthlySalary? = null,
    /** 该月的工资单；没有月薪时为 `null`（算不出来）。 */
    val payslip: Payslip? = null,
    /**
     * 是否**配过任何一份**月薪（`T-034`）。
     *
     * ⚠️ 它必须和 [salary] 一起才能说对话：`salary == null` 有**两种**原因 ——
     * 从没配过（"去下面配一份"）与配了但该月还没生效（"把生效日期填早一点"）。
     * `T-033` 的真机缺口就是只有 [salary] 一个信息，于是"刚保存完"显示成"还没有配月薪"。
     */
    val salaryEverConfigured: Boolean = false,
    val isBusy: Boolean = false,
    val notice: PayslipNotice? = null,
)

/** 一次性提示（持续性的事走状态，不走这里 —— `T-031` 的真机教训）。 */
sealed interface PayslipNotice {
    data object StorageFailed : PayslipNotice
}
