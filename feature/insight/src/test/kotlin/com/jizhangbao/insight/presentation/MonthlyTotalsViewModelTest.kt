package com.jizhangbao.insight.presentation

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.MonthlyTotals
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.insight.application.LoadCategoryShareUseCase
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

        assertEquals(1, reader.requestedRanges.size)
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
        // AC-7：两个数字同屏，必须来自同一次刷新、同一个区间
        assertEquals(reader.requestedRanges.last(), reader.requestedBreakdownRanges.last())
        assertEquals(Instant.parse("2026-08-31T16:00:00Z"), reader.requestedRanges.last().start)
    }

    @Test
    fun `当月时不允许翻到下一月`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canGoToNextMonth)

        vm.onNextMonth()
        advanceUntilIdle()

        // 意图被忽略：没有多出一次请求
        assertEquals(1, reader.requestedRanges.size)
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
