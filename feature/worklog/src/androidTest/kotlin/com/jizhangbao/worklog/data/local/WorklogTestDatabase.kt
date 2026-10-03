package com.jizhangbao.worklog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * **测试专用**的数据库（`T-029`）。
 *
 * 为什么要单独一个：生产库里只有一个 `@Database`（住在 `:app`，`ADR-0007`），
 * 而 `:app` 是 application 模块、它的 Room 实现没法在 feature 的 androidTest 里直接用。
 * 所以这里只为"工时段"这一张表建一个，专门用来在**真 SQLite** 上验证 DAO 的 SQL。
 *
 * `exportSchema = false`：它是测试用的，schema 没有归档价值
 * （生产的 schema 由 `:app` 导出并提交进 Git）。
 */
@Database(entities = [WorkSessionEntity::class], version = 1, exportSchema = false)
abstract class WorklogTestDatabase : RoomDatabase() {
    abstract fun workSessionDao(): WorkSessionDao
}
