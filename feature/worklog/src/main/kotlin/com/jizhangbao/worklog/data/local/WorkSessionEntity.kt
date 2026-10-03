package com.jizhangbao.worklog.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `WorkSession` 的 Room 实体（外部模型）。
 *
 * ## ⚠️ 归属日**不在这里**
 *
 * 领域里的 `WorkSession.day` 是由 `startedAt` + 时区**推导**出来的（`T-027` 的设计）。
 * 存一列 `day` 就会多一个可能与 `startedAt` 不一致的地方 ——
 * 而 `REQ-014/AC-4`（跨午夜归开始那天）正是那种"写错一次就悄悄算错工资"的规则。
 * 所以这一列**刻意不存**：读出来时按同一个规则再推一次。
 *
 * ## 字段全是原始类型（与其他实体一致）
 *
 * `Instant` 拆成 `epochMilli`、状态存**枚举名**（序号会随枚举顺序变化悄悄错位）。
 * 于是不需要任何 `TypeConverter`（`T-007` 记过这条取舍）。
 *
 * @param endedAtEpochMilli 可空：`Running` 的时段还没有结束。
 *   这个"可空"不是图方便 —— 它是状态机的一半不变量（`T-027` 的 `WorkSession`）。
 */
@Entity(
    tableName = "work_session",
    indices = [Index(value = ["startedAtEpochMilli"])],
)
data class WorkSessionEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMilli: Long,
    val endedAtEpochMilli: Long?,
    val state: String,
)
