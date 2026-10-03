package com.jizhangbao.worklog.data.repository

import com.jizhangbao.core.common.AppLogger
import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.worklog.data.local.WorkplaceDao
import com.jizhangbao.worklog.data.local.WorkplaceMapper
import com.jizhangbao.worklog.domain.Workplace
import com.jizhangbao.worklog.domain.WorkplaceId
import com.jizhangbao.worklog.domain.repository.WorkplaceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * 工作地点的仓储实现（`ADR-0007`）。
 *
 * 异常翻译与 `WorkSessionRepositoryImpl` **同一套**（`runCatching` + `fold`、
 * `CancellationException` 原样抛出、日志只有固定文案 —— 见那边的 KDoc）。
 * 没有把它抽成公共基类：两处代码各十行，而一个"通用仓储基类"会
 * 立刻变成所有上下文都往里塞东西的地方（那种类最后都叫 `BaseRepository`）。
 */
@Singleton
class WorkplaceRepositoryImpl @Inject constructor(
    private val dao: WorkplaceDao,
    private val logger: AppLogger,
) : WorkplaceRepository {

    override suspend fun save(workplace: Workplace): Outcome<Unit> =
        storageOutcome("保存工作地点") { dao.upsert(WorkplaceMapper.toEntity(workplace)) }

    override suspend fun all(): Outcome<List<Workplace>> =
        storageOutcome("读取工作地点") { dao.all().map(WorkplaceMapper::toDomain) }

    override suspend fun remove(id: WorkplaceId): Outcome<Unit> =
        storageOutcome("删除工作地点") { dao.deleteById(id.value) }

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
