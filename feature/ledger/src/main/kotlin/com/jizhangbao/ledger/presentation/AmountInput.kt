package com.jizhangbao.ledger.presentation

import com.jizhangbao.core.domain.Money

/**
 * 用户在金额框里输入的东西。
 *
 * ## 为什么解析放在 presentation 而不是领域
 *
 * 「`12.50` 这个字符串能不能变成金额」是**界面语言**的问题，不是业务规则：
 * 领域只认 [Money]（以分为单位、非负），它不知道什么是「小数点」。
 * 但**「金额必须大于 0」是业务规则**，所以它仍然由聚合判定 ——
 * 这里的 [AmountInputError.NotPositive] 只是为了在输入框旁边立刻给出提示，
 * 领域层的守卫不会因此少一层。
 */
internal sealed interface AmountInput {

    data class Valid(val money: Money) : AmountInput

    data class Invalid(val error: AmountInputError) : AmountInput
}

/**
 * 金额输入的错误种类。
 *
 * 刻意**不携带提示文案**：用户可见字符串必须来自资源文件（AGENTS.md 第 7 节第 12 条），
 * 由界面层把这里的枚举映射到 `R.string.*`。用字符串做错误类型，
 * 迟早会有人在业务代码里拼一句中文提示出来。
 */
internal enum class AmountInputError {
    /** 空输入 */
    Empty,

    /** 根本不是数字 */
    NotANumber,

    /** 小数位超过 2 位（例如 12.345） */
    TooManyDecimals,

    /** 金额不大于 0（0 或负数） */
    NotPositive,
}

/** 「元」最多两位小数。 */
private const val YUAN_SCALE = 2

/**
 * `Long.MAX_VALUE` 是 63 位（含符号位）。要求换算后的分值不超过 62 位，
 * 是为了让后面的 `* 100` 一定不会溢出 —— 魔数提成具名常量，
 * 免得下一个人看到 62 不知道它是从哪来的。
 */
private const val MAX_CENTS_BIT_LENGTH = 62

/**
 * 把「元」的文本解析成 [Money]。
 *
 * 用 `BigDecimal` 而不是 `Double`：`12.50` 在某些浮点表示下会变成 `12.499999...`，
 * 乘 100 取整就会少一分钱 —— 记账场景不能接受（这也是 `Money` 只用分的原因）。
 */
internal fun parseYuanToMoney(text: String): AmountInput {
    val trimmed = text.trim()

    val error = amountErrorOf(trimmed)
    if (error != null) return AmountInput.Invalid(error)

    // 走到这里说明上面已经验证过：能解析、scale ≤ 2、且不溢出
    val cents = trimmed.toBigDecimal().movePointRight(YUAN_SCALE).toBigIntegerExact()
    return AmountInput.Valid(Money.ofCents(cents.toLong()))
}

/**
 * 校验表：写成一条 `when` 而不是连续的 `return`。
 * 这样新增一条规则只是多一行，也不会因为 return 过多被 detekt 拦下。
 */
private fun amountErrorOf(trimmed: String): AmountInputError? {
    val decimal = trimmed.toBigDecimalOrNull()

    return when {
        trimmed.isEmpty() -> AmountInputError.Empty
        decimal == null -> AmountInputError.NotANumber
        decimal.scale() > YUAN_SCALE -> AmountInputError.TooManyDecimals
        decimal.signum() <= 0 -> AmountInputError.NotPositive
        decimal.movePointRight(YUAN_SCALE).toBigIntegerExact().bitLength() > MAX_CENTS_BIT_LENGTH ->
            AmountInputError.NotANumber
        else -> null
    }
}
