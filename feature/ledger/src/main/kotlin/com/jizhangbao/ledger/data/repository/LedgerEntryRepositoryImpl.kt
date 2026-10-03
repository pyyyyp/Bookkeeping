package com.jizhangbao.ledger.data.repository

import com.jizhangbao.core.common.AppLogger
import com.jizhangbao.core.domain.DomainError
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.TimeRange
import com.jizhangbao.ledger.data.local.LedgerEntryDao
import com.jizhangbao.ledger.data.local.LedgerEntryEntity
import com.jizhangbao.ledger.data.local.LedgerEntryMapper
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.LedgerEntryId
import com.jizhangbao.ledger.domain.model.RecentEntries
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
 * ## `recent` 为什么**逐行**映射（`REQ-006/AC-3`）
 *
 * 库里可能有读不出来的行（被外部工具改坏、迁移写错）。整批 `map { restore(...) }`
 * 时，**一处抛错就会让整张列表消失** —— `T-013` 冒烟时真实发生过：
 * 一条 id 不是 UUID 的脏数据让界面显示"还没有记账"，而库里 5 条一条不少。
 *
 * 现在逐行映射：坏行跳过、计数、记一条 warn，其余照常显示。
 * **不静默跳过** —— 计数会一路传到界面（[RecentEntries.unreadable]）。
 *
 * ⚠️ [CancellationException] **必须原样抛出**：它是协程取消的信号，
 * 不是存储失败。把它吞成 `Outcome.Err` 会让「用户离开界面」被当成「保存失败」，
 * 而且破坏结构化并发。
 */
internal class LedgerEntryRepositoryImpl @Inject constructor(
    private val dao: LedgerEntryDao,
    private val logger: AppLogger,
) : LedgerEntryRepository {

    override suspend fun add(entry: LedgerEntry): Outcome<Unit> =
        storageOutcome("写入一条账目") { dao.insert(LedgerEntryMapper.toEntity(entry)) }

    override suspend fun update(entry: LedgerEntry): Outcome<Unit> {
        val updated = storageOutcome("替换一条账目") { dao.update(LedgerEntryMapper.toEntity(entry)) }

        return when (updated) {
            is Outcome.Err -> updated
            // 与 remove 同一套语义：0 行 = 这条已经不在账本里了（BR-5）。
            // 注意这里**不能**退化成 insert：那会让一条已被删除的条目复活
            is Outcome.Ok -> {
                if (updated.value == 0) Outcome.Err(LedgerError.EntryNotFound) else Outcome.Ok(Unit)
            }
        }
    }

    override suspend fun remove(id: LedgerEntryId): Outcome<Unit> {
        val deleted = storageOutcome("删除一条账目") { dao.deleteById(id.value) }

        return when (deleted) {
            is Outcome.Err -> deleted
            is Outcome.Ok -> {
                // 受影响行数为 0 说明这条本来就不存在。它是**领域**上的「没找到」，
                // 不是存储故障，所以用 LedgerError 而不是 Technical.Storage。
                if (deleted.value == 0) Outcome.Err(LedgerError.EntryNotFound) else Outcome.Ok(Unit)
            }
        }
    }

    override suspend fun recent(limit: Int): Outcome<RecentEntries> {
        val rows = storageOutcome("读取最近的账目") { dao.recent(limit) }
        if (rows is Outcome.Err) return Outcome.Err(rows.error)

        return Outcome.Ok((rows as Outcome.Ok).value.toRecentEntries())
    }

    override suspend fun inCategory(
        categoryId: CategoryId,
        direction: EntryDirection,
        range: TimeRange,
        limit: Int,
    ): Outcome<RecentEntries> {
        // 口径与合计/占比同源（REQ-007/BR-1）：同一个方向、同一个半开区间、同一个归属字段
        val rows = storageOutcome("读取某个分类的账目") {
            dao.inCategory(
                categoryId = categoryId.value,
                direction = direction.name,
                fromEpochMilli = range.start.toEpochMilli(),
                toEpochMilli = range.end.toEpochMilli(),
                limit = limit,
            )
        }

        if (rows is Outcome.Err) return Outcome.Err(rows.error)
        return Outcome.Ok((rows as Outcome.Ok).value.toRecentEntries())
    }

    /**
     * 逐行还原成领域条目，**坏行跳过、计数、记一条 warn**（`REQ-006/AC-3`）。
     *
     * 从 [recent] 与 [inCategory] 里提出来：两处的处理**必须一模一样** ——
     * 一条坏数据在下钻清单里藏起整张清单，和在主列表里藏起整张列表是同一个故障。
     * 这种"两处必须一致"的逻辑不能复制粘贴（复制的那份迟早漏改）。
     */
    private fun List<LedgerEntryEntity>.toRecentEntries(): RecentEntries {
        val failures = mutableListOf<Throwable>()
        val entries = mapNotNull { row ->
            runCatching { LedgerEntryMapper.toDomain(row) }
                .onFailure(failures::add)
                .getOrNull()
        }

        if (failures.isNotEmpty()) {
            // BR-2：只记条数与第一个异常的原因，**不记**金额/备注/分类名
            logger.warn(
                "有 ${failures.size} 条账目读不出来（数据可能被外部改坏），已跳过",
                failures.first(),
            )
        }

        return RecentEntries(entries = entries, unreadable = failures.size)
    }

    /**
     * 把「任何存储失败」翻译成 [DomainError.Technical.Storage]，**并把原因记进日志**。
     *
     * 用 `runCatching` 而不是 `try/catch`：这里**就是要**兜住所有失败
     * ——数据层的职责正是把技术异常挡在领域边界之外。
     * 写成 `catch (e: Exception)` 会被 detekt 的 `TooGenericExceptionCaught` 拦下，
     * 而那条规则在这里不适用（它不是疏忽，是刻意的边界）。
     *
     * `what` 是**固定文案**（"写入一条账目"这类），它不带用户数据 —— 见 `REQ-006/BR-2`。
     * 这个缺口（"磁盘满"与"数据库损坏"在上层看起来一样）从 `T-007` 挂到 `T-015` 才补上。
     *
     * ⚠️ 先判 [CancellationException] 并原样抛出：协程取消**不是**存储失败，
     * 把它吞成 `Outcome.Err` 会让「用户离开界面」被当成「保存失败」，
     * 还会破坏结构化并发。`runCatching` 默认会连它一起兜住，所以这一判必须显式写。
     */
    private suspend fun <T> storageOutcome(what: String, block: suspend () -> T): Outcome<T> =
        runCatching { block() }.fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { failure ->
                if (failure is CancellationException) throw failure
                logger.warn("$what 失败：本机数据库出错（技术细节见下）", failure)
                Outcome.Err(DomainError.Technical.Storage)
            },
        )
}
