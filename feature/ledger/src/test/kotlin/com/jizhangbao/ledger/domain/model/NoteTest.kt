package com.jizhangbao.ledger.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 备注的字符边界 —— 覆盖 `docs/20-domain/ledger-model.md` 的 INV-5。
 *
 * 重点是**按 Unicode 码点计数**：用户看到的「200 个字」不能被 emoji 打折。
 */
class NoteTest {

    @Test
    fun `恰好 200 个字的备注被接受`() {
        val note = Note("记".repeat(Note.MAX_CODE_POINTS))

        assertEquals(Note.MAX_CODE_POINTS, note.text.codePointCount(0, note.text.length))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `201 个字的备注被拒绝`() {
        Note("记".repeat(Note.MAX_CODE_POINTS + 1))
    }

    @Test
    fun `200 个 emoji 被接受，因为按码点而不是 UTF-16 码元计数`() {
        val emoji = "🍜"

        // 前提：这个 emoji 在 UTF-16 里占 2 个码元，所以 String.length 会翻倍
        assertEquals(2, emoji.length)

        val note = Note(emoji.repeat(Note.MAX_CODE_POINTS))

        assertEquals(Note.MAX_CODE_POINTS, note.text.codePointCount(0, note.text.length))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `只有空白的备注被拒绝`() {
        Note("   ")
    }
}
