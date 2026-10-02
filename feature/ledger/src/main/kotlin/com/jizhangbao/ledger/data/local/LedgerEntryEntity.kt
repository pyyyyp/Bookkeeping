package com.jizhangbao.ledger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `LedgerEntry` 的 Room 实体（外部模型）。
 *
 * ## 为什么字段全是原始类型
 *
 * 领域值对象（`Money` / `CategoryId` / `Note`）与 `Instant` 一律拆成 `Long` / `String` 存储，
 * 转换交给 [LedgerEntryMapper]。好处有三条：
 *
 * 1. **不需要任何 `TypeConverter`** —— 于是「共享的转换器该放哪个模块」这个问题根本不存在
 *    （`Money` 是共享内核类型，但 `CategoryId` 是 Ledger 的；混在一起就得让 `:core:data`
 *    依赖 `:feature:ledger`，那是 R7 禁止的）。
 * 2. 数据库里存的是什么，一眼能看出来；表结构不依赖值对象的内部表示。
 * 3. 将来值对象换实现（例如 `Money` 改存更大范围），表结构不受影响。
 *
 * ## 存储细节（不是业务规则）
 *
 * - `direction` 存**枚举名**而不是序号：序号会随枚举顺序变化而悄悄错位。
 * - 时间存 `epochMilli`，因此**持久化只保留到毫秒**。
 *   领域层的 `Instant` 可以更精确，但界面能给到的最细粒度就是毫秒，
 *   所以这不是精度损失，而是「存储粒度与输入粒度一致」。
 * - `note` 可空：领域里「没有备注」只有 `null` 一种表达，这里保持一致。
 *
 * ⚠️ 本类名以 `Entity` 结尾是刻意的（它是外部模型），
 * 而 `R6` 的命名断言**只扫 `domain` 包**——它出现在 `data` 包里完全正当。
 */
@Entity(tableName = "ledger_entry")
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val direction: String,
    val amountCents: Long,
    val categoryId: String,
    val occurredAtEpochMilli: Long,
    val bookedAtEpochMilli: Long,
    val note: String?,
)
