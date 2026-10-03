package com.jizhangbao.payroll.domain

import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.SignedMoney
import java.time.YearMonth

/**
 * 一个结算月的工资单（`REQ-013`）。**算出来的读模型** —— 重算即得同一结果。
 *
 * ## `differenceFromSalary` 为什么必须存在
 *
 * `21.75` 是**法定月计薪天数**（年平均口径），而任意单月的实际工作/休息天数
 * 都不可能正好是 21.75，所以 [amountDue] 与月薪**本来就不相等**。
 *
 * 想"凑齐"只能加一个调整项，而那个项没法解释 —— 于是"这个月为什么多了 3 块钱"
 * 会变成一个查不清的谜。**把差额如实显示出来**，比凑齐诚实，也比凑齐有用：
 * 它是"这个月节假日多/缺勤多"的直接体现。
 */
data class Payslip(
    val month: YearMonth,
    val salary: MonthlySalary,
    val dailyRate: Money,
    val days: List<DailyIncome>,
    /** 应付金额 = Σ `days.amount`。 */
    val amountDue: Money,
    /** `amountDue − 月薪`。可为负（例如缺勤多）。 */
    val differenceFromSalary: SignedMoney,
)
