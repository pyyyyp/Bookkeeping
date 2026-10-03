package com.jizhangbao.worklog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 球面距离与"在不在圈里"（`REQ-016/AC-6`）。
 *
 * 两条最要紧的：**跨经线**（东经 179.9° 与西经 179.9°只差 0.2°，
 * 但直接相减得到 359.8° —— 那会把"隔壁"算成"绕地球一圈"）
 * 与**正好在半径上**（含等号是一个选择，得钉住）。
 */
class GeofenceMathTest {

    private val somewhere = GeoPoint(latitude = 31.2304, longitude = 121.4737) // 上海

    @Test
    fun `同一个点距离为零`() {
        assertEquals(0.0, GeofenceMath.distanceMeters(somewhere, somewhere), 0.001)
    }

    @Test
    fun `一度纬度约 111 公里`() {
        val oneDegreeNorth = GeoPoint(somewhere.latitude + 1.0, somewhere.longitude)

        // Haversine 用平均半径，一度纬度约 111.19 km；给 0.5 km 容差
        assertEquals(111_195.0, GeofenceMath.distanceMeters(somewhere, oneDegreeNorth), 500.0)
    }

    @Test
    fun `跨经线时不会把隔壁算成绕地球一圈`() {
        val justWestOfTheLine = GeoPoint(latitude = 0.0, longitude = 179.9)
        val justEastOfTheLine = GeoPoint(latitude = 0.0, longitude = -179.9)

        val distance = GeofenceMath.distanceMeters(justWestOfTheLine, justEastOfTheLine)

        // 0.2 度 ≈ 22.2 km。若按经度直接相减（359.8 度）会得到约 40000 km
        assertEquals(22_240.0, distance, 200.0)
    }

    @Test
    fun `极地附近不产生 NaN`() {
        val northPole = GeoPoint(latitude = 90.0, longitude = 0.0)
        val nearby = GeoPoint(latitude = 89.999, longitude = 180.0)

        val distance = GeofenceMath.distanceMeters(northPole, nearby)

        // 关键不是数值精度，而是它是个**有限的正常数**（浮点越界会让 asin 变 NaN）
        assertTrue("距离不该是 NaN：$distance", distance.isFinite())
        assertTrue("该在 100 米量级：$distance", distance in 50.0..200.0)
    }

    @Test
    fun `正好在半径上算在圈内`() {
        // 取一条"正北 111.19 米"的线段不可靠，所以反过来：用已知距离构造半径
        val point = GeoPoint(somewhere.latitude + 0.001, somewhere.longitude) // 约 111.2 米
        val exactDistance = GeofenceMath.distanceMeters(somewhere, point)

        // 半径正好等于距离 → 含边界 → 在圈内
        assertTrue(GeofenceMath.isInside(point, somewhere, exactDistance))
    }

    @Test
    fun `稍微超出半径就是在圈外`() {
        val point = GeoPoint(somewhere.latitude + 0.001, somewhere.longitude)
        val exactDistance = GeofenceMath.distanceMeters(somewhere, point)

        assertFalse(GeofenceMath.isInside(point, somewhere, exactDistance - 0.001))
    }

    @Test
    fun `圈内圈外的常见尺度`() {
        // 工作地点半径 200 米：正北约 100 米处在内，约 300 米处在外
        val inside = GeoPoint(somewhere.latitude + 0.0009, somewhere.longitude)
        val outside = GeoPoint(somewhere.latitude + 0.0027, somewhere.longitude)

        assertTrue(GeofenceMath.isInside(inside, somewhere, 200.0))
        assertFalse(GeofenceMath.isInside(outside, somewhere, 200.0))
    }
}
