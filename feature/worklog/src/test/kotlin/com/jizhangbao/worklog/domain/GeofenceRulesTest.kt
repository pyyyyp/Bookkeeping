package com.jizhangbao.worklog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * 围栏的状态迁移（`REQ-016/AC-1`~`AC-4`）。
 *
 * 这里最值钱的两条断言：
 *
 * 1. **离开不足门槛时结束时刻是"第一次离开那一刻"**，不是"确认不回来那一刻"——
 *    否则每次抖动都会多算 10 分钟工时（那是**多算钱**，方向上比少算更糟）。
 * 2. **抖动窗口内回到圈内，状态要被清干净**（`exitedAt = null`），
 *    否则下一次离开会拿旧的时间戳去算，判定全乱。
 */
class GeofenceRulesTest {

    private val t0: Instant = Instant.parse("2026-10-14T01:00:00Z")
    private val openId = WorkSessionId.random()

    private fun minutesAfter(base: Instant, minutes: Long): Instant =
        base.plus(Duration.ofMinutes(minutes))

    @Test
    fun `AC-1 进入围栏且没有开着的工时时_开始一段`() {
        val (state, action) = GeofenceRules.reduce(GeofenceState(), GeofenceEvent.Entered(t0))

        assertEquals(GeofenceAction.StartSession(t0), action)
        // ⚠️ 身份由执行者生成，所以规则给的还是 null（见 GeofenceRules 的 KDoc）
        assertNull(state.openSessionId)
    }

    @Test
    fun `AC-2 离开后超过门槛_在第一次离开那一刻结束`() {
        val opened = GeofenceState(openSessionId = openId)
        val firstExit = t0

        // 第一次观测到离开：进窗口，**不结束**
        val (afterFirst, firstAction) =
            GeofenceRules.reduce(opened, GeofenceEvent.Exited(firstExit))
        assertEquals(GeofenceAction.Ignore, firstAction)
        assertEquals(firstExit, afterFirst.exitedAt)

        // 11 分钟后又一次"在外面" → 真的走了
        val (afterFinish, finishAction) =
            GeofenceRules.reduce(afterFirst, GeofenceEvent.Exited(minutesAfter(t0, 11)))

        assertEquals(GeofenceAction.FinishSession(openId, endedAt = firstExit), finishAction)
        assertNull(afterFinish.openSessionId)
        assertNull(afterFinish.exitedAt)
    }

    @Test
    fun `AC-4 离开不足门槛又回来_还是同一段`() {
        val opened = GeofenceState(openSessionId = openId)

        val (outside, _) = GeofenceRules.reduce(opened, GeofenceEvent.Exited(t0))
        // 3 分钟后回来了
        val (backInside, action) =
            GeofenceRules.reduce(outside, GeofenceEvent.Entered(minutesAfter(t0, 3)))

        // 不结束、不新建 —— 而且把"离开中"的状态清干净
        assertEquals(GeofenceAction.Ignore, action)
        assertEquals(openId, backInside.openSessionId)
        assertNull(backInside.exitedAt)
    }

    @Test
    fun `门槛边界_正好 10 分钟仍然算没离开`() {
        val opened = GeofenceState(openSessionId = openId)
        val (outside, _) = GeofenceRules.reduce(opened, GeofenceEvent.Exited(t0))

        val (_, action) =
            GeofenceRules.reduce(outside, GeofenceEvent.Exited(minutesAfter(t0, 10)))

        // 规则是"超过 10 分钟才算离开"（`>`，不是 `>=`）—— 边界值是个选择，钉住它
        assertEquals(GeofenceAction.Ignore, action)
    }

    @Test
    fun `没有开着的工时时_离开什么都不做`() {
        val (state, action) = GeofenceRules.reduce(GeofenceState(), GeofenceEvent.Exited(t0))

        assertEquals(GeofenceAction.Ignore, action)
        assertNull(state.openSessionId)
    }

    @Test
    fun `已经开着一段时再进入_不会新建第二段`() {
        val opened = GeofenceState(openSessionId = openId)

        val (state, action) = GeofenceRules.reduce(opened, GeofenceEvent.Entered(t0))

        assertEquals(GeofenceAction.Ignore, action)
        assertEquals(openId, state.openSessionId)
    }

    @Test
    fun `结束之后再进入_是新的一段`() {
        val opened = GeofenceState(openSessionId = openId)
        val (outside, _) = GeofenceRules.reduce(opened, GeofenceEvent.Exited(t0))
        val (afterFinish, _) =
            GeofenceRules.reduce(outside, GeofenceEvent.Exited(minutesAfter(t0, 15)))

        val (_, action) = GeofenceRules.reduce(afterFinish, GeofenceEvent.Entered(minutesAfter(t0, 20)))

        assertEquals(GeofenceAction.StartSession(minutesAfter(t0, 20)), action)
    }
}
