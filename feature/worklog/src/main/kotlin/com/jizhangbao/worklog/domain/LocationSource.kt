package com.jizhangbao.worklog.domain

import kotlinx.coroutines.flow.Flow

/**
 * 定位来源（**ACL 端口**，上下文地图里写着「定位服务（外部）→ Worklog = ACL」）。
 *
 * ## 为什么要有它
 *
 * 领域规则（[GeofenceRules] / [GeofenceMath]）**必须能跑在 JVM 上**，
 * 所以它们不能碰 `android.location`。平台把坐标送进来的方式被封在这个接口后面：
 * 系统换成别的东西（或者将来要模拟位置做测试）时，被替换的只有实现。
 *
 * ## 为什么带 [hasPermission]
 *
 * `REQ-016/AC-5`：没有权限时**不崩、也不静默**。
 * 静默最糟 —— 用户会以为"今天没上班"，而真相是权限没给（`REQ-006` 的教训）。
 * 所以"有没有权限"是端口**必须回答**的问题，而不是实现内部的细节。
 *
 * ## v1 只在 App 运行时发数据
 *
 * 没有后台定位（`ADR-0013` 决策 3）：用户不用这个 App 时就不记录。
 * 这是**明说的范围**，不是疏漏。
 */
interface LocationSource {

    /** 现在有没有定位权限。没有时 [locations] 不会发任何数据。 */
    fun hasPermission(): Boolean

    /**
     * 位置更新流。**热流**：订阅时开始接收，取消时停止。
     *
     * ⚠️ 只发**坐标**，不发"在不在圈里" —— 那是规则的事（`ADR-0013` 决策 2）。
     */
    fun locations(): Flow<GeoPoint>
}
