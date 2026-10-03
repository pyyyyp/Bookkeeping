package com.jizhangbao.worklog.domain.repository

import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.worklog.domain.Workplace
import com.jizhangbao.worklog.domain.WorkplaceId

/**
 * 工作地点的仓储（`REQ-016/AC-7`、`AC-10`）。
 *
 * 接口说领域语言：存一个地点、取全部、删一个。不提表名、不提 SQL。
 */
interface WorkplaceRepository {

    suspend fun save(workplace: Workplace): Outcome<Unit>

    /** 按名称排序（界面直接显示，不再排一次）。 */
    suspend fun all(): Outcome<List<Workplace>>

    suspend fun remove(id: WorkplaceId): Outcome<Unit>
}
