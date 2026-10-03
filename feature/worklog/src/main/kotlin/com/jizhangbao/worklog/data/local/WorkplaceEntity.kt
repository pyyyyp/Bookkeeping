package com.jizhangbao.worklog.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `Workplace` 的 Room 实体（外部模型，`REQ-016/AC-7`、`AC-10`）。
 *
 * 字段全是原始类型（与其他实体一致 ✓）：坐标与半径存 `Double` → SQLite 的 `REAL`。
 * 领域里的校验（纬度 ±90、半径 > 0）在 `Workplace` 的 `init` 里，
 * 这里的 `restore()` 也**照走过一遍** —— 所以库里被外部改坏的值会被拦住，而不是带着它去判定围栏。
 */
@Entity(tableName = "workplace")
data class WorkplaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
)
