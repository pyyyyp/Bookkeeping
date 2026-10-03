package com.jizhangbao.worklog.data.local

import com.jizhangbao.worklog.domain.GeoPoint
import com.jizhangbao.worklog.domain.Workplace
import com.jizhangbao.worklog.domain.WorkplaceId

/**
 * 实体 ↔ 领域（`R6`）。**读的时候照样过一遍领域校验** ——
 * 库里被外部改坏的坐标不该被带进围栏判定（它会直接变成"哪天真算加班"）。
 */
internal object WorkplaceMapper {

    fun toDomain(entity: WorkplaceEntity): Workplace = Workplace.restore(
        id = WorkplaceId(entity.id),
        name = entity.name,
        center = GeoPoint(latitude = entity.latitude, longitude = entity.longitude),
        radiusMeters = entity.radiusMeters,
    )

    fun toEntity(workplace: Workplace): WorkplaceEntity = WorkplaceEntity(
        id = workplace.id.value,
        name = workplace.name,
        latitude = workplace.center.latitude,
        longitude = workplace.center.longitude,
        radiusMeters = workplace.radiusMeters,
    )
}
