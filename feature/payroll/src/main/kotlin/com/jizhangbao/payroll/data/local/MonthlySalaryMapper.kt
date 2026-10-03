package com.jizhangbao.payroll.data.local

import com.jizhangbao.core.domain.Money
import com.jizhangbao.payroll.domain.MonthlySalary
import java.time.LocalDate
import java.util.UUID

/**
 * 实体 ↔ 领域（`R6`）。
 *
 * ⚠️ 表里的 `id` **不进领域**：领域的 `MonthlySalary` 只需要"金额 + 生效日期"这两个事实
 * （`Q-019` 的语义）。身份只在存储层用来区分"同一天存了两份"这种情况。
 */
internal object MonthlySalaryMapper {

    fun toDomain(entity: MonthlySalaryEntity): MonthlySalary = MonthlySalary(
        amount = Money.ofCents(entity.amountCents),
        effectiveFrom = LocalDate.ofEpochDay(entity.effectiveFromEpochDay),
    )

    /** 存进去时生成身份（`uuid` 由调用方给，便于测试里稳定）。 */
    fun toEntity(salary: MonthlySalary, id: String): MonthlySalaryEntity = MonthlySalaryEntity(
        id = id,
        amountCents = salary.amount.cents,
        effectiveFromEpochDay = salary.effectiveFrom.toEpochDay(),
    )

    fun randomId(): String = UUID.randomUUID().toString()
}
