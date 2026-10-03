package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.toTimeRange
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.RecentEntries
import com.jizhangbao.ledger.domain.model.UnreadableRow
import com.jizhangbao.ledger.testing.FakeLedgerEntryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

/**
 * 分类下钻的用例（`REQ-007`）。
 *
 * 最要紧的一条是 `口径与占比同源`：它断言用例拿到的区间**就是**内核
 * `YearMonth.toTimeRange` 的结果（`BR-1`）。这条断言的价值在于
 * **它会在有人"顺手在这儿算一下月初"时失败** —— 那正是两个数字开始不一致的时刻。
 */
class LoadCategoryEntriesUseCaseTest {

    private val repository = FakeLedgerEntryRepository()
    private val useCase = LoadCategoryEntriesUseCase(repository)
    private val zone = ZoneId.of("Asia/Shanghai")
    private val month = YearMonth.of(2026, 10)

    @Test
    fun `按分类与区间读取_方向默认是支出`() = runBlocking {
        repository.inCategoryOutcome = Outcome.Ok(RecentEntries.of(emptyList()))

        useCase(categoryId = CategoryId("food"), range = month.toTimeRange(zone))

        val called = repository.lastInCategory
        assertEquals(CategoryId("food"), called?.first)
        // 占比只统计支出（REQ-005/AC-8），所以从占比下钻进来默认也是支出
        assertEquals(EntryDirection.Expense, called?.second)
    }

    @Test
    fun `区间与合计占比同源_用的就是内核的月份口径`() = runBlocking {
        repository.inCategoryOutcome = Outcome.Ok(RecentEntries.of(emptyList()))

        useCase(categoryId = CategoryId("food"), range = month.toTimeRange(zone))

        // BR-1：同一个函数算出来的区间。不一致就意味着"占比说 40000、清单加起来不是"
        assertEquals(month.toTimeRange(zone), repository.lastInCategory?.third)
    }

    @Test
    fun `坏行计数会一路传出来`() = runBlocking {
        // REQ-006/AC-3 的处理在下钻清单里同样生效，不能被用例吞掉
        repository.inCategoryOutcome = Outcome.Ok(
            RecentEntries(
                entries = emptyList(),
                unreadableRows = listOf(
                    UnreadableRow(rawId = "bad-1", reason = "标识不是合法 UUID"),
                    UnreadableRow(rawId = "bad-2", reason = "金额为负"),
                ),
            ),
        )

        val result = useCase(categoryId = CategoryId("food"), range = month.toTimeRange(zone))

        assertEquals(2, (result as Outcome.Ok).value.unreadable)
    }

    @Test
    fun `读失败时返回错误而不是空清单`() = runBlocking {
        repository.inCategoryOutcome = Outcome.Err(DomainError.Technical.Storage)

        val result = useCase(categoryId = CategoryId("food"), range = month.toTimeRange(zone))

        // 「读不出来」与「这个月这一类没有支出」是两件事（AC-3 的空态不能掩盖失败）
        assertTrue(result is Outcome.Err)
    }

    @Test
    fun `默认条数与主列表一致`() = runBlocking {
        repository.inCategoryOutcome = Outcome.Ok(RecentEntries.of(emptyList()))

        useCase(categoryId = CategoryId("food"), range = month.toTimeRange(zone))

        assertEquals(LoadRecentEntriesUseCase.DEFAULT_LIMIT, repository.lastInCategoryLimit)
    }
}
