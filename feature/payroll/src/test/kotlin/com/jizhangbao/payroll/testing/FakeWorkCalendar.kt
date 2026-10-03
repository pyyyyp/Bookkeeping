package com.jizhangbao.payroll.testing

import com.jizhangbao.core.domain.DayType
import com.jizhangbao.core.domain.DayTypeResult
import com.jizhangbao.core.domain.WorkCalendar
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 工作日历端口的测试替身（`REQ-015`）。
 *
 * 默认按**周末规则**判（周六周日休息、其余工作日），可以用 [holidays] / [extraWorkdays]
 * 覆盖单日 —— 与真实实现的判定顺序一致（覆盖优先）。
 *
 * [missingYear] 用来模拟"这一年还没有节假日数据"（`REQ-012/AC-4`）。
 */
class FakeWorkCalendar(
    private val holidays: Set<LocalDate> = emptySet(),
    private val extraWorkdays: Set<LocalDate> = emptySet(),
    private val missingYear: Boolean = false,
) : WorkCalendar {

    override fun typeOf(date: LocalDate): DayTypeResult {
        val type = when {
            date in holidays -> DayType.STATUTORY_HOLIDAY
            date in extraWorkdays -> DayType.WORKDAY
            date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY -> DayType.REST_DAY
            else -> DayType.WORKDAY
        }
        return DayTypeResult(date = date, type = type, missingYear = missingYear)
    }
}
