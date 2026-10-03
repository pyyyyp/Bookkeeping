package com.jizhangbao.calendar.data

import android.content.Context
import com.jizhangbao.calendar.domain.HolidayYear
import com.jizhangbao.calendar.domain.typeOf
import com.jizhangbao.core.common.AppLogger
import com.jizhangbao.core.domain.DayTypeResult
import com.jizhangbao.core.domain.WorkCalendar
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工作日历的端口实现（`REQ-012`）：从 assets 读内置数据，按规则判定。
 *
 * ## 为什么只读一次
 *
 * 这份数据**一年变一次**，而 Payroll 算一个月工资会问 28~31 次。
 * 每次查询都去读一遍文件，是把"一年一次"的更新成本摊到每一次判定上。
 * 所以用 `lazy` 读一次、之后只查内存 —— 代价是文件改了要重启，
 * 而"每年改一次、改完重启"正是这个数据的使用方式。
 *
 * ## 坏数据的两条路，都**不崩**
 *
 * | 情况 | 行为 |
 * |---|---|
 * | 有坏行 | 跳过坏行 + 计数 + 记日志（一行坏不毁掉一整年） |
 * | 整个文件读不出来 | 所有年份视为无数据 + 记日志（`DayTypeResult.missingYear = true`，界面据此提示） |
 *
 * **静默退化最糟**：用户会以为"今年没有节假日"，而真相是"数据没读到"（`ADR-0011` 决策 2）。
 */
@Singleton
class AssetWorkCalendar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) : WorkCalendar {

    private val years: Map<Int, HolidayYear> by lazy { load() }

    override fun typeOf(date: LocalDate): DayTypeResult = typeOf(date, years[date.year])

    private fun load(): Map<Int, HolidayYear> = try {
        val text = context.assets.open(DATA_FILE).bufferedReader().use { it.readText() }
        val parsed = HolidayDataMapper.parse(text)
        if (parsed.skippedLines > 0) {
            // 只说"坏了几行"，不把行内容打进日志 —— 数据文件里不该有 PII，但也不必冒这个险
            logger.warn("节假日数据有 ${parsed.skippedLines} 行无法解析，已跳过")
        }
        parsed.years
    } catch (e: IOException) {
        logger.warn("节假日数据读不出来：今年将按周末规则判定", e)
        emptyMap()
    }

    companion object {
        /** 数据文件名。格式与更新方式见 `ADR-0011` 决策 1。 */
        const val DATA_FILE = "holiday_data.txt"
    }
}
