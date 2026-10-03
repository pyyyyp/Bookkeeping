package com.jizhangbao.worklog.data

import com.jizhangbao.core.domain.AttendedDay
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.WorklogReader
import com.jizhangbao.worklog.domain.AttendedDays
import com.jizhangbao.worklog.domain.repository.WorkSessionRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内核端口 `WorklogReader` 的实现（`ADR-0012` 决策 1：端口住内核、实现住上下文、装配在 `:app`）。
 *
 * ## ⚠️ 一个明说的取舍：读失败与"没有工时段"在返回值上不可区分
 *
 * 端口返回的是 `List<AttendedDay>`，没有失败通道。所以数据层读不出来时，
 * 这里只能返回空表 —— 而空表与"这几天真的没记工时"长得一样。
 *
 * 为什么 v1 接受它：
 *
 * - **失败的原因已被数据层记进日志**（`WorkSessionRepositoryImpl`），排障有据；
 * - **工作日缺省是 `1×`**（`Q-026`），所以"读失败"不会让工作日少算钱；
 * - 受影响的只有**休息日/法定节假日的加班**（会少算）—— 而这一版还没有界面能录入工时，
 *   也就是说这条路暂时没人走。
 *
 * **将来要改**：给端口加一个"这次读成功了吗"的标志，让工资单能把它显示出来
 * （就像 `REQ-012` 的 `missingYear` 那样）。那时 `LoadPayslipUseCase` 也会跟着改成
 * 返回 `Outcome` —— 那是一次显式的改动，不是悄悄吞掉。
 */
@Singleton
class WorklogReaderImpl @Inject constructor(
    private val repository: WorkSessionRepository,
) : WorklogReader {

    override suspend fun attendedDays(from: LocalDate, toInclusive: LocalDate): List<AttendedDay> =
        when (val sessions = repository.sessionsIn(from, toInclusive)) {
            is Outcome.Ok -> AttendedDays.from(sessions.value, from, toInclusive)
            is Outcome.Err -> emptyList()
        }
}
