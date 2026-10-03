package com.jizhangbao.worklog.data.local

import com.jizhangbao.worklog.domain.SessionState
import com.jizhangbao.worklog.domain.WorkSession
import com.jizhangbao.worklog.domain.WorkSessionId
import java.time.Instant
import java.time.ZoneId

/**
 * 实体 ↔ 领域（`R6`：外部模型绝不进领域）。
 *
 * ⚠️ 它必须拿到**时区**：归属日不存储在表里，读出来时按 `startedAt` + 时区再推导一次
 * （见 [WorkSessionEntity] 的 KDoc）。
 */
internal object WorkSessionMapper {

    fun toDomain(entity: WorkSessionEntity, zone: ZoneId): WorkSession = WorkSession.restore(
        id = WorkSessionId(entity.id),
        startedAt = Instant.ofEpochMilli(entity.startedAtEpochMilli),
        endedAt = entity.endedAtEpochMilli?.let { Instant.ofEpochMilli(it) },
        // 存的是枚举名 —— 用序号的话，枚举顺序一变，历史数据的含义就会悄悄错位
        state = SessionState.valueOf(entity.state),
        zone = zone,
    )

    fun toEntity(session: WorkSession): WorkSessionEntity = WorkSessionEntity(
        id = session.id.value,
        startedAtEpochMilli = session.startedAt.toEpochMilli(),
        endedAtEpochMilli = session.endedAt?.toEpochMilli(),
        state = session.state.name,
    )
}
