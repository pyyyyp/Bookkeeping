package com.jizhangbao.payroll.application

import com.jizhangbao.core.domain.DayType
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.WorkCalendar
import com.jizhangbao.core.domain.WorklogReader
import com.jizhangbao.payroll.domain.DailyIncome
import com.jizhangbao.payroll.domain.DailyIncomeCalculator
import com.jizhangbao.payroll.domain.MonthlySalary
import com.jizhangbao.payroll.domain.Payslip
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * 算一个月的工资单（`REQ-015`）—— **三个内核在这里第一次接起来**：
 *
 * ```
 * Calendar（这天是什么日子）  ┐
 *                             ├─→ 门槛判定 ─→ 日收入 ─→ 工资单
 * Worklog（这天干了多久）      ┘
 * ```
 *
 * ## 门槛为什么在这里
 *
 * `Q-017` 说"满 8 小时算 1 天"。放 Worklog 就得多一条到 Calendar 的边
 * （它得知道哪天是休息日才能判"要不要算加班"）；放这里，Payroll 本来就认识 Calendar。
 * 见 `ADR-0012` 决策 2：**门槛是钱的事，归 Payroll。**
 *
 * ## 为什么不会失败
 *
 * 两个端口都是**同步纯查询**，而且各自已经把 IO 失败吞成了"没有数据 / 数据没填"
 * （`ADR-0011` 决策 2 的同一原则）。所以这里没有可失败之处 —— 返回 `Payslip` 而不是 `Outcome`。
 * 将来若某个端口改成会失败，这个签名会跟着改，那是一次**显式**的改动。
 */
class LoadPayslipUseCase @Inject constructor(
    private val calendar: WorkCalendar,
    private val worklog: WorklogReader,
) {

    /**
     * @param month 结算月。
     * @param salary 这个月适用的月薪（选哪一份是 `Q-019` 的事，这里只要结果）。
     * @param upTo 算到哪一天为止（通常传"今天"）。
     *   **未来的日子不参与**：把还没到的日子当成"没出勤"会让工资单凭空少一截。
     */
    operator fun invoke(month: YearMonth, salary: MonthlySalary, upTo: LocalDate): Payslip {
        val first = month.atDay(1)
        // 问一个还没开始的月份的工资单是调用方的 bug，不是业务结果
        require(!upTo.isBefore(first)) { "不能算一个还没开始的月份：$month（算到 $upTo）" }

        val last = minOf(month.atEndOfMonth(), upTo)
        // 一次查询拿全月的出勤事实（没有记录的日子**不会**以 0 出现在里面）
        val attendedMinutes = worklog.attendedDays(first, last).associate { it.date to it.confirmedMinutes }

        val dayTypes = (1..last.dayOfMonth).map { dayOfMonth ->
            val date = month.atDay(dayOfMonth)
            date to calendar.typeOf(date)
        }
        val rate = DailyIncomeCalculator.dailyRate(salary)
        val days = dayTypes.map { (date, dayType) ->
            incomeOf(date, dayType.type, rate, attendedMinutes[date] ?: 0)
        }

        return DailyIncomeCalculator.payslip(
            month = month,
            salary = salary,
            dailyRate = rate,
            days = days,
            // 只要有一天来自"这一年还没有节假日数据"，就要说出来（否则用户以为今年没有节假日）
            holidayDataMissing = dayTypes.any { it.second.missingYear },
        )
    }

    /**
     * 某一天的计薪。这里只有**门槛**这一条新规则，倍率仍由 `DailyIncomeCalculator` 决定：
     *
     * | 日期类型 | 判定 | 结果 |
     * |---|---|---|
     * | 工作日 | 不看记录（`Q-026`：没记录 ≠ 缺勤） | `1×` |
     * | 休息日 | 够门槛算加班 | `2×`，否则 `0` |
     * | 法定节假日 | 够门槛算加班 | `3×`，否则**带薪** `1×` |
     */
    private fun incomeOf(date: LocalDate, dayType: DayType, rate: Money, minutes: Int): DailyIncome =
        when (dayType) {
            DayType.WORKDAY -> DailyIncomeCalculator.dailyIncome(date, dayType, rate)
            DayType.REST_DAY, DayType.STATUTORY_HOLIDAY -> DailyIncomeCalculator.dailyIncome(
                date = date,
                dayType = dayType,
                rate = rate,
                worked = minutes >= MINUTES_FOR_OVERTIME_DAY,
            )
        }

    companion object {
        /**
         * 加班一天所需的最少已确认分钟数（`Q-017` 的 v1 简化）。
         *
         * `Q-017` 原本是"满 8 小时算 1 天、4–8 小时算半天、不足 4 小时不计"，
         * 但**半天需要有理数倍率**（法定节假日的一半是 `1.5×`），而现在的倍率是整数。
         * 所以 v1 只做整天，半天记为 `Q-025`。
         */
        const val MINUTES_FOR_OVERTIME_DAY = 8 * 60
    }
}
