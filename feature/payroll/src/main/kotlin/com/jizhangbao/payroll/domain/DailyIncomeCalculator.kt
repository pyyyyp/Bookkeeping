package com.jizhangbao.payroll.domain

import com.jizhangbao.core.domain.DayType
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.SignedMoney
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日收入与工资单的计算（`REQ-013`）。**纯函数**，没有 IO、没有状态。
 *
 * ## 为什么没有聚合
 *
 * `Payslip` 是算出来的：给它一个输入，永远得到同一个结果。
 * 它没有生命周期、没有要守住的不变式 —— 给它造聚合根只会多一个可变的壳。
 * **唯一需要守住的是算法的正确性，而那是测试的活。**
 *
 * ## 出勤为什么是参数
 *
 * "哪天出勤、哪天缺勤"是 **Worklog** 的事实，而 Worklog 还没实现。
 * 所以这里**不为它造一个假数据源、也不默认出勤**（`BR-5`）：
 * 调用方给什么就是什么。将来接线时，Worklog 通过内核端口提供它（`ADR-0008` 的读通路）。
 */
object DailyIncomeCalculator {

    /**
     * 日薪 = 月薪 ÷ 21.75，四舍五入到分。
     *
     * **整数运算**：「除以 21.75」与「乘 100 再除以 2175」是同一件事，
     * 但后者能在整数上做完，只留**一次**四舍五入。
     * 若用 `Double`，`8000 ÷ 21.75 = 367.816091954023` —— 工资单要对得上分，
     * 不能让二进制小数决定用户看到几毛几分（与 `REQ-005` 占比用整数千分点同一个理由）。
     */
    fun dailyRate(salary: MonthlySalary): Money {
        val hundredths = salary.amount.cents * 100
        val rounded = (hundredths + MonthlySalary.PAID_DAYS_PER_MONTH_HUNDREDTHS / 2) /
            MonthlySalary.PAID_DAYS_PER_MONTH_HUNDREDTHS
        return Money.ofCents(rounded)
    }

    /**
     * 某一天的计薪。
     *
     * | 日期类型 | 出勤 | 倍率 |
     * |---|---|---|
     * | 工作日 | 出勤 | `1×` |
     * | 工作日 | **缺勤** | `0`（`Q-015`） |
     * | 休息日 | 加班 | `2×` |
     * | 休息日 | 不加班 | `0` |
     * | 法定节假日 | 加班 | `3×` |
     * | 法定节假日 | 休息 | **`1×`**（`Q-015`：带薪） |
     *
     * @param worked 在**休息日或法定节假日**是否出勤（工作日正常上班不算加班）。
     * @param absent 在**工作日**是否缺勤。对休息日与法定节假日无意义（那两个看 [worked]）。
     */
    fun dailyIncome(
        date: LocalDate,
        dayType: DayType,
        rate: Money,
        worked: Boolean = false,
        absent: Boolean = false,
    ): DailyIncome {
        val multiplier = when (dayType) {
            DayType.WORKDAY -> if (absent) PayMultiplier.NONE else PayMultiplier.NORMAL
            DayType.REST_DAY -> if (worked) PayMultiplier.REST_DAY_OVERTIME else PayMultiplier.NONE
            DayType.STATUTORY_HOLIDAY ->
                if (worked) PayMultiplier.HOLIDAY_OVERTIME else PayMultiplier.NORMAL
        }
        return DailyIncome(
            date = date,
            dayType = dayType,
            multiplier = multiplier,
            amount = Money.ofCents(rate.cents * multiplier.factor),
        )
    }

    /**
     * 汇总一个月的工资单。
     *
     * @param days 该月每一天及其日期类型（由 **Calendar** 判定，`ADR-0011` 决策 4）。
     * @param workedDays 出勤的休息日/法定节假日。
     * @param absentDays 缺勤的工作日。
     */
    fun payslip(
        month: YearMonth,
        salary: MonthlySalary,
        days: List<Pair<LocalDate, DayType>>,
        workedDays: Set<LocalDate> = emptySet(),
        absentDays: Set<LocalDate> = emptySet(),
    ): Payslip {
        val rate = dailyRate(salary)
        val incomes = days.map { (date, dayType) ->
            dailyIncome(
                date = date,
                dayType = dayType,
                rate = rate,
                worked = date in workedDays,
                absent = date in absentDays,
            )
        }
        val total = Money.ofCents(incomes.sumOf { it.amount.cents })
        return Payslip(
            month = month,
            salary = salary,
            dailyRate = rate,
            days = incomes,
            amountDue = total,
            // 不硬凑：差额如实显示（见 Payslip 的 KDoc）
            differenceFromSalary = SignedMoney.ofCents(total.cents - salary.amount.cents),
        )
    }
}
