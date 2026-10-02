package com.jizhangbao.ledger.presentation

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.application.DeleteLedgerEntryUseCase
import com.jizhangbao.ledger.application.LoadRecentEntriesUseCase
import com.jizhangbao.ledger.application.RecordLedgerEntryUseCase
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.testing.FakeLedgerEntryRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * `LedgerViewModel` 的自动化测试 —— 补上 `T-007`/`T-008` 留下的缺口。
 *
 * ## 为什么这一层必须测
 *
 * 它里面**没有业务规则**（规则在聚合里），但有一堆「编排决定」，而这些决定同样会出错：
 * 保存失败时该不该清空输入框？切方向后原来的分类还留着吗？删除失败后确认框要不要留着？
 * 这些都不是领域规则，却直接决定用户会不会丢数据或重复提交。之前它们只被手工冒烟覆盖过。
 *
 * ## 为什么需要协程测试库
 *
 * `viewModelScope` 默认跑在 `Dispatchers.Main` 上，而单元测试里没有 Android 主线程 ——
 * 不替换就会抛 `Module with the Main dispatcher had failed to initialize`。
 * 这里用 `StandardTestDispatcher`（而不是 `UnconfinedTestDispatcher`）：
 * 它让协程**不会**在调用处同步跑完，于是"点保存到状态更新之间"的中间态也能被断言。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeLedgerEntryRepository()
    private val now: Instant = Instant.parse("2026-10-02T03:04:05Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun viewModel() = LedgerViewModel(
        recordEntry = RecordLedgerEntryUseCase(repository, clock),
        loadEntries = LoadRecentEntriesUseCase(repository),
        deleteEntry = DeleteLedgerEntryUseCase(repository),
        clock = clock,
    )

    private suspend fun seed(cents: Long = 1250) {
        val entry = (LedgerEntry.record(
            direction = EntryDirection.Expense,
            amount = com.jizhangbao.core.domain.Money.ofCents(cents),
            categoryId = CategoryId("food"),
            occurredAt = now,
            bookedAt = now,
        ) as Outcome.Ok).value
        repository.add(entry)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `初始状态是空表单加当月发生时间`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("", state.amountText)
        assertEquals(EntryDirection.Expense, state.direction)
        assertNull(state.selectedCategoryId)
        assertEquals(now, state.occurredAt)
        assertTrue(state.entries.isEmpty())
    }

    @Test
    fun `启动时载入已有条目`() = runTest(dispatcher) {
        seed(cents = 800_000)
        seed(cents = 1250)

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.entries.size)
    }

    @Test
    fun `保存成功后清空金额与备注并刷新列表`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAmountChange("12.50")
        vm.onCategorySelected(CategoryId("food"))
        vm.onNoteChange("午饭")
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("", state.amountText)
        assertEquals("", state.noteText)
        assertNull(state.failure)
        assertEquals(1, state.entries.size)
        // 方向与分类**保留**：连续记同类账目时不该重新选一遍
        assertEquals(CategoryId("food"), state.selectedCategoryId)
    }

    @Test
    fun `金额为 0 时提示且不写入仓储`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAmountChange("0")
        vm.onCategorySelected(CategoryId("food"))
        vm.onSave()
        advanceUntilIdle()

        assertEquals(AmountInputError.NotPositive, vm.uiState.value.amountError)
        assertEquals(0, repository.addCallCount)
    }

    @Test
    fun `没选分类时被领域层拒绝且不写入仓储`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAmountChange("12.50")
        vm.onSave()
        advanceUntilIdle()

        val failure = vm.uiState.value.failure
        assertTrue(failure is SaveFailure.Rejected)
        assertEquals(LedgerError.CategoryRequired, (failure as SaveFailure.Rejected).error)
        assertEquals(0, repository.addCallCount)
    }

    @Test
    fun `备注超过 200 字时在界面层就被拦下`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAmountChange("12.50")
        vm.onCategorySelected(CategoryId("food"))
        vm.onNoteChange("字".repeat(201))
        vm.onSave()
        advanceUntilIdle()

        assertEquals(SaveFailure.NoteTooLong, vm.uiState.value.failure)
        assertEquals(0, repository.addCallCount)
    }

    @Test
    fun `存储失败时保留用户输入以便重试`() = runTest(dispatcher) {
        repository.addOutcome = Outcome.Err(com.jizhangbao.core.domain.DomainError.Technical.Storage)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAmountChange("12.50")
        vm.onCategorySelected(CategoryId("food"))
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(SaveFailure.Storage, state.failure)
        // 关键：金额**不能**被清掉 —— 否则用户填的东西白填了，还得重新输一遍
        assertEquals("12.50", state.amountText)
    }

    @Test
    fun `切换方向后丢弃已不适用的分类`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onCategorySelected(CategoryId("food")) // 餐饮是支出分类
        vm.onDirectionChange(EntryDirection.Income)

        // 不静默保留：否则用户会提交一个与方向矛盾的分类
        assertNull(vm.uiState.value.selectedCategoryId)
    }

    @Test
    fun `切到收入后可选分类里没有餐饮`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onDirectionChange(EntryDirection.Income)

        val ids = vm.uiState.value.selectableCategories.map { it.id.value }
        assertFalse(ids.contains("food"))
        assertTrue(ids.contains("salary"))
    }

    @Test
    fun `删除要先经过确认状态`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        advanceUntilIdle()
        val target = vm.uiState.value.entries.single()

        vm.onDeleteRequested(target)
        assertEquals(target.id, vm.uiState.value.pendingDelete?.id)
        // 还没确认，仓储不该被调用
        assertEquals(1, vm.uiState.value.entries.size)
    }

    @Test
    fun `取消删除时什么都不删`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onDeleteRequested(vm.uiState.value.entries.single())
        vm.onDeleteCancelled()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingDelete)
        assertEquals(1, vm.uiState.value.entries.size)
        assertEquals(1, repository.stored().size)
    }

    @Test
    fun `确认删除后条目消失并给出反馈`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onDeleteRequested(vm.uiState.value.entries.single())
        vm.onDeleteConfirmed()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.pendingDelete)
        assertTrue(state.deletedNotice)
        assertTrue(state.entries.isEmpty())
    }

    @Test
    fun `删除失败时保留确认状态以便重试`() = runTest(dispatcher) {
        seed()
        repository.removeOutcome = Outcome.Err(com.jizhangbao.core.domain.DomainError.Technical.Storage)
        val vm = viewModel()
        advanceUntilIdle()
        val target = vm.uiState.value.entries.single()

        vm.onDeleteRequested(target)
        vm.onDeleteConfirmed()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(SaveFailure.Storage, state.failure)
        // 留着待确认项：用户能再点一次，而不是以为删掉了
        assertEquals(target.id, state.pendingDelete?.id)
        assertFalse(state.deletedNotice)
    }

    @Test
    fun `账本数据变化时修订号递增_供组合根重算合计`() = runTest(dispatcher) {
        // 这是 REQ-002/AC-4 与 AC-5 在 Ledger 这一侧的**全部机制**：
        // 合计属于另一个上下文（Insight），按 R2 它不能订阅账本，
        // 于是由组合根观察这个修订号、在它变大时通知合计重算。
        // 修订号不递增 = 记了账合计不动，而那在界面上看起来就像"数据没保存"。
        seed(cents = 5_000) // 用不同的金额，好让后面那条 1250 是唯一的
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.entriesRevision)

        // 记一笔 → +1
        vm.onAmountChange("12.50")
        vm.onCategorySelected(CategoryId("food"))
        vm.onSave()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.entriesRevision)

        // 删一笔 → 再 +1（删除同样改变合计，不能只在保存时更新）
        vm.onDeleteRequested(vm.uiState.value.entries.single { it.amount.cents == 1250L })
        vm.onDeleteConfirmed()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.entriesRevision)
    }

    @Test
    fun `删除失败时修订号不变_不该通知合计重算`() = runTest(dispatcher) {
        seed()
        repository.removeOutcome = Outcome.Err(com.jizhangbao.core.domain.DomainError.Technical.Storage)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onDeleteRequested(vm.uiState.value.entries.single())
        vm.onDeleteConfirmed()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.entriesRevision)
    }
}
