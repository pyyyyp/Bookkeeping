package com.jizhangbao.ledger.presentation

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.ledger.application.ArchiveCategoryUseCase
import com.jizhangbao.ledger.application.CreateCategoryUseCase
import com.jizhangbao.ledger.application.LoadCategoriesUseCase
import com.jizhangbao.ledger.application.RenameCategoryUseCase
import com.jizhangbao.ledger.application.RestoreCategoryUseCase
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryCatalog
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.testing.FakeCategoryRepository
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

/**
 * 分类管理状态持有者的测试（`REQ-004`）。
 *
 * 它没有业务规则（名字合法性在聚合、重名在用例），但有一件事值得测：
 * **失败之后界面看到的是不是"什么都没发生"** —— 也就是"不乐观更新"这条做法
 * 是否真的成立（失败时不刷新，所以清单还是旧的）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryManagerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCategoryRepository()

    private fun viewModel() = CategoryManagerViewModel(
        loadCategories = LoadCategoriesUseCase(repository),
        createCategory = CreateCategoryUseCase(repository),
        renameCategory = RenameCategoryUseCase(repository),
        archiveCategory = ArchiveCategoryUseCase(repository),
        restoreCategory = RestoreCategoryUseCase(repository),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun seed(
        id: String = "custom-pet",
        name: String = "宠物",
        archived: Boolean = false,
    ): Category {
        val category = Category.restore(CategoryId(id), name, setOf(EntryDirection.Expense), archived)
        repository.add(category)
        return category
    }

    @Test
    fun `初始清单里既有内置分类也有自定义分类`() = runTest(dispatcher) {
        seed()

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(CategoryCatalog.PRESET.all().size, state.presetCategories.size)
        assertEquals(listOf("宠物"), state.customCategories.map { it.displayName })
    }

    @Test
    fun `新建成功后清空输入框_并把新分类放进清单`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onNewNameChange("话费")
        vm.onCreate()
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.newName)
        assertNull(vm.uiState.value.failure)
        assertTrue(vm.uiState.value.customCategories.any { it.displayName == "话费" })
    }

    @Test
    fun `重名时给出失败_且清单里没有多出东西`() = runTest(dispatcher) {
        seed(name = "宠物")

        val vm = viewModel()
        advanceUntilIdle()
        val before = vm.uiState.value.customCategories.size

        vm.onNewNameChange("宠物")
        vm.onCreate()
        advanceUntilIdle()

        assertEquals(CategoryFailure.NameTaken, vm.uiState.value.failure)
        assertEquals(before, vm.uiState.value.customCategories.size)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun `名字非法时给出失败`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onNewNameChange("   ")
        vm.onCreate()
        advanceUntilIdle()

        assertEquals(CategoryFailure.NameInvalid, vm.uiState.value.failure)
    }

    @Test
    fun `改名的两步：进入改名态回填旧名，确认后清单更新`() = runTest(dispatcher) {
        val target = seed(name = "宠物")

        val vm = viewModel()
        advanceUntilIdle()

        vm.onRenameStart(target)
        assertEquals("宠物", vm.uiState.value.renameText)
        assertEquals(target.id, vm.uiState.value.renamingId)

        vm.onRenameTextChange("猫主子")
        vm.onRenameConfirm()
        advanceUntilIdle()

        assertNull(vm.uiState.value.renamingId)
        assertEquals(listOf("猫主子"), vm.uiState.value.customCategories.map { it.displayName })
    }

    @Test
    fun `取消改名不动数据`() = runTest(dispatcher) {
        val target = seed(name = "宠物")

        val vm = viewModel()
        advanceUntilIdle()

        vm.onRenameStart(target)
        vm.onRenameTextChange("猫主子")
        vm.onRenameCancel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.renamingId)
        assertEquals(listOf("宠物"), vm.uiState.value.customCategories.map { it.displayName })
    }

    @Test
    fun `归档与恢复`() = runTest(dispatcher) {
        val target = seed()

        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.customCategories.single().archived)

        vm.onArchive(target)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.customCategories.single().archived)

        vm.onRestore(target)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.customCategories.single().archived)
    }

    @Test
    fun `方向至少要留一个_全部取消时保持原样`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        // 初始只有「支出」
        vm.onNewDirectionToggled(EntryDirection.Expense)

        assertEquals(setOf(EntryDirection.Expense), vm.uiState.value.newDirections)

        vm.onNewDirectionToggled(EntryDirection.Income)
        assertEquals(
            setOf(EntryDirection.Expense, EntryDirection.Income),
            vm.uiState.value.newDirections,
        )
    }

    @Test
    fun `名字为空时不允许新建`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canCreate)

        vm.onNewNameChange("话费")
        assertTrue(vm.uiState.value.canCreate)
    }
}
