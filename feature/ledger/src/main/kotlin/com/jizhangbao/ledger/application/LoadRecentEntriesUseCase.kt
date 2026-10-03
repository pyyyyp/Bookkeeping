package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.model.RecentEntries
import com.jizhangbao.ledger.domain.repository.LedgerEntryRepository
import javax.inject.Inject

/**
 * 读取最近的账目条目，供列表展示（`REQ-001/AC-7`）。
 *
 * 叫 `Load...` 而不是 `Observe...`：本卡不做反应式观察（见仓储接口的说明），
 * **名字必须说明它真的做了什么**——一个叫 `Observe` 却只查一次的方法，
 * 会让调用方以为界面会自动刷新。
 *
 * [DEFAULT_LIMIT] 是**展示层的取舍**（列表首屏显示多少条），不是业务规则：
 * 它不改变任何不变式，也不影响金额计算，所以放在用例而不是聚合里。
 */
class LoadRecentEntriesUseCase @Inject constructor(private val repository: LedgerEntryRepository) {

    suspend operator fun invoke(limit: Int = DEFAULT_LIMIT): Outcome<RecentEntries> =
        repository.recent(limit)

    companion object {
        /** 列表首屏条数。`REQ-001` 的非功能约束提到「≤ 200 条无明显卡顿」。 */
        const val DEFAULT_LIMIT = 200
    }
}
