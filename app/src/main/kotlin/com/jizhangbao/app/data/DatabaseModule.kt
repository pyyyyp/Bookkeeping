package com.jizhangbao.app.data

import android.content.Context
import androidx.room.Room
import com.jizhangbao.ledger.data.local.LedgerEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * 组合根的依赖装配：数据库、DAO，以及全局基础设施（时钟）。
 *
 * 按 `ADR-0007`：上下文自己的绑定在各 feature 的 `di` 包，
 * 而「数据库」与「跨上下文的通用设施」在这里 —— `:app` 是唯一看得见全部 feature 的模块。
 *
 * 放在 `data` 包（R10 允许 `data`/`di` 引用 data 层）✓。
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JizhangbaoDatabase =
        Room.databaseBuilder(
            context = context,
            klass = JizhangbaoDatabase::class.java,
            name = DATABASE_NAME,
        ).build()

    @Provides
    fun provideLedgerEntryDao(database: JizhangbaoDatabase): LedgerEntryDao =
        database.ledgerEntryDao()

    /**
     * 系统时钟。注入而不是各处直接 `Instant.now()`：
     * 否则「补记」与「录入时间」的用例没办法稳定测试。
     *
     * ⚠️ 用 `systemDefaultZone()` 而不是 `systemUTC()`：**时钟带时区**，
     * 而 `REQ-002/BR-1` 要求「本月」按用户日历上的月（本机时区）划分自然月。
     * 用 UTC 会让东八区用户在月初/月末看到错误的合计（例如 10-01 08:00 记的账
     * 会被算进 9 月）。`instant()` 与时区无关，所以这个改动**不影响任何既有行为**。
     */
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    private const val DATABASE_NAME = "jizhangbao.db"
}
