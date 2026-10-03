package com.jizhangbao.ledger.domain.model

import java.util.UUID

/**
 * 账目条目的标识。
 *
 * **为什么由领域分配 UUID，而不是等数据库自增 ID**：
 * 标识在「被记录」的那一刻就该存在——聚合在持久化之前必须是完整的、可测试的，
 * 不该为了拿一个 ID 而依赖数据层（这是 R4 依赖倒置在标识上的体现）。
 *
 * 见 `docs/20-domain/ledger-model.md` 的「标识」一节。
 */
@JvmInline
value class LedgerEntryId(val value: String) {

    init {
        require(value.isNotBlank()) { "条目标识不可为空" }
        require(runCatching { UUID.fromString(value) }.isSuccess) {
            "条目标识必须是合法 UUID：$value"
        }
    }

    override fun toString(): String = value

    companion object {
        /** 新建条目的标识 */
        fun new(): LedgerEntryId = LedgerEntryId(UUID.randomUUID().toString())
    }
}
