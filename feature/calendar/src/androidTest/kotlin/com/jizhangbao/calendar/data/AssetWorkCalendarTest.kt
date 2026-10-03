package com.jizhangbao.calendar.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jizhangbao.core.common.AppLogger
import com.jizhangbao.core.domain.DayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * **真机上的证据**：资产文件真的被打进包里了、并且真的读得出来（`T-025`）。
 *
 * 纯 JVM 测试证明不了这一点 —— 而它一旦坏掉，表现不是崩溃，而是
 * **"永远显示今年没有节假日"**：一个安静的错误。
 *
 * ⚠️ 方法名用 ASCII（教训 17：仪器化环境里非 ASCII 方法名会让上报层抛异常，
 * 结果是"空结果 + 退出码 1"，看不出跑没跑）。
 */
@RunWith(AndroidJUnit4::class)
class AssetWorkCalendarTest {

    private val logger = object : AppLogger {
        val messages = mutableListOf<String>()
        override fun warn(message: String, cause: Throwable?) {
            messages += message
        }
    }

    private val calendar = AssetWorkCalendar(ApplicationProvider.getApplicationContext(), logger)

    /** 任意一个周六（从星期几推出来，不依赖"某个具体日子是周几"的记忆）。 */
    private val aSaturday: LocalDate =
        LocalDate.of(2026, 10, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))

    @Test
    fun asset_is_packaged_and_readable() {
        calendar.typeOf(aSaturday)

        // 读得出来就不该有"读不出来"那条日志。这一步要是失败，说明资产没进包。
        assertTrue(
            "资产读不出来，日志是：${logger.messages}",
            logger.messages.none { it.contains("读不出来") },
        )
    }

    @Test
    fun empty_data_falls_back_to_weekend_rule_and_says_so() {
        val result = calendar.typeOf(aSaturday)

        // 数据文件目前是空的（真实的法定节假日由人每年填），
        // 所以行为必须是 AC-4 那一条：按周末规则判定 + **明确说这是推出来的**
        assertEquals(DayType.REST_DAY, result.type)
        assertTrue("数据没填时必须标 missingYear，否则用户会以为今年没有节假日", result.missingYear)
        assertFalse("这一次不该有坏行日志", logger.messages.any { it.contains("无法解析") })
    }
}
