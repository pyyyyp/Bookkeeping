package com.jizhangbao.worklog.domain

/**
 * 一个经纬度（十进制度）。
 *
 * ## 为什么自校验
 *
 * 坐标只有一个来源（系统定位），但**坏数据还是会进来**：外部改坏的库、
 * 将来的手工导入、以及写错的默认值。纬度 91 度这样的值一旦进入距离计算，
 * 结果是无意义的数字 —— 而它的下游是"哪天算加班"，差一倍工资。
 * 宁可在这里拒绝，也不要在工资单上出现一个没人看得懂的数。
 *
 * ⚠️ `NaN` 会被 `in` 区间判断拒绝（`NaN in a..b` 恒为 false），这正是想要的：
 * 定位服务偶尔会给出 NaN。
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in MIN_LATITUDE..MAX_LATITUDE) {
            "纬度必须在 ±90 之间：$latitude"
        }
        require(longitude in MIN_LONGITUDE..MAX_LONGITUDE) {
            "经度必须在 ±180 之间：$longitude"
        }
    }

    /**
     * 合法范围。写成具名常量而不是散在 `require` 里的字面量 ——
     * 它们不只是"校验用的数字"，而是这个值对象**定义的一部分**：
     * 纬度 ±90、经度 ±180 是地理事实，别处（将来的数据校验、导入、界面输入框）
     * 也该引用同一份。
     */
    companion object {
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0
    }
}
