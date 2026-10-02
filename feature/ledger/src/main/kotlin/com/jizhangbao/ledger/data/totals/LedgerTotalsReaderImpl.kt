package com.jizhangbao.ledger.data.totals

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.LedgerTotalsReader
import com.jizhangbao.core.domain.Money
import com.jizhangbao.core.domain.MonthlyTotals
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.TimeRange
import com.jizhangbao.ledger.data.local.LedgerEntryDao
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * `LedgerTotalsReader` 的实现 —— 生产方在 Ledger 这边（`ADR-0008`）。
 *
 * 为什么实现在这里而不是在 Insight：`ledger_entry` 这张表属于 Ledger，
 * 别的上下文不该知道它的列名（那正是 `ADR-0008` 否掉"直接读同一张表"的理由）。
 * 消费方 `:feature:insight` 只依赖内核里的端口接口。
 *
 * 两次查询（收入、支出）而不是一次分组查询：SQL 更直白，而且方向只有两个，
 * 多一次 round-trip 在这个量级上不值得为它把代码写复杂。
 */
internal class LedgerTotalsReaderImpl @Inject constructor(
    private val dao: LedgerEntryDao,
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
                // 与仓储实现一致：异常翻译成领域错误，不让它跨层（也不打印原因，见 T-007 的已知缺口）
                Outcome.Err(DomainError.Technical.Storage)
            },
        )
    }
}
