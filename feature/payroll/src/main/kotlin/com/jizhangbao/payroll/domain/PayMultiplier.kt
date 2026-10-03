package com.jizhangbao.payroll.domain

// 计薪倍率的数值。写成文件级 `const`（编译期常量）而不是 companion 里的常量：
// **枚举常量在 companion 之前初始化**，所以枚举的构造参数引用不到它们
// （Kotlin 会报 "Companion object of enum class is uninitialized here"）。
// 它们取自 Q-012 的答复原文，是规则的一部分，不是随手写的数字。
private const val NO_PAY = 0
private const val NORMAL_PAY = 1
private const val REST_DAY_FACTOR = 2
private const val HOLIDAY_FACTOR = 3

/**
 * 计薪倍率（`Q-012` + `Q-015` 定案）。
 *
 * `factor` 是**整数**：倍率只有这四种，用整数就不会出现
 * "`1.9999999 × 日薪`"这种事 —— 钱的倍率不该是个可以带小数的东西。
 */
enum class PayMultiplier(val factor: Int) {
    /** 不计薪：缺勤的工作日、不加班的休息日。 */
    NONE(NO_PAY),

    /** 正常计薪：工作日出勤，以及**法定节假日休息**（`Q-015` 定案：带薪）。 */
    NORMAL(NORMAL_PAY),

    /** 休息日加班：2 倍（`Q-012`）。 */
    REST_DAY_OVERTIME(REST_DAY_FACTOR),

    /** 法定节假日加班：3 倍（`Q-012`）。 */
    HOLIDAY_OVERTIME(HOLIDAY_FACTOR),
}
