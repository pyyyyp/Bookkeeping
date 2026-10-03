package com.jizhangbao.payroll.domain

import com.jizhangbao.core.domain.Money
import java.time.LocalDate

/**
 * 约定的固定月工资总额（`Q-012`），**带生效日期**（`Q-019`）。
 *
 * 涨薪是**新增一份**，不是改旧的 —— 否则历史月份的工资会被追溯重算，
 * 而"上个月的工资条怎么变了"是最不该发生的事之一。
 *
 * @param effectiveFrom 从这一天起适用。
 */
data class MonthlySalary(
    val amount: Money,
    val effectiveFrom: LocalDate,
) {
    companion object {
        /**
         * 法定月计薪天数 `21.75 = (365 − 104) ÷ 12`（`Q-012`）。
         *
         * ⚠️ 存成**百分之一**（2175）而不是 `21.75`——因为日薪要用整数算
         * （见 [DailyIncomeCalculator.dailyRate]）。`Double` 一旦进来，
         * `8000 ÷ 21.75` 就会得到 `367.816091954023` 这种数字，而工资单要对得上分。
         */
        const val PAID_DAYS_PER_MONTH_HUNDREDTHS = 2175
    }
}
