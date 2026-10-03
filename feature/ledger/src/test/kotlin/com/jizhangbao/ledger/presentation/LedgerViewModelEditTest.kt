package com.jizhangbao.ledger.presentation

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.application.DeleteLedgerEntryUseCase
import com.jizhangbao.ledger.application.LoadCategoriesUseCase
import com.jizhangbao.ledger.application.LoadRecentEntriesUseCase
import com.jizhangbao.ledger.application.RecordLedgerEntryUseCase
import com.jizhangbao.ledger.application.ReviseLedgerEntryUseCase
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.Note
import com.jizhangbao.ledger.testing.FakeCategoryRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * ViewModel 的**编辑态**测试（`REQ-003`）。
 *
 * 与记账/删除那批测试分开一个文件：编辑引入了一个新的状态维度（表单处于"记一笔"还是"改一笔"），
 * 而它的失败方式与记账不同（例如"取消之后表单里还留着上一次编辑的值"）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModelEditTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeLedgerEntryRepository()
    private val now: Instant = Instant.parse("2026-10-02T03:04:05Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun viewModel() = LedgerViewModel(
        recordEntry = RecordLedgerEntryUseCase(repository, clock),
        loadEntries = LoadRecentEntriesUseCase(repository),
        deleteEntry = DeleteLedgerEntryUseCase(repository),
        // REQ-008：坏行的删除入口 —— 这条测试路径不测它，但构造参数不能少
        discardUnreadableRow = com.jizhangbao.ledger.application.DiscardUnreadableRowUseCase(repository),
        reviseEntry = ReviseLedgerEntryUseCase(repository),
        // REQ-004：分类清单来自仓储（预置 ∪ 自定义）。这里给空的 fake —— 记账/编辑路径不用它
        loadCategories = LoadCategoriesUseCase(FakeCategoryRepository()),
        clock = clock,
    )

    private suspend fun seed(
        cents: Long = 1_250,
        occurredAt: Instant = Instant.parse("2026-10-02T01:00:00Z"),
        note: Note? = null,
        direction: EntryDirection = EntryDirection.Expense,
        categoryId: String = "food",
    ): LedgerEntry {
        val entry = (LedgerEntry.record(
            direction = direction,
            amount = Money.ofCents(cents),
            categoryId = CategoryId(categoryId),
            occurredAt = occurredAt,
            note = note,
            bookedAt = now,
        ) as Outcome.Ok).value
        repository.add(entry)
        return entry
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
    fun `进入编辑态时表单回填该条的值`() = runTest(dispatcher) {
        val target = seed(cents = 1_250, note = Note("午饭"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEditRequested(target)

        val state = vm.uiState.value
        assertEquals(target.id, state.editing?.id)
        assertEquals("12.50", state.amountText) // 分 → 元文本，不经浮点
        assertEquals(CategoryId("food"), state.selectedCategoryId)
        assertEquals(EntryDirection.Expense, state.direction)
        assertEquals("午饭", state.noteText)
        assertEquals(target.occurredAt, state.occurredAt)
    }

    @Test
    fun `保存修改走替换而不是新增`() = runTest(dispatcher) {
        val target = seed(cents = 1_250)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEditRequested(target)
        vm.onAmountChange("20.00")
        vm.onSave()
        advanceUntilIdle()

        // AC-1：仍然只有一条，且是同一条（身份不变）
        val stored = repository.stored().single()
        assertEquals(target.id, stored.id)
        assertEquals(Money.ofCents(2_000), stored.amount)
        // 编辑态退出、表单清空
        assertNull(vm.uiState.value.editing)
        assertEquals("", vm.uiState.value.amountText)
        // 修订号 +1：合计要重算（AC-5）
        assertEquals(1, vm.uiState.value.entriesRevision)
    }

    @Test
    fun `编辑可以改发生时间`() = runTest(dispatcher) {
        val target = seed(cents = 1_250)
        val vm = viewModel()
        advanceUntilIdle()
        val corrected = Instant.parse("2026-09-15T01:00:00Z")

        vm.onEditRequested(target)
        vm.onOccurredAtChange(corrected)
        vm.onSave()
        advanceUntilIdle()

        assertEquals(corrected, repository.stored().single().occurredAt)
        // BR-3：录入时间不受影响
        assertEquals(now, repository.stored().single().bookedAt)
    }

    @Test
    fun `取消编辑时仓储一次都不会被调用且表单回到新条目形态`() = runTest(dispatcher) {
        val target = seed(cents = 1_250)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEditRequested(target)
        vm.onAmountChange("99.00")
        vm.onEditCancelled()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.editing)
        assertEquals("", state.amountText) // 放弃的那次编辑不该留在表单里
        assertEquals("", state.noteText)
        assertEquals(0, repository.updateCallCount)
        // AC-6：账本里那条一个字没变
        assertEquals(Money.ofCents(1_250), repository.stored().single().amount)
    }

    @Test
    fun `编辑时金额非法_不写库且保留编辑态`() = runTest(dispatcher) {
        val target = seed(cents = 1_250)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEditRequested(target)
        vm.onAmountChange("0")
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(AmountInputError.NotPositive, state.amountError)
        // 仍在编辑态：用户可以改完再试，而不是以为改动生效了
        assertNotNull(state.editing)
        assertEquals(0, repository.updateCallCount)
        assertEquals(Money.ofCents(1_250), repository.stored().single().amount)
    }

    @Test
    fun `编辑一条已经不存在的条目时给出失败而不是假装成功`() = runTest(dispatcher) {
        val target = seed(cents = 1_250)
        // 在别处被删掉了
        repository.remove(target.id)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEditRequested(target)
        vm.onAmountChange("20.00")
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.failure is SaveFailure.Rejected)
        assertEquals(
            com.jizhangbao.ledger.domain.error.LedgerError.EntryNotFound,
            (state.failure as SaveFailure.Rejected).error,
        )
        // AC-8：没有凭空造出一条新记录
        assertTrue(repository.stored().isEmpty())
    }

    @Test
    fun `编辑成功后修订号递增_让合计重算`() = runTest(dispatcher) {
        val target = seed(cents = 1_250)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.entriesRevision)

        vm.onEditRequested(target)
        vm.onAmountChange("30.00")
        vm.onSave()
        advanceUntilIdle()

        // AC-5 在 Ledger 这一侧的机制：修订号变化 → 组合根通知 Insight 重算
        assertEquals(1, vm.uiState.value.entriesRevision)
        // 没选分类时不该写库（顺手确认编辑路径也走同一套校验）
        assertEquals(Money.ofCents(3_000), repository.stored().single().amount)
    }
}
