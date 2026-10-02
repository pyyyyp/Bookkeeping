package com.jizhangbao.ledger.application

import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.domain.model.LedgerEntryId
import com.jizhangbao.ledger.domain.repository.LedgerEntryRepository
import javax.inject.Inject

/**
 * 删除一条账目条目（`REQ-001/AC-8` `AC-9`）。
 *
 * ## 它现在只是转发，为什么还要一个用例类
 *
 * 这不是「为了分层而分层」，有两个具体理由：
 *
 * 1. **界面不该直接拿仓储**。R5 要求 presentation 只依赖抽象，而用例是那个抽象里
 *    最贴近用户意图的一层——界面说「删掉这条」，不是「从这个仓储里 remove 这个 id」。
 * 2. **这里是将来加规则的地方**。删除目前无条件，但很可能出现
 *    「已入工资单的条目不能删」「只允许删最近 N 天」这类规则——
 *    它们属于应用层编排，不该让界面或仓储承担。**现在不预设**，但留下位置。
 *
 * ## 删除是物理删除
 *
 * 见 `ADR-0005`：不写软删除字段、不留墓碑。条目不存在时仓储返回
 * `LedgerError.EntryNotFound`，本用例**原样上抛**——那是真实结果，不该假装成功。
 */
class DeleteLedgerEntryUseCase @Inject constructor(
    private val repository: LedgerEntryRepository,
) {

    suspend operator fun invoke(id: LedgerEntryId): Outcome<Unit> = repository.remove(id)
}
