package com.jizhangbao.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jizhangbao.ledger.data.local.CategoryDao
import com.jizhangbao.ledger.data.local.CategoryEntity
import com.jizhangbao.ledger.data.local.LedgerEntryDao
import com.jizhangbao.ledger.data.local.LedgerEntryEntity
import com.jizhangbao.worklog.data.local.WorkSessionDao
import com.jizhangbao.worklog.data.local.WorkSessionEntity
import com.jizhangbao.worklog.data.local.WorkplaceDao
import com.jizhangbao.worklog.data.local.WorkplaceEntity

/**
 * 应用**唯一**的 Room 数据库（本机唯一事实源）。
 *
 * ## 为什么它住在 `:app`
 *
 * `@Database` 必须在编译期列出全部实体，因此它必须能看见所有上下文的实体；
 * 能同时依赖全部 feature 的模块只有 `:app`（R8）。详见 `ADR-0007`。
 *
 * ## 为什么放在 `data` 包
 *
 * 它是数据基础设施，理应待在 data 层；同时 Konsist 的 R10
 * （非 `data`/`di` 的文件不得引用 data 层）也要求这个位置 ——
 * **把它挪到别的包会让架构断言直接失败**，不是随意的选择。
 *
 * ## 迁移
 *
 * `exportSchema = true`：schema JSON 提交进 Git（ADR-0001 决策 3），否则将来写不了迁移测试。
 * **禁止** `fallbackToDestructiveMigration()`（AGENTS.md 第 7 节第 14 条）——
 * 那等于把用户的数据在版本升级时悄悄删掉。
 *
 * 版本历史（每次加表/改列都要在这里记一笔，并同时写迁移）：
 * - `1`：`ledger_entry`（`T-007`）
 * - `2`：`category`（`T-012` / `REQ-004`）—— 新表，用 `MIGRATION_1_2` 建
 * - `3`：`work_session`（`T-029` / `REQ-014/AC-6`）—— 新表，用 `MIGRATION_2_3` 建
 * - `4`：`workplace`（`T-031` / `REQ-016/AC-10`）—— 新表，用 `MIGRATION_3_4` 建。
 *   **三次升级都只加表、不动旧表** —— 所以它们都不可能碰到用户已有的账。
 *
 * 迁移 SQL 都写在 [MIGRATION_1_2] 所在的 `Migrations.kt`（同一个 `data` 包）。
 */
@Database(
    entities = [
        LedgerEntryEntity::class,
        CategoryEntity::class,
        WorkSessionEntity::class,
        WorkplaceEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class JizhangbaoDatabase : RoomDatabase() {

    abstract fun ledgerEntryDao(): LedgerEntryDao

    abstract fun categoryDao(): CategoryDao

    abstract fun workSessionDao(): WorkSessionDao

    abstract fun workplaceDao(): WorkplaceDao
}
