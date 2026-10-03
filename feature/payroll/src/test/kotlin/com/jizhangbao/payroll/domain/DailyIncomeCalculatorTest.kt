package com.jizhangbao.payroll.domain

import com.jizhangbao.core.domain.DayType
import com.jizhangbao.core.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日收入与工资单（`REQ-013`）。
 *
 * 夹具金额特意取 `¥8000.00` —— 因为 `8000 ÷ 21.75 = 367.8160919…`，
 * 正是"取整会出错"的那种数；用整数月薪（比如 10000）测不出这件事。
 */
class DailyIncomeCalculatorTest {

    private val salary = MonthlySalary(
        amount = Money.ofCents(800_000),
        effectiveFrom = LocalDate.of(2026, 1, 1),
    )
    private val rate = DailyIncomeCalculator.dailyRate(salary)

    @Test
    fun `AC-1 日薪按 21_75 折算并四舍五入到分`() {
        // 8000.00 元 × 100 ÷ 2175 = 36781.6… 分 → 进位到 36782 分
        assertEquals(Money.ofCents(36_782), rate)
        assertEquals("¥367.82", rate.toString())
    }

    @Test
    fun `AC-1 取整边界_太小则舍去`() {
        // 1 分月薪：1 × 100 ÷ 2175 = 0.046 分 → 0（不足半分舍去，且不会出现负数或 1 分）
        val tiny = MonthlySalary(Money.ofCents(1), LocalDate.of(2026, 1, 1))

        assertEquals(Money.ZERO, DailyIncomeCalculator.dailyRate(tiny))
    }

    @Test
    fun `AC-2 工作日出勤 1 倍_缺勤 0`() {
        val workday = LocalDate.of(2026, 10, 14)

        val attended = DailyIncomeCalculator.dailyIncome(workday, DayType.WORKDAY, rate)
        val absent = DailyIncomeCalculator.dailyIncome(workday, DayType.WORKDAY, rate, absent = true)

        assertEquals(PayMultiplier.NORMAL, attended.multiplier)
        assertEquals(rate, attended.amount)
        // Q-015 定案：缺勤当天不计
        assertEquals(PayMultiplier.NONE, absent.multiplier)
        assertEquals(Money.ZERO, absent.amount)
    }

    @Test
    fun `AC-3 休息日加班 2 倍_不加班 0`() {
        val restDay = LocalDate.of(2026, 10, 17)

        val overtime = DailyIncomeCalculator.dailyIncome(restDay, DayType.REST_DAY, rate, worked = true)
        val idle = DailyIncomeCalculator.dailyIncome(restDay, DayType.REST_DAY, rate)

        assertEquals(PayMultiplier.REST_DAY_OVERTIME, overtime.multiplier)
        assertEquals(Money.ofCents(rate.cents * 2), overtime.amount)
        // Q-012 原话："不加班的周末没有"
        assertEquals(Money.ZERO, idle.amount)
    }

    @Test
    fun `AC-4 法定节假日加班 3 倍_休息仍带薪 1 倍`() {
        val holiday = LocalDate.of(2026, 10, 1)

        val overtime = DailyIncomeCalculator.dailyIncome(holiday, DayType.STATUTORY_HOLIDAY, rate, worked = true)
        val resting = DailyIncomeCalculator.dailyIncome(holiday, DayType.STATUTORY_HOLIDAY, rate)

        assertEquals(PayMultiplier.HOLIDAY_OVERTIME, overtime.multiplier)
        assertEquals(Money.ofCents(rate.cents * 3), overtime.amount)
        // ⚠️ Q-015 定案：法定节假日**休息也带薪**。改动这一行会改掉所有人的工资。
        assertEquals(PayMultiplier.NORMAL, resting.multiplier)
        assertEquals(rate, resting.amount)
    }

    @Test
    fun `AC-5 汇总等于各日之和_差额如实显示且不硬凑`() {
        val workedDay = LocalDate.of(2026, 10, 14)
        val absentDay = LocalDate.of(2026, 10, 15)
        val thirdWorkday = LocalDate.of(2026, 10, 16)
        val restDay = LocalDate.of(2026, 10, 17)
        val holiday = LocalDate.of(2026, 10, 1)

        val payslip = DailyIncomeCalculator.payslip(
            month = YearMonth.of(2026, 10),
            salary = salary,
            days = listOf(
                holiday to DayType.STATUTORY_HOLIDAY,
                workedDay to DayType.WORKDAY,
                absentDay to DayType.WORKDAY,
                thirdWorkday to DayType.WORKDAY,
                restDay to DayType.REST_DAY,
            ),
            absentDays = setOf(absentDay),
        )

        // 1（节假日带薪）+ 1 + 0（缺勤）+ 1 + 0（周末不加班）= 3 × 日薪
        assertEquals(Money.ofCents(rate.cents * 3), payslip.amountDue)
        assertEquals(payslip.days.sumOf { it.amount.cents }, payslip.amountDue.cents)

        // ⚠️ 关键断言：**没有硬凑成月薪**。21.75 是年平均值，
        // 任意单月的日收入之和本来就不会等于月薪；凑齐只能靠一个无法解释的调整项。
        assertNotEquals(salary.amount, payslip.amountDue)
        assertEquals(
            payslip.amountDue.cents - salary.amount.cents,
            payslip.differenceFromSalary.cents,
        )
        // 只有 5 天数据 → 差额是负的（这个月还没过完），且措辞与 REQ-009 的环比同一套
        assertEquals(true, payslip.differenceFromSalary.isNegative)
        assertEquals("-¥6896.54", payslip.differenceFromSalary.toString())
    }
}
