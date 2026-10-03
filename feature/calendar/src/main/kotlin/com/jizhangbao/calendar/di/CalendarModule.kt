package com.jizhangbao.calendar.di

import com.jizhangbao.calendar.data.AssetWorkCalendar
import com.jizhangbao.core.domain.WorkCalendar
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Calendar 的装配：把只读端口绑到实现上（`ADR-0008` 的读通路）。
 *
 * 消费方（Payroll）只认内核里的 `WorkCalendar`，不认这里的实现 ——
 * 这正是 `R2` 下跨上下文读数据的唯一通路（`ADR-0011` 决策 4 记了那次裁决）。
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class CalendarModule {

    @Binds
    @Singleton
    internal abstract fun bindWorkCalendar(impl: AssetWorkCalendar): WorkCalendar
}
