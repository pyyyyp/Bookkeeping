package com.jizhangbao.payroll.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `MonthlySalary` 的 Room 实体（外部模型，`REQ-017/AC-1`）。
 *
 * ## 为什么有 id，而领域里没有
 *
 * 领域的 `MonthlySalary` 是"金额 + 生效日期"这一对事实（`Q-019`），它不需要身份。
 * 但涨薪是**新增一份**（不是改旧的）—— 于是同一天可能存着两份（用户改了主意），
 * 需要一个主键才能区分。**身份是存储的事，事实是领域的事**，所以 id 只出现在这里。
 *
 * @param effectiveFromEpochDay 生效日期存**纪元日**（`LocalDate.toEpochDay()`）——
 * 它是"哪一天"，不是"哪一刻"，用 `epochMilli` 会引入一个不存在的时区问题。
 */
@Entity(tableName = "monthly_salary")
data class MonthlySalaryEntity(
    @PrimaryKey val id: String,
    val amountCents: Long,
    val effectiveFromEpochDay: Long,
)
