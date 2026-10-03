package com.jizhangbao.worklog.data.repository

import com.jizhangbao.core.common.AppLogger
import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.worklog.data.local.WorkSessionDao
import com.jizhangbao.worklog.data.local.WorkSessionMapper
import com.jizhangbao.worklog.domain.WorkSession
import com.jizhangbao.worklog.domain.WorkSessionId
import com.jizhangbao.worklog.domain.repository.WorkSessionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * 工时时段的仓储实现（`ADR-0007`：实现住上下文模块）。
 *
 * ## 日期区间 → 时刻区间，换算只在这一处
 *
 * 表里没有"归属日"这一列（它是推导出来的），所以这里把 `[from, toInclusive]` 换算成
 * 开始时刻的**半开区间** `[from 00:00, (to+1) 00:00)`，交给 DAO。
 * 于是"某段工时属于哪一天"这条规则**仍然只有领域那一处**（`startedAt` + 时区）。
 *
 * ## 异常翻译与账本那边**同一套**（`LedgerEntryRepositoryImpl.storageOutcome`）
 *
 * 三条都是踩过的：
 *
 * 1. 失败一律翻成 `Technical.Storage`，并把原因记下来（`REQ-006` 的教训：
 *    原因从没被记录过，排障只能靠猜）。日志里只有固定文案与异常，**不记 PII**（`ADR-0010`）。
 * 2. **用 `runCatching` 而不是 `try/catch`**：这里**就是要**兜住所有失败 ——
 *    数据层的职责正是把技术异常挡在领域边界之外。写成 `catch (e: Exception)`
 *    会被 detekt 的 `TooGenericExceptionCaught` 拦下，而那条规则在这里不适用
 *    （不是疏忽，是刻意的边界）。这条我原本写成了 `catch (Exception)`，被 detekt 拦下来了 ✓
 * 3. ⚠️ `CancellationException` **必须原样抛出**：协程取消**不是**存储失败。
 *    `runCatching` 默认会连它一起兜住，所以这一判必须显式写 —— 否则
 *    「用户离开界面」会被当成「保存失败」，还会破坏结构化并发。
 */
@Singleton
class WorkSessionRepositoryImpl @Inject constructor(
    private val dao: WorkSessionDao,
    private val logger: AppLogger,
    private val clock: Clock,
) : WorkSessionRepository {

    override suspend fun record(startedAt: Instant, endedAt: Instant): Outcome<WorkSessionId> =
        storageOutcome("写入一段工时") {
            val session = WorkSession.recorded(WorkSessionId.random(), startedAt, endedAt, clock.zone)
            dao.insert(WorkSessionMapper.toEntity(session))
            session.id
        }

    override suspend fun save(session: WorkSession): Outcome<Unit> =
        storageOutcome("更新一段工时") { dao.update(WorkSessionMapper.toEntity(session)) }

    override suspend fun sessionsIn(
        from: LocalDate,
        toInclusive: LocalDate,
    ): Outcome<List<WorkSession>> = storageOutcome("读取某几天的工时") {
        val zone = clock.zone
        val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        // 半开：上界排他，与内核的 TimeRange 一致（不重不漏）
        val toMillis = toInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        dao.startedBetween(fromMillis, toMillis).map { WorkSessionMapper.toDomain(it, zone) }
    }

    private suspend fun <T> storageOutcome(what: String, block: suspend () -> T): Outcome<T> =
        runCatching { block() }.fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { failure ->
                if (failure is CancellationException) throw failure
                logger.warn("$what 失败", failure)
                Outcome.Err(DomainError.Technical.Storage)
            },
        )
}
