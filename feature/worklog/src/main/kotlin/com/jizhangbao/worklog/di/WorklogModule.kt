package com.jizhangbao.worklog.di

import com.jizhangbao.core.domain.WorklogReader
import com.jizhangbao.worklog.data.WorklogReaderImpl
import com.jizhangbao.worklog.data.location.AndroidLocationSource
import com.jizhangbao.worklog.data.repository.WorkSessionRepositoryImpl
import com.jizhangbao.worklog.data.repository.WorkplaceRepositoryImpl
import com.jizhangbao.worklog.domain.LocationSource
import com.jizhangbao.worklog.domain.repository.WorkSessionRepository
import com.jizhangbao.worklog.domain.repository.WorkplaceRepository
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

    /** 工作地点（`T-031`）：界面上配的地点要存得住、读得回（`REQ-016/AC-10`）。 */
    @Binds
    @Singleton
    internal abstract fun bindWorkplaceRepository(impl: WorkplaceRepositoryImpl): WorkplaceRepository

    @Binds
    @Singleton
    internal abstract fun bindWorklogReader(impl: WorklogReaderImpl): WorklogReader

    /**
     * 定位来源（`REQ-016`）：**ACL** —— 领域只认 `LocationSource`，
     * 系统 `LocationManager` 的实现被封在 data 层（`ADR-0013` 决策 2）。
     */
    @Binds
    @Singleton
    internal abstract fun bindLocationSource(impl: AndroidLocationSource): LocationSource
}
