package com.jizhangbao.payroll.domain

import com.jizhangbao.core.domain.DayType
import com.jizhangbao.core.domain.Money
import java.time.LocalDate

/**
 * 某一天的计薪结果（`REQ-013`）。
 *
 * 它存在的意义是**可解释**：拿到工资条那天，每一天为什么是 `1×`、`2×`、`3×` 还是 `0`，
 * 都要能从这一行看出来 —— 所以这里留着 [dayType] 与 [multiplier]，
 * 而不是只存一个金额。
 */
data class DailyIncome(
    val date: LocalDate,
    val dayType: DayType,
    val multiplier: PayMultiplier,
    val amount: Money,
)
