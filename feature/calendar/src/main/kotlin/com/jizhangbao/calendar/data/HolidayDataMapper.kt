package com.jizhangbao.calendar.data

import com.jizhangbao.calendar.domain.HolidayYear
import java.time.LocalDate

/**
 * 外部数据文件 → 领域模型的 Mapper（`R6`：外部格式绝不进领域层）。
 *
 * ## 为什么它只解析、不记日志
 *
 * 记日志要 `AppLogger`，那是基础设施。把两者混在一起，就得为了测"坏行会被跳过"
 * 而造一个假 logger —— 而这类假对象正是"测试替身掩盖真实行为"的起点。
 * 所以这里只**报告**坏了几行（[ParseResult.skippedLines]），
 * 由调用方（数据层）决定怎么记。**纯解析与副作用分开，测试才能只测规则。**
 *
 * ## 数据在哪一年
 *
 * **日期自带年份**，所以不存在"声明的年份与数据不符"这种状态（`ADR-0011` 决策 1）。
 * 某一年有数据 ⇔ 该年至少有一条日期行。
 */
data class ParseResult(
    /** 按年份分组的结果。只包含**至少有一条数据**的年份。 */
    val years: Map<Int, HolidayYear>,
    /** 跳过了几行（坏行 + 只写了一个词的半行）。只在 > 0 时值得记一条日志。 */
    val skippedLines: Int,
)

object HolidayDataMapper {

    private const val HOLIDAY = "holiday"
    private const val WORKDAY = "workday"

    fun parse(text: String): ParseResult {
        val holidays = mutableMapOf<Int, MutableSet<LocalDate>>()
        val workdays = mutableMapOf<Int, MutableSet<LocalDate>>()
        var skipped = 0

        text.lineSequence().forEach { raw ->
            // `#` 之后是注释；空行忽略。这样数据文件里可以写清"来源：国务院 X 年公告"。
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach

            val parts = line.split(' ')
            if (parts.size != 2) {
                skipped++
                return@forEach
            }
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull()
            if (date == null) {
                skipped++
                return@forEach
            }

            val bucket = when (parts[1].lowercase()) {
                HOLIDAY -> holidays
                WORKDAY -> workdays
                // 认不出的词也当坏行：**不要**猜它想说什么（P5）
                else -> {
                    skipped++
                    null
                }
            }
            bucket?.getOrPut(date.year) { mutableSetOf() }?.add(date)
        }

        val years = (holidays.keys + workdays.keys).associateWith { year ->
            HolidayYear(
                year = year,
                holidays = holidays[year].orEmpty().toSet(),
                workdays = workdays[year].orEmpty().toSet(),
            )
        }
        return ParseResult(years = years, skippedLines = skipped)
    }
}
