package com.jizhangbao.worklog.domain.repository

import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.worklog.domain.WorkSession
import com.jizhangbao.worklog.domain.WorkSessionId
import java.time.Instant
import java.time.LocalDate

/**
 * 工时时段的仓储（`REQ-014/AC-6`）。
 *
 * 接口说**领域语言**：`record`（记一段）、`sessionsIn`（取某几天的）。
 * 它不提 SQL、不提表名、也不提"归属日存在哪一列"（那根本没存，见 `WorkSessionEntity`）。
 */
interface WorkSessionRepository {

    /**
     * 记一段工时。
     *
     * v1 的手工录入**直接是已确认**（`BR-3`：没有围栏可校验时，用户的录入就是断言）。
     */
    suspend fun record(startedAt: Instant, endedAt: Instant): Outcome<WorkSessionId>

    /** 取**归属日**落在 `[from, toInclusive]` 的时段（按开始时刻升序）。 */
    suspend fun sessionsIn(from: LocalDate, toInclusive: LocalDate): Outcome<List<WorkSession>>

    /**
     * 把一段工时**整段**存回去（`T-031`：确认 / 作废走这条路）。
     *
     * 状态转换在领域里做（`WorkSession.confirm()` / `discard()`），仓储不参与判断 ——
     * 它只负责"库里那一行现在等于领域那一行"。
     */
    suspend fun save(session: WorkSession): Outcome<Unit>
}
