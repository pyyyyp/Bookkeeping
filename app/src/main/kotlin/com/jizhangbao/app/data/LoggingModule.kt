package com.jizhangbao.app.data

import com.jizhangbao.core.common.AppLogger
import com.jizhangbao.core.common.LogcatLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 日志的装配（`ADR-0010`：接口在 `:core:common`，实现与绑定在组合根）。
 *
 * 单独一个模块而不是塞进 `DatabaseModule`：日志不是数据库的一部分，
 * 它将来会被别的基础设施用到（网络、导出……）。
 */
@Module
@InstallIn(SingletonComponent::class)
internal object LoggingModule {

    @Provides
    @Singleton
    fun provideAppLogger(): AppLogger = LogcatLogger()
}
