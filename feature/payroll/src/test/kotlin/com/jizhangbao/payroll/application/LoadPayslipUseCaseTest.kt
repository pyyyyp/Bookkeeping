package com.jizhangbao.payroll.application

import com.jizhangbao.core.domain.AttendedDay
import com.jizhangbao.core.domain.Money
import com.jizhangbao.payroll.domain.MonthlySalary
import com.jizhangbao.payroll.domain.PayMultiplier
import com.jizhangbao.payroll.testing.FakeWorkCalendar
import com.jizhangbao.payroll.testing.FakeWorklogReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * 工资单与出勤的集成（`REQ-015`）—— **三个内核第一次接起来**。
 *
 * ⚠️ 夹具日期仍然**从星期几推出来**（`T-025`/`T-027` 的同一条纪律）：
 * 若测试自己依赖"2026-10-01 是周四"，那它验证的是我的记忆，不是规则。
 */
class LoadPayslipUseCaseTest {

    private val salary = MonthlySalary(
        amount = Money.ofCents(800_000), // ¥8000.00 → 日薪 ¥367.82
        effectiveFrom = LocalDate.of(2026, 1, 1),
    )
    private val rate = Money.ofCents(36_782)

    private val month = YearMonth.of(2026, 10)
    private val anchor = LocalDate.of(2026, 10, 1)

    private val aWednesday: LocalDate = anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
    private val aSaturday: LocalDate = anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))

    private fun useCase(
        holidays: Set<LocalDate> = emptySet(),
        attended: List<AttendedDay> = emptyList(),
        missingYear: Boolean = false,
    ): Pair<LoadPayslipUseCase, FakeWorklogReader> {
        val worklog = FakeWorklogReader(attended)
        val calendar = FakeWorkCalendar(holidays = holidays, missingYear = missingYear)
        return LoadPayslipUseCase(calendar, worklog) to worklog
    }

    /**
     * 只留一天：把"这一天"的判定从整月的噪音里隔离出来。
     *
     * ⚠️ [minutes] 为 `null` 表示**没有这一天的记录**（不是"记录为 0"）——
     * `AttendedDay` 的不变量要求分钟数为正（它表示"**有**出勤事实"），
     * 而"没有记录"在事实层面就是**没有这一条**。两者的区别正是 `Q-026` 的核心：
     * 没记录 ≠ 缺勤，也 ≠ 出勤 0 分钟。
     */
    private fun incomeOn(
        date: LocalDate,
        minutes: Int?,
        holidays: Set<LocalDate> = emptySet(),
    ) = useCase(
        holidays = holidays,
        attended = if (minutes == null) emptyList() else listOf(AttendedDay(date, minutes)),
    )
        .first(month = month, salary = salary, upTo = date)
        .days
        .single { it.date == date }

    @Test
    fun `AC-1 工作日没记录也按 1 倍`() {
        // Q-026：没记录 ≠ 缺勤。月薪的默认含义就是"该上班的日子都上了"
        val day = incomeOn(aWednesday, minutes = null)

        assertEquals(PayMultiplier.NORMAL, day.multiplier)
        assertEquals(rate, day.amount)
    }

    @Test
    fun `AC-2 休息日够门槛 2 倍_不够 0`() {
        assertEquals(PayMultiplier.REST_DAY_OVERTIME, incomeOn(aSaturday, 8 * 60).multiplier)
        assertEquals(Money.ofCents(rate.cents * 2), incomeOn(aSaturday, 8 * 60).amount)
        assertEquals(PayMultiplier.NONE, incomeOn(aSaturday, 7 * 60).multiplier)
        assertEquals(Money.ZERO, incomeOn(aSaturday, 7 * 60).amount)
    }

    @Test
    fun `AC-3 法定节假日够门槛 3 倍_不够仍带薪 1 倍`() {
        val holiday = aSaturday // 用周六当法定节假日，顺便说明"日期类型不由星期几决定"

        val overtime = incomeOn(holiday, 8 * 60, holidays = setOf(holiday))
        val resting = incomeOn(holiday, 7 * 60, holidays = setOf(holiday))

        assertEquals(PayMultiplier.HOLIDAY_OVERTIME, overtime.multiplier)
        assertEquals(Money.ofCents(rate.cents * 3), overtime.amount)
        // Q-015 定案：法定节假日休息**带薪**
        assertEquals(PayMultiplier.NORMAL, resting.multiplier)
        assertEquals(rate, resting.amount)
    }

    @Test
    fun `AC-4 门槛是线的两边_7 小时 59 分与 8 小时整`() {
        assertEquals(PayMultiplier.NONE, incomeOn(aSaturday, 8 * 60 - 1).multiplier)
        assertEquals(PayMultiplier.REST_DAY_OVERTIME, incomeOn(aSaturday, 8 * 60).multiplier)
    }

    @Test
    fun `AC-5 只算到今天为止_未来的日子不参与`() {
        val upTo = aWednesday
        val (useCase, worklog) = useCase()

        val payslip = useCase(month = month, salary = salary, upTo = upTo)

        // 只到今天：天数 == 今天在本月的日序
        assertEquals(upTo.dayOfMonth, payslip.days.size)
        assertEquals(upTo, payslip.days.last().date)
        // 而且**一次**问全月（不是每天问一次）
        assertEquals(listOf(month.atDay(1) to upTo), worklog.requestedRanges)
    }

    @Test
    fun `AC-6 节假日数据没填时_工资单要带出来`() {
        val (useCase, _) = useCase(missingYear = true)

        val payslip = useCase(month = month, salary = salary, upTo = aWednesday)

        // 否则用户会以为"今年没有节假日"，而真相是数据还没填
        assertTrue(payslip.holidayDataMissing)
    }

    @Test
    fun `有数据时不谎报缺数据`() {
        val (useCase, _) = useCase(missingYear = false)

        assertFalse(useCase(month = month, salary = salary, upTo = aWednesday).holidayDataMissing)
    }

    @Test
    fun `一个月还没开始时_是调用方的 bug`() {
        val (useCase, _) = useCase()

        // 这是编程错误而不是业务结果，所以用 require 明确拒绝（BR-5 说明了为什么不用 Outcome）
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            useCase(month = month, salary = salary, upTo = month.atDay(1).minusDays(1))
        }
    }
}
