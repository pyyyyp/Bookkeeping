package com.jizhangbao.ledger.presentation

import com.jizhangbao.core.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 金额解析 —— `REQ-001/AC-3` 的**自动化**部分。
 *
 * 模拟器冒烟只能证明「屏幕上提示对了」，证明不了边界：
 * 0 / 负数 / 三位小数 / 极大值都必须在这里被钉住。
 * 用 `BigDecimal` 而不是 `Double` 的原因也由第一组用例守住（12.50 不能变成 12.49）。
 */
class AmountInputTest {

    private fun money(text: String): Money {
        val parsed = parseYuanToMoney(text)
        assertEquals("期望 $text 是合法金额，实际 $parsed", true, parsed is AmountInput.Valid)
        return (parsed as AmountInput.Valid).money
    }

    private fun error(text: String): AmountInputError {
        val parsed = parseYuanToMoney(text)
        assertEquals("期望 $text 被拒绝，实际 $parsed", true, parsed is AmountInput.Invalid)
        return (parsed as AmountInput.Invalid).error
    }

    @Test
    fun `两位小数被精确换算成分而不是浮点近似`() {
        assertEquals(Money.ofCents(1250), money("12.50"))
        assertEquals(Money.ofCents(1), money("0.01"))
        assertEquals(Money.ofCents(800_000), money("8000"))
        assertEquals(Money.ofCents(1250), money("12.5"))
    }

    @Test
    fun `前后空格被忽略`() {
        assertEquals(Money.ofCents(1250), money("  12.50  "))
    }

    @Test
    fun `空输入被拒绝`() {
        assertEquals(AmountInputError.Empty, error(""))
        assertEquals(AmountInputError.Empty, error("   "))
    }

    @Test
    fun `不是数字的输入被拒绝`() {
        assertEquals(AmountInputError.NotANumber, error("abc"))
        assertEquals(AmountInputError.NotANumber, error("12.5.0"))
    }

    @Test
    fun `超过两位小数被拒绝`() {
        assertEquals(AmountInputError.TooManyDecimals, error("12.345"))
        assertEquals(AmountInputError.TooManyDecimals, error("0.001"))
    }

    @Test
    fun `零与负数被拒绝`() {
        assertEquals(AmountInputError.NotPositive, error("0"))
        assertEquals(AmountInputError.NotPositive, error("0.00"))
        assertEquals(AmountInputError.NotPositive, error("-5"))
    }

    @Test
    fun `超出分表示范围的金额被拒绝而不是溢出`() {
        // 若不做位长检查，* 100 会溢出成负数，用户会看到一笔莫名其妙的正数金额
        assertEquals(AmountInputError.NotANumber, error("99999999999999999999"))
    }
}
