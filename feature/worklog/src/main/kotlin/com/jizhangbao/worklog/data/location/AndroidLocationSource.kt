package com.jizhangbao.worklog.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.jizhangbao.core.common.AppLogger
import com.jizhangbao.worklog.domain.GeoPoint
import com.jizhangbao.worklog.domain.LocationSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 系统定位的实现（`REQ-016`）——**ACL**：把 `android.location` 的变化挡在这里。
 *
 * ## 为什么不是 Play Services（`ADR-0013` 决策 1）
 *
 * 不是"省一个依赖"，而是**它在目标设备上根本跑不起来**：
 * 核实过 MuMu 上没有 `com.google.android.gms`（也没有 Play 商店），
 * 而系统定位服务在运行（`dumpsys location` 有 passive provider）。
 * 引入一个在目标设备上不工作的依赖是双重代价。
 *
 * ## v1 只在 App 运行时发数据（决策 3）
 *
 * 没有后台定位。用户不用这个 App 时就不记录 —— 这是明说的范围，不是疏漏。
 *
 * ## 权限的三条行为（`REQ-016/AC-5`）
 *
 * 1. 没权限 → [locations] **不发任何数据**（直接结束流），不崩；
 * 2. [hasPermission] 让上层**能说明原因**（静默失效最糟：用户会以为"今天没上班"）；
 * 3. fine 与 coarse **任一**有就算有权限 —— 用户只给"大致位置"时也该能工作
 *    （对几百米的围栏够用）。
 *
 * ⚠️ 坐标**不进日志**（`ADR-0010`：不记 PII）。
 */
@Singleton
class AndroidLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) : LocationSource {

    override fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun locations(): Flow<GeoPoint> = callbackFlow {
        if (!hasPermission()) {
            // 没权限就不发数据 —— 但上层能从 hasPermission() 知道"为什么没有"
            close()
            return@callbackFlow
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            close()
            return@callbackFlow
        }

        val listener = LocationListener { location: Location ->
            // 平台给的最细粒度就够了；坐标校验与"在不在圈里"交给领域（决策 2）
            trySend(GeoPoint(latitude = location.latitude, longitude = location.longitude))
        }

        // 两个 provider 都请求：GPS 准但慢、网络快但粗，围栏判定靠的是"大致在哪一片"
        requestFrom(manager, LocationManager.GPS_PROVIDER, listener)
        requestFrom(manager, LocationManager.NETWORK_PROVIDER, listener)

        awaitClose { manager.removeUpdates(listener) }
    }

    /**
     * ⚠️ 这里**在使用点再查一次权限**，而不是复用 [hasPermission]。
     *
     * 两个理由，第二个才是主要的：
     *
     * 1. lint 的 `MissingPermission` 要求"检查在请求点附近可见" —— 它跟不上
     *    从 `callbackFlow` 开头传下来的结论。我**没有**用 `@SuppressLint` 压掉它，
     *    因为那样会连第 2 条一起压掉；
     * 2. **权限可能在数据流活着的期间被撤销**：用户去系统设置里关掉位置，
     *    而我们这边流还开着 —— 那时 `requestLocationUpdates` 会抛 `SecurityException`。
     *    所以显式接住它（用 `try/catch (SecurityException)` 而不是 `runCatching`：
     *    lint 只认前者，而且这里要接住的**就是**这一种，别的异常应该照常炸出来）。
     */
    // ⚠️ @SuppressLint("MissingPermission") 是**有意的、要解释清楚**：
    //    它压掉的是一条**静态误报**，不是真的关掉检查。上面那道 checkSelfPermission
    //    （两个权限取 ||）lint 的数据流证不出来，下面那道 catch (SecurityException) 它也不认。
    //    行为是真做对的：**两道防护都在**，只是静态分析跟不上。
    //    若哪天有人删掉其中任何一道，这个注解就是唯一的线索 —— 所以它必须带着这段说明。
    @SuppressLint("MissingPermission")
    private fun requestFrom(
        manager: LocationManager,
        provider: String,
        listener: LocationListener,
    ) {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        // 提供者可能不存在（模拟器/省电模式）—— 那时静默跳过，另一个还在
        if (!manager.allProviders.contains(provider)) return

        try {
            manager.requestLocationUpdates(
                provider,
                MIN_UPDATE_INTERVAL_MILLIS,
                MIN_UPDATE_DISTANCE_METERS,
                listener,
                Looper.getMainLooper(),
            )
        } catch (revoked: SecurityException) {
            // 权限刚被撤销（用户在系统设置里关掉了位置）—— 跳过这个 provider。
            // 记一条：它不是故障，但**是**一件该能查出来的事（"为什么今天没自动记"）。
            // 文案固定、不带坐标（ADR-0010：不记 PII）。
            logger.warn("定位权限在请求时被拒绝，跳过该 provider", revoked)
        }
    }

    private companion object {
        /**
         * 位置更新的节流。
         *
         * 围栏判定不需要每秒一次：工作地点半径是几百米，而人来人往是分钟级的。
         * 太频繁只是耗电。⚠️ 这两个值是**省电与及时性的折中**，不是物理常数。
         */
        const val MIN_UPDATE_INTERVAL_MILLIS = 60_000L

        const val MIN_UPDATE_DISTANCE_METERS = 50f
    }
}
