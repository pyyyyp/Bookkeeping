package com.jizhangbao.ledger.testing

import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.TimeRange
import com.jizhangbao.ledger.domain.error.LedgerError
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import com.jizhangbao.ledger.domain.model.LedgerEntryId
import com.jizhangbao.ledger.domain.model.RecentEntries
import com.jizhangbao.ledger.domain.repository.LedgerEntryRepository

/**
 * 内存版仓储，供用例测试使用。
 *
 * 刻意**不做排序**：排序是 SQL 的职责（`ORDER BY occurredAtEpochMilli DESC`），
 * 让 fake 也排一遍等于在测试里重新实现一遍实现，测不出真问题。
 * 这里只回答两件事：**方法被调用了吗**，以及**调用结果由测试指定时返回什么**。
 *
 * > 本文件在 `src/test` 下，因此不在架构断言的扫描范围里（它只扫生产源集）。
 * > 这也解释了为什么 Fake 不能放 `:core:testing`：那个模块属于 `core:*`，
 * > 一旦要引用 Ledger 的领域类型就违反 R7（见 `ADR-0007` 的同类推理）。
 */
internal class FakeLedgerEntryRepository : LedgerEntryRepository {

    private val entries = mutableListOf<LedgerEntry>()

    /** 由测试指定 `add` 的结果（默认成功）。 */
    var addOutcome: Outcome<Unit> = Outcome.Ok(Unit)

    /** 非 null 时 `recent` 直接返回它，用于制造失败场景。 */
    var recentOutcome: Outcome<RecentEntries>? = null

    /** 非 null 时 `discardUnreadableRow` 直接返回它，用于制造失败场景（`REQ-008`）。 */
    var discardOutcome: Outcome<Unit>? = null

    /** 被要求删掉的坏行原始标识（按调用顺序）。 */
    val discarded = mutableListOf<String>()

    override suspend fun discardUnreadableRow(rawId: String): Outcome<Unit> {
        discardOutcome?.let { return it }
        discarded += rawId
        return Outcome.Ok(Unit)
    }

    /** 非 null 时 `remove` 直接返回它，用于制造失败场景。 */
    var removeOutcome: Outcome<Unit>? = null

    /** 非 null 时 `update` 直接返回它，用于制造失败场景。 */
    var updateOutcome: Outcome<Unit>? = null

    var addCallCount = 0
        private set

    var updateCallCount = 0
        private set

    override suspend fun add(entry: LedgerEntry): Outcome<Unit> {
        addCallCount++
        if (addOutcome is Outcome.Ok) entries += entry
        return addOutcome
    }

    override suspend fun update(entry: LedgerEntry): Outcome<Unit> {
        updateCallCount++
        val forced = updateOutcome
        // 用 when + 单个 return：detekt 的 ReturnCount 上限是 2，而这里本来有 3 条路径
        return when {
            forced != null -> forced
            else -> {
                val index = entries.indexOfFirst { it.id == entry.id }
                // 语义与真实现一致：不存在的条目**不插入**，返回"没找到"
                if (index < 0) {
                    Outcome.Err(LedgerError.EntryNotFound)
                } else {
                    entries[index] = entry
                    Outcome.Ok(Unit)
                }
            }
        }
    }

    override suspend fun remove(id: LedgerEntryId): Outcome<Unit> {
        removeOutcome?.let { return it }
        val removed = entries.removeAll { it.id == id }
        return if (removed) Outcome.Ok(Unit) else Outcome.Err(LedgerError.EntryNotFound)
    }

    override suspend fun recent(limit: Int): Outcome<RecentEntries> =
        recentOutcome ?: Outcome.Ok(RecentEntries.of(entries.take(limit)))

    /** 非 null 时 `inCategory` 直接返回它，用于制造失败场景。 */
    var inCategoryOutcome: Outcome<RecentEntries>? = null

    /** 最近一次 `inCategory` 收到的（分类、方向、区间）—— 用来钉住"口径与占比同源"（`REQ-007/BR-1`）。 */
    var lastInCategory: Triple<CategoryId, EntryDirection, TimeRange>? = null

    /** 最近一次 `inCategory` 收到的条数上限。 */
    var lastInCategoryLimit: Int? = null

    override suspend fun inCategory(
        categoryId: CategoryId,
        direction: EntryDirection,
        range: TimeRange,
        limit: Int,
    ): Outcome<RecentEntries> {
        lastInCategory = Triple(categoryId, direction, range)
        lastInCategoryLimit = limit
        return inCategoryOutcome ?: Outcome.Ok(
            RecentEntries.of(
                entries
                    .filter { it.categoryId == categoryId && it.direction == direction }
                    // 半开区间，与 SQL 的口径一致（>= start 且 < end）
                    .filter {
                        !it.occurredAt.isBefore(range.start) && it.occurredAt.isBefore(range.end)
                    }
                    .take(limit),
            ),
        )
    }

    /** 已经真的存进去的条目（`add` 失败时不会出现在这里）。 */
    fun stored(): List<LedgerEntry> = entries.toList()
}
