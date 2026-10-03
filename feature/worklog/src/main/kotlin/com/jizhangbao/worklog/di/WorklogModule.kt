package com.jizhangbao.worklog.di

import com.jizhangbao.core.domain.WorklogReader
import com.jizhangbao.worklog.data.WorklogReaderImpl
import com.jizhangbao.worklog.data.repository.WorkSessionRepositoryImpl
import com.jizhangbao.worklog.domain.repository.WorkSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Worklog 的依赖绑定（`ADR-0007`：上下文自己的绑定住上下文的 `di` 包）。
 *
 * 两条绑定都是**依赖倒置**：
 *
 * - `WorkSessionRepository`（domain 接口）→ 实现（data）
 * - `WorklogReader`（**内核端口**）→ 实现（data）
 *
 * 第二条是 `ADR-0008` / `ADR-0012` 的读通路：端口住 `core:domain`，
 * 实现在这里，**装配点也在**这里（`:app` 只负责把它需要的模块拉进来）。
 * 于是 Payroll 能拿到工时事实，却**不认识** `feature:worklog` 的任何类型。
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WorklogModule {

    @Binds
    @Singleton
    internal abstract fun bindWorkSessionRepository(impl: WorkSessionRepositoryImpl): WorkSessionRepository

    @Binds
    @Singleton
    internal abstract fun bindWorklogReader(impl: WorklogReaderImpl): WorklogReader
}
