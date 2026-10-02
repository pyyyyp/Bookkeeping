package com.jizhangbao.ledger.data.repository

import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.data.local.LedgerEntryDao
import com.jizhangbao.ledger.data.local.LedgerEntryMapper
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.LedgerEntryId
import com.jizhangbao.ledger.domain.repository.LedgerEntryRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * [LedgerEntryRepository] 的 Room 实现（R4：`data` 实现 `domain` 声明的接口）。
 *
 * ## 异常翻译是本类的核心职责
 *
 * 「裸异常不得跨越 `data` → `domain` 边界」——所以每个落库操作都包在
 * [storageOutcome] 里：技术异常在这里被翻成 [DomainError.Technical.Storage]，
 * 上层拿到的是 `Outcome`，不需要 try/catch。
 *
 * ⚠️ [CancellationException] **必须原样抛出**：它是协程取消的信号，
 * 不是存储失败。把它吞成 `Outcome.Err` 会让「用户离开界面」被当成「保存失败」，
 * 而且破坏结构化并发。
 */
internal class LedgerEntryRepositoryImpl @Inject constructor(
    private val dao: LedgerEntryDao,
) : LedgerEntryRepository {

    override suspend fun add(entry: LedgerEntry): Outcome<Unit> =
        storageOutcome { dao.insert(LedgerEntryMapper.toEntity(entry)) }

    override suspend fun remove(id: LedgerEntryId): Outcome<Unit> {
        val deleted = storageOutcome { dao.deleteById(id.value) }

        return when (deleted) {
            is Outcome.Err -> deleted
            is Outcome.Ok -> {
                // 受影响行数为 0 说明这条本来就不存在。它是**领域**上的「没找到」，
                // 不是存储故障，所以用 LedgerError 而不是 Technical.Storage。
                if (deleted.value == 0) Outcome.Err(LedgerError.EntryNotFound) else Outcome.Ok(Unit)
            }
        }
    }

    override suspend fun recent(limit: Int): Outcome<List<LedgerEntry>> =
        storageOutcome {
            dao.recent(limit).map(LedgerEntryMapper::toDomain)
        }

    /**
     * 把「任何存储失败」翻译成 [DomainError.Technical.Storage]。
     *
     * 用 `runCatching` 而不是 `try/catch`：这里**就是要**兜住所有失败
     * ——数据层的职责正是把技术异常挡在领域边界之外。
     * 写成 `catch (e: Exception)` 会被 detekt 的 `TooGenericExceptionCaught` 拦下，
     * 而那条规则在这里不适用（它不是疏忽，是刻意的边界）。
     *
     * ⚠️ 先判 [CancellationException] 并原样抛出：协程取消**不是**存储失败，
     * 把它吞成 `Outcome.Err` 会让「用户离开界面」被当成「保存失败」，
     * 还会破坏结构化并发。`runCatching` 默认会连它一起兜住，所以这一判必须显式写。
     *
     * ⚠️ **已知缺口**：异常原因目前**没有被记录**（`Technical.Storage` 不带载荷，
     * 项目也还没有日志抽象）。也就是说「磁盘满」与「数据库损坏」在上层看起来一样。
     * 这是可接受的短期状态，但**不是长期方案**：`core:common` 引入日志后，
     * 应在这里把原因记下来（不含 PII）。已记在 `T-007` 卡。
     */
    private suspend fun <T> storageOutcome(block: suspend () -> T): Outcome<T> =
        runCatching { block() }.fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { failure ->
                if (failure is CancellationException) throw failure
                Outcome.Err(DomainError.Technical.Storage)
            },
        )
}
