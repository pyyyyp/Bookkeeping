package com.jizhangbao.ledger.di

import com.jizhangbao.core.domain.LedgerTotalsReader
import com.jizhangbao.ledger.data.repository.LedgerEntryRepositoryImpl
import com.jizhangbao.ledger.data.totals.LedgerTotalsReaderImpl
import com.jizhangbao.ledger.domain.repository.LedgerEntryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Ledger 上下文的依赖装配。
 *
 * 按 `ADR-0007`：上下文自己的绑定放在自己的 `di` 包；
 * 数据库与 DAO 的提供在 `:app`（那是组合根的事）。
 *
 * `@Binds` 而不是 `@Provides`：实现类有 `@Inject` 构造器，
 * `@Binds` 只是把接口指向它——生成的代码更小，也不必手写 new。
 *
 * 本模块**不**提供 `Clock`：那是全局基础设施，`@Provides` 在 `:app`。
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class LedgerModule {

    @Binds
    @Singleton
    internal abstract fun bindLedgerEntryRepository(
        impl: LedgerEntryRepositoryImpl,
    ): LedgerEntryRepository

    /**
     * 跨上下文读端口（`ADR-0008`）：契约在内核，实现在这里，消费方是 `:feature:insight`。
     *
     * 绑在 SingletonComponent 里，所以两个模块的 Hilt 装配能对上——
     * 但**没有任何编译期依赖**在 feature 之间产生。
     */
    @Binds
    @Singleton
    internal abstract fun bindLedgerTotalsReader(
        impl: LedgerTotalsReaderImpl,
    ): LedgerTotalsReader
}
