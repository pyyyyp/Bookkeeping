package com.jizhangbao.core.domain

/**
 * 领域操作的返回值。
 *
 * 为什么不用异常：业务失败（积分不足、时段重叠）是**正常路径**，不是异常状况。
 * 用密封类表达可以让调用方在编译期被迫处理失败分支，
 * 也避免异常携带堆栈穿越层边界造成的性能与语义污染。
 *
 * 见 AGENTS.md 第 11 节「错误处理与状态建模」
 */
sealed interface Outcome<out T> {

    data class Ok<T>(val value: T) : Outcome<T>

    data class Err(val error: DomainError) : Outcome<Nothing>
}

/**
 * 领域错误的公共父类型。
 *
 * 分层规则：`data` 层负责把网络/数据库异常**翻译**成这里的子类型，
 * 裸异常不得跨越 `data` → `domain` 边界。
 */
sealed interface DomainError {

    /** 聚合不存在 */
    data object NotFound : DomainError

    /** 输入不满足值对象校验 */
    data object InvalidInput : DomainError

    /** 聚合不变式被拒绝 */
    data object InvariantViolated : DomainError

    /** 技术性错误。由 data 层翻译而来 */
    sealed interface Technical : DomainError {
        data object Network : Technical
        data object Storage : Technical
        data object Unknown : Technical
    }
}
