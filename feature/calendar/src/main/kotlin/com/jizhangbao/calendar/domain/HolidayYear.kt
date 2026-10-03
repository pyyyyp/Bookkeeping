package com.jizhangbao.calendar.domain

import com.jizhangbao.core.domain.DayType
import com.jizhangbao.core.domain.DayTypeResult
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 某一年的节假日与调休安排（**已经从外部格式映射进来的领域模型**）。
 *
 * `R6`：外部格式（那个文本文件）绝不进领域层 —— 它在这里已经被 Mapper 变成了日期集合。
 *
 * @param holidays 法定节假日。
 * @param workdays **调休**：被调成工作日的周末。
 */
data class HolidayYear(
    val year: Int,
    val holidays: Set<LocalDate> = emptySet(),
    val workdays: Set<LocalDate> = emptySet(),
)

/**
 * 判定规则（`REQ-012`）。**纯函数** —— 数据进、类型出。
 *
 * ## 顺序本身就是规则
 *
 * ```
 * 1. 覆盖表 → 用它标的类型      （法定节假日、调休都在这里）
 * 2. 周六/周日 → REST_DAY
 * 3. 其余 → WORKDAY
 * 4. 没有这一年的数据 → 跳过第 1 步，并把 missingYear 置 true
 * ```
 *
 * 反过来（先看星期几）会让**调休判错** —— 那个周六会被当成休息日，
 * 而工资的倍数因此差一倍。判定顺序不是实现细节，是业务规则。
 *
 * ## 为什么没有聚合
 *
 * 这里没有需要守住的不变式：节假日数据是外部事实，判定是纯推导。
 * 给它造一个聚合根只会多一个可变的壳（`ADR-0011` 决策 5 的同一推理）。
 *
 * @param year 该年的数据；`null` = 这一年还没有数据（见 [DayTypeResult.missingYear]）。
 */
fun typeOf(date: LocalDate, year: HolidayYear?): DayTypeResult {
    // 1. 覆盖优先：法定节假日与调休都在覆盖表里，且节假日优先于调休
    val overridden = when {
        year == null -> null
        date in year.holidays -> DayType.STATUTORY_HOLIDAY
        date in year.workdays -> DayType.WORKDAY
        else -> null
    }

    // 2/3. 周末规则
    val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val type = overridden ?: if (weekend) DayType.REST_DAY else DayType.WORKDAY

    // 4. 没有数据就说清楚：这个类型是**推**出来的，不是查出来的
    return DayTypeResult(date, type, missingYear = year == null)
}
