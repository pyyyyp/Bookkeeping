package com.jizhangbao.worklog.domain

/**
 * 一个**有地理围栏**的工作地点（`REQ-016`）：名称 + 圆心 + 半径。
 *
 * ## 为什么不是 `data class`
 *
 * 它有不变量（半径必须为正、名称不能空）—— 与 `WorkSession` 同一条理由：
 * `data class` 的 `copy()` 会绕过全部不变量。所以私有构造 + 具名工厂。
 *
 * ## 为什么自己算距离，而不用平台的围栏 API
 *
 * `ADR-0013` 决策 2：**规则归自己，平台只取数**。
 * `LocationManager.addProximityAlert` 在 API 29 已废弃；更重要的是，
 * "在不在圈里"是一条规则，规则跑在 JVM 上就能有边界用例（正好在半径上、跨经线），
 * 而不是依赖某个 API 的黑盒行为。
 */
class Workplace private constructor(
    val id: WorkplaceId,
    /** 用户起的名字（不是硬编码文案，`R12`）。 */
    val name: String,
    val center: GeoPoint,
    val radiusMeters: Double,
) {

    init {
        require(name.isNotBlank()) { "工作地点名称不能为空" }
        require(radiusMeters > 0.0) { "半径必须为正：$radiusMeters" }
    }

    /**
     * 这个点是否在围栏**内**。
     *
     * ⚠️ 含边界（`<=`）：正好落在半径上算在圈内。`REQ-016/AC-6` 把这条写进了验收标准 ——
     * 因为它是个**选择**，不是"显然"：反过来（`<`）会让"刚好在门口"的状态反复横跳。
     */
    fun contains(point: GeoPoint): Boolean =
        GeofenceMath.distanceMeters(center, point) <= radiusMeters

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is Workplace &&
                id == other.id &&
                name == other.name &&
                center == other.center &&
                radiusMeters == other.radiusMeters
            )

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + center.hashCode()
        result = 31 * result + radiusMeters.hashCode()
        return result
    }

    override fun toString(): String =
        "Workplace(id=$id, name=$name, radius=${radiusMeters}m)"

    companion object {

        /** 新建一个工作地点（界面/配置路径）。 */
        fun of(
            id: WorkplaceId,
            name: String,
            center: GeoPoint,
            radiusMeters: Double,
        ): Workplace = Workplace(id, name, center, radiusMeters)

        /** 从存储还原（数据层用）—— 照样过一遍不变量。 */
        fun restore(
            id: WorkplaceId,
            name: String,
            center: GeoPoint,
            radiusMeters: Double,
        ): Workplace = Workplace(id, name, center, radiusMeters)
    }
}
