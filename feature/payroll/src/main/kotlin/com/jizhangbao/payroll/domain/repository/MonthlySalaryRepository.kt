package com.jizhangbao.payroll.domain.repository

import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.payroll.domain.MonthlySalary
import java.time.LocalDate

/**
 * 月薪的仓储（`REQ-017/AC-1`）。
 *
 * 接口说领域语言：存一份月薪、问"某一天生效的是哪一份"。
 */
interface MonthlySalaryRepository {

    /**
     * 存一份月薪。
     *
     * ⚠️ 这是**新增**，不是"改当前的"（`Q-019`）：涨薪就是加一份新的，
     * 于是历史月份的工资不会被追溯改掉。
     */
    suspend fun save(salary: MonthlySalary): Outcome<Unit>

    /**
     * [date] 那天生效的月薪；**从没配过时返回 `null`**。
     *
     * ⚠️ `null` 不等于"月薪是 0"：前者要界面说"先配月薪"，后者会算出一份
     * 看起来正常、实际全错的工资单（`REQ-017/BR-2`）。
     */
    suspend fun effectiveAt(date: LocalDate): Outcome<MonthlySalary?>
}
