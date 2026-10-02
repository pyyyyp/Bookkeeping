package com.jizhangbao.insight.application

import com.jizhangbao.core.domain.CategoryBreakdown
import com.jizhangbao.core.domain.LedgerTotalsReader
import com.jizhangbao.core.domain.Outcome
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/**
 * 取某个月**支出按分类的占比**（`REQ-005`）。
 *
 * 与 [LoadMonthlyTotalsUseCase] 是同一套换算、同一个端口，只是换了方法：
 * 这样"两个数字同屏、口径必须一致"（`BR-5`）在实现上就是**同一段代码**，
 * 而不是两处各写一遍再靠人去对齐。
 *
 * 界面上的顺序、占比的四舍五入、空清单规则都不在这里 ——
 * 它们住在内核的 `CategoryBreakdown`（见 `docs/20-domain/insight-model.md`）。
 */
class LoadCategoryShareUseCase @Inject constructor(
    private val reader: LedgerTotalsReader,
    private val clock: Clock,
) {

    suspend operator fun invoke(month: YearMonth = YearMonth.now(clock)): Outcome<CategoryBreakdown> =
        // toTimeRange 与合计共用（internal，同模块），所以两者的区间必然一致
        reader.expensesByCategory(month.toTimeRange(clock.zone))
}
