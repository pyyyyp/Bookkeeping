package com.jizhangbao.ledger.data.repository

import com.jizhangbao.core.common.AppLogger
import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.ledger.data.local.CategoryDao
import com.jizhangbao.ledger.data.local.CategoryMapper
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.Category
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.repository.CategoryRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * [CategoryRepository] 的 Room 实现（`REQ-004`）。
 *
 * 异常翻译与 `LedgerEntryRepositoryImpl` 完全同构：技术异常在这里被翻成
 * [DomainError.Technical.Storage]，裸异常不得越过 `data` → `domain` 边界；
 * 而 [CancellationException] 必须原样抛出（协程取消不是存储失败）。
 *
 * 两处实现长得像，但**不抽公共基类**：它们各自演化（条目那边有查询与删除，
 * 分类这边只有存取），强行合并会得到一个泛型化到看不懂的基类。
 * 真到第三处重复时再说 —— 那时的抽象才有三个样本可依据。
 */
internal class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
    private val logger: AppLogger,
) : CategoryRepository {

    override suspend fun add(category: Category): Outcome<Unit> =
        storageOutcome { dao.insert(CategoryMapper.toEntity(category)) }

    override suspend fun update(category: Category): Outcome<Unit> {
        val updated = storageOutcome { dao.update(CategoryMapper.toEntity(category)) }

        return when (updated) {
            is Outcome.Err -> updated
            // 0 行 = 这个分类已经不在库里了（与条目那边同一套语义）
            is Outcome.Ok -> {
                if (updated.value == 0) Outcome.Err(LedgerError.CategoryNotFound) else Outcome.Ok(Unit)
            }
        }
    }

    override suspend fun all(): Outcome<List<Category>> =
        storageOutcome { dao.all().map(CategoryMapper::toDomain) }

    override suspend fun byId(id: CategoryId): Outcome<Category> {
        val found = storageOutcome { dao.byId(id.value) }

        return when (found) {
            is Outcome.Err -> found
            // 查不到 = 领域上的「没找到」，不是存储故障
            is Outcome.Ok -> found.value
                ?.let { Outcome.Ok(CategoryMapper.toDomain(it)) }
                ?: Outcome.Err(LedgerError.CategoryNotFound)
        }
    }

    /**
     * 与条目仓储同构：把技术异常翻成 [DomainError.Technical.Storage]，**并把原因记进日志**
     * （`REQ-006/AC-1`；不记 PII，见 `BR-2`）。
     */
    private suspend fun <T> storageOutcome(block: suspend () -> T): Outcome<T> =
        runCatching { block() }.fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { failure ->
                if (failure is CancellationException) throw failure
                logger.warn("分类的存储操作失败：本机数据库出错（技术细节见下）", failure)
                Outcome.Err(DomainError.Technical.Storage)
            },
        )
}
