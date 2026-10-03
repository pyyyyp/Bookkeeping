package com.jizhangbao.ledger.data.totals

import com.jizhangbao.core.common.AppLogger
import com.jizhangbao.core.domain.CategoryAmount
import com.jizhangbao.core.domain.CategoryBreakdown
import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.LedgerTotalsReader
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.MonthlyTotals
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.TimeRange
import com.jizhangbao.ledger.data.local.LedgerEntryDao
import com.jizhangbao.ledger.domain.model.CategoryCatalog
import com.jizhangbao.ledger.domain.repository.CategoryRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * `LedgerTotalsReader` 的实现 —— 生产方在 Ledger 这边（`ADR-0008`）。
 *
 * 为什么实现在这里而不是在 Insight：`ledger_entry` 这张表属于 Ledger，
 * 别的上下文不该知道它的列名（那正是 `ADR-0008` 否掉"直接读同一张表"的理由）。
 * 消费方 `:feature:insight` 只依赖内核里的端口接口。
 *
 * 合计用两次查询（收入、支出）而不是一次分组查询：SQL 更直白，而且方向只有两个。
 * 分类占比用一次 `GROUP BY` —— 那个分组本来就是结果本身。
 */
internal class LedgerTotalsReaderImpl @Inject constructor(
    private val dao: LedgerEntryDao,
    private val categoryRepository: CategoryRepository,
    private val logger: AppLogger,
) : LedgerTotalsReader {

    override suspend fun totalsIn(range: TimeRange): Outcome<MonthlyTotals> {
        val from = range.start.toEpochMilli()
        val to = range.end.toEpochMilli()

        val computed = runCatching {
            MonthlyTotals(
                income = Money.ofCents(dao.sumAmountCents(EntryDirection.Income.name, from, to)),
                expense = Money.ofCents(dao.sumAmountCents(EntryDirection.Expense.name, from, to)),
            )
        }

        return computed.fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { failure ->
                // 协程取消不是存储失败：必须原样抛出，否则取消会被当成绩效问题悄悄吞掉
                if (failure is CancellationException) throw failure
                // 与仓储实现一致：异常翻译成领域错误，不让它跨层；原因记进日志（REQ-006/AC-1）
                logger.warn("读取本月合计失败：本机数据库出错（技术细节见下）", failure)
                Outcome.Err(DomainError.Technical.Storage)
            },
        )
    }

    override suspend fun expensesByCategory(range: TimeRange): Outcome<CategoryBreakdown> {
        // 先把分类读出来：读不到就没法给出"给人看的名字"，
        // 而按 BR-7 名字必须由 Ledger 解析（Insight 不拥有分类语义）
        val custom = categoryRepository.all()
        if (custom is Outcome.Err) return Outcome.Err(custom.error)

        val names = CategoryCatalog.PRESET
            .mergedWith((custom as Outcome.Ok).value)
            .associate { it.id.value to it.displayName }

        val from = range.start.toEpochMilli()
        val to = range.end.toEpochMilli()

        val computed = runCatching {
            val sums = dao.sumByCategory(EntryDirection.Expense.name, from, to)
            val rows = sums.map { sum ->
                CategoryAmount(
                    // 查不到名字时退回标识：至少能看出是哪一条（与界面上的处理一致）
                    categoryName = names[sum.categoryId] ?: sum.categoryId,
                    amount = Money.ofCents(sum.amountCents),
                )
            }
            // 排序与"合计为 0 就空清单"都在 CategoryBreakdown.of 里（BR-4 / BR-6）
            CategoryBreakdown.of(rows, Money.ofCents(sums.sumOf { it.amountCents }))
        }

        return computed.fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { failure ->
                if (failure is CancellationException) throw failure
                logger.warn("读取支出构成失败：本机数据库出错（技术细节见下）", failure)
                Outcome.Err(DomainError.Technical.Storage)
            },
        )
    }
}
