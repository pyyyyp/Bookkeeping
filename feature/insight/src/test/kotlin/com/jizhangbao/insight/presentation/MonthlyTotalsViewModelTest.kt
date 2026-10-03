package com.jizhangbao.insight.presentation

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.MonthlyTotals
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.toTimeRange
import com.jizhangbao.insight.application.LoadCategoryShareUseCase
import com.jizhangbao.insight.application.LoadMonthlyComparisonUseCase
import com.jizhangbao.insight.application.LoadMonthlyTotalsUseCase
import com.jizhangbao.insight.testing.FakeLedgerTotalsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.Month
import java.time.ZoneId

/**
 * 合计区状态持有者的测试（`REQ-005`）。
 *
 * 本卡在 ViewModel 上只有一条实质规则：**合计与占比在同一次刷新里取、用同一个月份**
 * （`AC-7`）。所以这里的断言都围绕"两个 fake 收到的区间是否一致"展开 ——
 * 那是这条规则唯一可自动化的形态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyTotalsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val reader = FakeLedgerTotalsReader()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), zone)

    private fun viewModel() = MonthlyTotalsViewModel(
        loadMonthlyTotals = LoadMonthlyTotalsUseCase(reader, clock),
        loadCategoryShare = LoadCategoryShareUseCase(reader, clock),
        // REQ-009：环比用同一个端口 fake（它收的就是任意区间）
        loadMonthlyComparison = LoadMonthlyComparisonUseCase(reader, clock),
        clock = clock,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `初始化时合计与占比都加载一次`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        // 一次刷新取**三段**区间：合计问一次本月，环比用例自带"本月 + 上月"（REQ-009）。
        //
        // 多出来的那一次本月 SUM 是**刻意的取舍**：环比用例只收一个 month，因此**自足** ——
        // 它不依赖"调用方已经先取好本月合计"。代价是每次刷新多一条最便宜的聚合查询。
        // 这里把顺序逐字钉住，将来若改成"复用已取的本月合计"，这条断言会提醒改测试。
        val october = YearMonth.of(2026, 10).toTimeRange(zone)
        val september = YearMonth.of(2026, 9).toTimeRange(zone)
        assertEquals(listOf(october, october, september), reader.requestedRanges)
        assertEquals(1, reader.requestedBreakdownRanges.size)
        assertEquals(Month.OCTOBER, vm.uiState.value.month.month)
    }

    @Test
    fun `翻月时两者拿到同一个新月份`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPreviousMonth()
        advanceUntilIdle()

        assertEquals(Month.SEPTEMBER, vm.uiState.value.month.month)
        // AC-7：两个数字同屏，占比必须与**合计**来自同一段区间
        val september = YearMonth.of(2026, 9).toTimeRange(zone)
        assertEquals(september, reader.requestedBreakdownRanges.last())
        // 而环比（REQ-009）多问了一段上月的 —— 它不该被误当成合计的区间
        assertEquals(YearMonth.of(2026, 8).toTimeRange(zone), reader.requestedRanges.last())
        assertEquals(september, reader.requestedRanges[reader.requestedRanges.size - 2])
    }

    @Test
    fun `当月时不允许翻到下一月`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canGoToNextMonth)

        val before = reader.requestedRanges.size
        vm.onNextMonth()
        advanceUntilIdle()

        // 意图被忽略：**没有多出**一次刷新。
        // 不断言具体次数 —— REQ-009 之后一次刷新本身就是两段区间
        assertEquals(before, reader.requestedRanges.size)
    }

    @Test
    fun `翻到过去之后可以再翻回来`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPreviousMonth()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canGoToNextMonth)

        vm.onNextMonth()
        advanceUntilIdle()
        assertEquals(Month.OCTOBER, vm.uiState.value.month.month)
    }

    @Test
    fun `占比读不出来时显式失败_而不是当成没有支出`() = runTest(dispatcher) {
        reader.breakdownOutcome = Outcome.Err(DomainError.Technical.Storage)

        val vm = viewModel()
        advanceUntilIdle()

        // "查不到"与"这个月没花钱"必须分开：后者是空清单，前者要提示
        assertTrue(vm.uiState.value.hasFailure)
        assertTrue(vm.uiState.value.breakdown.isEmpty)
    }

    @Test
    fun `合计读不出来时也显式失败`() = runTest(dispatcher) {
        reader.outcome = Outcome.Err(DomainError.Technical.Storage)

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hasFailure)
        assertEquals(MonthlyTotals.ZERO, vm.uiState.value.totals)
    }
}
