package com.jizhangbao.worklog.domain

import java.util.UUID

/**
 * 工时时段的身份。
 *
 * 与 `LedgerEntryId` 一样要求合法 UUID：身份不从外部来（本 App 自己生成），
 * 但**数据被外部改坏时要能被发现** —— 一个 "abc" 这样的标识说明库里的东西不是本 App 写的，
 * 那时应该明确拒绝，而不是带着一个来路不明的身份继续算工资。
 */
@JvmInline
value class WorkSessionId(val value: String) {
    init {
        require(runCatching { UUID.fromString(value) }.isSuccess) {
            "工时时段标识必须是合法 UUID：$value"
        }
    }

    companion object {
        fun random(): WorkSessionId = WorkSessionId(UUID.randomUUID().toString())
    }
}
