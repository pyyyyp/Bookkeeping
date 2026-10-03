package com.jizhangbao.payroll.di

import com.jizhangbao.payroll.data.repository.MonthlySalaryRepositoryImpl
import com.jizhangbao.payroll.domain.repository.MonthlySalaryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Payroll 的依赖绑定（`ADR-0007`：上下文自己的绑定住上下文的 `di` 包）。
 *
 * 只要一条：月薪仓储（`REQ-017/AC-1`）。
 * `LoadPayslipUseCase` 不需要绑定 —— 它的构造参数是内核端口，由别的模块提供。
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class PayrollModule {

    @Binds
    @Singleton
    internal abstract fun bindMonthlySalaryRepository(
        impl: MonthlySalaryRepositoryImpl,
    ): MonthlySalaryRepository
}
