package com.jizhangbao.worklog.domain

import java.util.UUID

/**
 * 工作地点的身份。
 *
 * 与 `WorkSessionId` 同样的理由要求合法 UUID：身份不由外部输入，
 * 但**数据被外部改坏时要能被发现** —— 那时应当明确拒绝，
 * 而不是拿着一个来路不明的身份继续判断"该不该开始记工时"。
 */
@JvmInline
value class WorkplaceId(val value: String) {
    init {
        require(runCatching { UUID.fromString(value) }.isSuccess) {
            "工作地点标识必须是合法 UUID：$value"
        }
    }

    companion object {
        fun random(): WorkplaceId = WorkplaceId(UUID.randomUUID().toString())
    }
}
