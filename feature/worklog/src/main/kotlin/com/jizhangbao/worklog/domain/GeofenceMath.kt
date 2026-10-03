package com.jizhangbao.worklog.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 球面距离与"在不在圈里"（`REQ-016/AC-6`）。**纯函数，无平台依赖。**
 *
 * ## Haversine 公式，以及它的精度
 *
 * 它假设地球是**球体**，用平均半径 6371 km 计算。（真实地球是椭球，赤道半径 6378 km、
 * 极半径 6357 km。）
 *
 * 对"几百米的工作地点半径"来说，这一点误差（约 0.3%，即 500 米上差 1.5 米）完全无所谓 ——
 * 而 GPS 本身的误差是**几米到几十米**。所以这里不是精度妥协，而是**与需求匹配的精度**：
 * 换成 Vincenty 公式会多几十行，换来一个被 GPS 噪声完全淹没的改善。
 *
 * ## 为什么自己算而不是用平台的现成方法
 *
 * 见 `ADR-0013` 决策 2。还有一个现实理由：**纯函数能在 JVM 上测跨经线、极地、
 * 零距离这些边界** —— 而这些恰恰是围栏最容易错的地方（东经 179° 与西经 179° 只差 2°）。
 */
object GeofenceMath {

    /** 地球平均半径（米）。 */
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * 两点间的球面距离（米）。
     *
     * ⚠️ 末尾的 `min(1.0, …)` 不是装饰：浮点误差可能让 `sqrt(a)` 略微超过 1
     * （例如两个**完全相同**的点在极端舍入下），而 `asin(>1)` 是 **NaN** ——
     * 那会让"在不在圈里"变成 false，也就是"明明在上班却不算"。
     */
    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        val a = sin(deltaLat / 2).pow(2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)

        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    /** 点是否落在以 [center] 为圆心、[radiusMeters] 为半径的圈内（**含边界**）。 */
    fun isInside(point: GeoPoint, center: GeoPoint, radiusMeters: Double): Boolean =
        distanceMeters(center, point) <= radiusMeters
}
