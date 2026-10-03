package com.jizhangbao.insight.presentation

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.MonthlyTotals
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.toTimeRange
import com.jizhangbao.insight.application.LoadCategoryShareUseCase
import com.jizhangbao.insight.application.LoadMonthlyComparisonUseCase
import com.jizhangbao.insight.application.LoadMonthlyTrendUseCase
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
        // REQ-010：趋势同样用这个端口 fake（对最近六段区间各问一次）
        loadMonthlyTrend = LoadMonthlyTrendUseCase(reader, clock),
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

        // 一次刷新取**三块**：合计 1 次、环比 2 次（本月 + 上月）、趋势 6 次（含前两者）。
        //
        // 这里把序列逐字钉住 —— 它是"每个用例自足"的代价（见 T-019 / T-021 卡的说明）：
        // 将来若改成"一次快照"，这条断言会**提醒改测试**，而不是悄悄多打几次查询。
        val october = YearMonth.of(2026, 10).toTimeRange(zone)
        val september = YearMonth.of(2026, 9).toTimeRange(zone)
        val august = YearMonth.of(2026, 8).toTimeRange(zone)
        val july = YearMonth.of(2026, 7).toTimeRange(zone)
        val june = YearMonth.of(2026, 6).toTimeRange(zone)
        val may = YearMonth.of(2026, 5).toTimeRange(zone)
        assertEquals(
            listOf(
                october, // 合计（REQ-002）
                october, september, // 环比：本月 + 上月（REQ-009）
                october, september, august, july, june, may, // 趋势：最近六个月（REQ-010）
            ),
            reader.requestedRanges,
        )
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
        // 趋势的最后一行是翻月后的第 6 个月（REQ-010）—— 它显然不该被误当成合计
        assertEquals(YearMonth.of(2026, 4).toTimeRange(zone), reader.requestedRanges.last())
        // ⚠️ 不能用 `requestedRanges.first()` 代表"这一次的合计"：**初始化那次刷新也在同一个列表里**
        // （它是十月）。能钉住的是"翻月后九月这一段确实被问了" —— 合计、环比、趋势各一次
        assertTrue(reader.requestedRanges.count { it == september } >= 3)
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
