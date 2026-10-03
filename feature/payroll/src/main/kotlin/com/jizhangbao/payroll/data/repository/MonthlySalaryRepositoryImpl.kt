package com.jizhangbao.payroll.data.repository

import com.jizhangbao.core.common.AppLogger
import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.payroll.data.local.MonthlySalaryDao
import com.jizhangbao.payroll.data.local.MonthlySalaryMapper
import com.jizhangbao.payroll.domain.MonthlySalary
import com.jizhangbao.payroll.domain.repository.MonthlySalaryRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * 月薪仓储的实现（`ADR-0007`）。
 *
 * 异常翻译与别的仓储**同一套**（`runCatching` + `fold`、`CancellationException` 原样抛出、
 * 日志只有固定文案）—— 见 `WorkSessionRepositoryImpl` 的 KDoc 里那三条理由。
 */
@Singleton
class MonthlySalaryRepositoryImpl @Inject constructor(
    private val dao: MonthlySalaryDao,
    private val logger: AppLogger,
) : MonthlySalaryRepository {

    override suspend fun save(salary: MonthlySalary): Outcome<Unit> =
        storageOutcome("保存月薪") {
            dao.insert(MonthlySalaryMapper.toEntity(salary, MonthlySalaryMapper.randomId()))
        }

    override suspend fun effectiveAt(date: LocalDate): Outcome<MonthlySalary?> =
        storageOutcome("读取月薪") {
            dao.effectiveAt(date.toEpochDay())?.let(MonthlySalaryMapper::toDomain)
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
