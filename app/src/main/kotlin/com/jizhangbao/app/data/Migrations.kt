package com.jizhangbao.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移（本项目**手写**，不使用自动迁移）。
 *
 * ## 为什么手写、以及为什么不用 `fallbackToDestructiveMigration`
 *
 * 破坏性迁移等于在版本升级时把用户的数据删掉 —— 明令禁止（AGENTS.md 第 7 节第 14 条）。
 * 自动迁移（`@AutoMigration`）也能用，但它在"加表"这种情形下要求实体与 SQL 严格对应，
 * 出错时报的是运行时异常；手写 SQL 让**升级时到底执行了什么**一眼可见，
 * 而这是本机唯一事实源，值得写清楚。
 *
 * ## 版本历史
 *
 * - `1 → 2`（`T-012` / `REQ-004`）：新增自定义分类表 `category`。
 *   **只加表，不动 `ledger_entry`** —— 所以这次升级不可能碰到用户已有的账 ✓
 *   （`REQ-004/AC-9` 会在带真实数据的模拟器上验证这一点）。
 *
 * ⚠️ SQL 必须与 `CategoryEntity` 经 Room 生成的 schema **逐字对应**
 * （列名、类型、`NOT NULL`、主键）。对不上时 Room 会在**打开数据库时**
 * 用 `Migration didn't properly handle...` 报错 —— 那不是静默失败，但只有真跑一次才看得见，
 * 所以每次改表都要在真机上装一次新版本。
 */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `category` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`directions` TEXT NOT NULL, " +
                "`archived` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`)" +
                ")",
        )
    }
}

/** 全部迁移，按版本顺序。组合根装配数据库时一次性交给 Room。 */
internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
