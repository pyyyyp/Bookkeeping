package com.jizhangbao.core.domain

import java.time.Instant

/**
 * 领域事件：业务上**已经发生**的事实。
 *
 * 命名必须用过去式（`OrderPlaced`，不是 `PlaceOrderEvent`）。
 * 它是跨上下文解耦的唯一推荐通道 —— 上下文之间不得互相 import。
 *
 * 见 docs/20-domain/context-map.md
 */
interface DomainEvent {
    val occurredAt: Instant
}

/**
 * 聚合根。
 *
 * 约定：
 * - 一次事务只修改一个聚合
 * - 不变式在聚合根方法内强制，外部无法绕过
 * - 外部只能通过本接口访问，拿不到内部实体的可变引用
 *
 * [pendingEvents] 由应用服务在保存聚合后取出并发布，取出后必须调用
 * [clearPendingEvents]，否则事件会被重复发布。
 */
interface AggregateRoot<ID : Any> {
    val id: ID
    val pendingEvents: List<DomainEvent>
    fun clearPendingEvents()
}
