package com.jizhangbao.payroll.presentation

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.payroll.application.LoadPayslipUseCase
import com.jizhangbao.payroll.domain.MonthlySalary
import com.jizhangbao.payroll.domain.PayMultiplier
import com.jizhangbao.payroll.domain.repository.MonthlySalaryRepository
import com.jizhangbao.payroll.testing.FakeWorkCalendar
import com.jizhangbao.payroll.testing.FakeWorklogReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 工资单页的逻辑（`REQ-017`）。
 *
 * ## 三条最想守住的
 *
 * 1. **没配月薪时不算**（`BR-2`）：返回 `null`，而不是按 0 算出一份
 *    看起来正常、实际全错的工资单；
 * 2. **未来月份不算**（`AC-5`）：用例会 `require` 拒绝，界面必须挡在它前面；
 * 3. **差额如实**（`AC-2`）：`8000` 的月薪、一个月只有 5 天数据时，差额必然不为零 ——
 *    这条断言就是在守护"不硬凑"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PayslipViewModelTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Instant = Instant.parse("2026-10-14T03:00:00Z") // 北京时间 11:00
    private val today: LocalDate = LocalDate.of(2026, 10, 14)
    private val clock: Clock = Clock.fixed(now, zone)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 月薪的内存替身：`effectiveAt` 用与 SQL 相同的规则（取"到那天为止最晚生效的那一份"）。 */
    private class FakeSalaries(
        private val stored: MutableList<MonthlySalary> = mutableListOf(),
        private val failOnSave: Boolean = false,
    ) : MonthlySalaryRepository {
        val saved = mutableListOf<MonthlySalary>()

        override suspend fun save(salary: MonthlySalary): Outcome<Unit> {
            if (failOnSave) return Outcome.Err(DomainError.Technical.Storage)
            saved += salary
            stored += salary
            return Outcome.Ok(Unit)
        }

        override suspend fun effectiveAt(date: LocalDate): Outcome<MonthlySalary?> = Outcome.Ok(
            stored.filter { !it.effectiveFrom.isAfter(date) }
                .maxByOrNull { it.effectiveFrom },
        )
    }

    private fun viewModel(
        salaries: FakeSalaries = FakeSalaries(),
        missingHolidayData: Boolean = false,
    ) = PayslipViewModel(
        salaries = salaries,
        loadPayslip = LoadPayslipUseCase(
            calendar = FakeWorkCalendar(missingYear = missingHolidayData),
            worklog = FakeWorklogReader(),
        ),
        clock = clock,
    )

    private fun salaryFrom2026Jan() = MonthlySalary(
        amount = Money.ofCents(800_000),
        effectiveFrom = LocalDate.of(2026, 1, 1),
    )

    // ---- AC-1：配月薪 ----

    @Test
    fun `AC-1 保存月薪_存下来的是输入的金额与生效日期`() = runTest {
        val salaries = FakeSalaries()
        val viewModel = viewModel(salaries)

        viewModel.saveSalary("8000.00", "2026-01-01")

        val saved = salaries.saved.single()
        assertEquals(Money.ofCents(800_000), saved.amount)
        assertEquals(LocalDate.of(2026, 1, 1), saved.effectiveFrom)
    }

    @Test
    fun `AC-1 金额解析走整数分_不经过浮点`() {
        assertEquals(800_000L, PayslipViewModel.parseYuanToCents("8000"))
        assertEquals(800_050L, PayslipViewModel.parseYuanToCents("8000.50"))
        assertEquals(1L, PayslipViewModel.parseYuanToCents("0.01"))
        // 非正数与垃圾一律拒绝（月薪 0 会让工资单看起来正常却全错）
        assertNull(PayslipViewModel.parseYuanToCents("0"))
        assertNull(PayslipViewModel.parseYuanToCents("-100"))
        assertNull(PayslipViewModel.parseYuanToCents(""))
        assertNull(PayslipViewModel.parseYuanToCents("八千"))
    }

    @Test
    fun `AC-1 生效日期解析不出时_什么都不存`() = runTest {
        val salaries = FakeSalaries()
        val viewModel = viewModel(salaries)

        viewModel.saveSalary("8000", "不是日期")

        assertTrue(salaries.saved.isEmpty())
    }

    // ---- BR-2：没月薪就不算 ----

    @Test
    fun `BR-2 没配月薪时不算工资单`() = runTest {
        val viewModel = viewModel()

        assertNull(viewModel.state.value.salary)
        // 关键：是 null，不是"一份全 0 的工资单"
        assertNull(viewModel.state.value.payslip)
    }

    // ---- AC-2 / AC-6：算出来的是什么 ----

    @Test
    fun `AC-2 有月薪时算出日薪与合计_差额如实不为零`() = runTest {
        val salaries = FakeSalaries(mutableListOf(salaryFrom2026Jan()))
        val viewModel = viewModel(salaries)

        val payslip = viewModel.state.value.payslip
        requireNotNull(payslip)
        // 日薪 = 8000 ÷ 21.75 → ¥367.82（REQ-013/AC-1）
        assertEquals(Money.ofCents(36_782), payslip.dailyRate)
        // 只算到今天（10-14）→ 14 天
        assertEquals(today.dayOfMonth, payslip.days.size)
        // 合计就是各日之和（REQ-013/AC-5）
        assertEquals(payslip.days.sumOf { it.amount.cents }, payslip.amountDue.cents)
        // ⚠️ 而它**小于** 14 × 日薪：`Q-012` 的原话是"不加班的周末没有"，
        // 所以那 14 天里有几天是 0 —— 这正是"逐日可解释"要让人看见的东西。
        // （我第一版把它写成了"等于 14 × 日薪"，被这条测试纠正了。）
        assertTrue(payslip.days.any { it.multiplier == PayMultiplier.NONE })
        assertTrue(payslip.amountDue.cents < 36_782L * today.dayOfMonth)
        // ⚠️ 差额**必然不为零** —— 21.75 是年平均值，凑齐只能靠一个无法解释的调整项
        assertFalse(payslip.differenceFromSalary.cents == 0L)
    }

    @Test
    fun `AC-6 节假日数据没填时_工资单上带出来`() = runTest {
        val salaries = FakeSalaries(mutableListOf(salaryFrom2026Jan()))
        val viewModel = viewModel(salaries, missingHolidayData = true)

        assertTrue(viewModel.state.value.payslip?.holidayDataMissing == true)
    }

    @Test
    fun `BR-3 已过去的月份算到月末`() = runTest {
        val salaries = FakeSalaries(mutableListOf(salaryFrom2026Jan()))
        val viewModel = viewModel(salaries)

        viewModel.onPreviousMonth() // 9 月（30 天）

        val payslip = viewModel.state.value.payslip
        requireNotNull(payslip)
        assertEquals(YearMonth.of(2026, 9), payslip.month)
        assertEquals(30, payslip.days.size)
        // 10-01 是工作日（按周末规则推定），所以不会是 0
        assertTrue(payslip.days.any { it.multiplier == PayMultiplier.NORMAL })
    }

    // ---- AC-5：未来月份 ----

    @Test
    fun `AC-5 当月时不能翻到下一月`() = runTest {
        val viewModel = viewModel()

        assertEquals(YearMonth.of(2026, 10), viewModel.state.value.month)
        assertFalse(viewModel.state.value.canGoToNextMonth)

        viewModel.onNextMonth()

        // 不是"点了没反应"，而是**禁用**；而且状态确实没动
        assertEquals(YearMonth.of(2026, 10), viewModel.state.value.month)
    }

    @Test
    fun `AC-5 翻到上月之后_可以翻回来`() = runTest {
        val viewModel = viewModel()

        viewModel.onPreviousMonth()
        assertTrue(viewModel.state.value.canGoToNextMonth)

        viewModel.onNextMonth()
        assertEquals(YearMonth.of(2026, 10), viewModel.state.value.month)
        assertFalse(viewModel.state.value.canGoToNextMonth)
    }

    @Test
    fun `存储失败时_状态里给出提示`() = runTest {
        val viewModel = viewModel(FakeSalaries(failOnSave = true))

        viewModel.saveSalary("8000", today.toString())

        assertEquals(PayslipNotice.StorageFailed, viewModel.state.value.notice)
    }
}
