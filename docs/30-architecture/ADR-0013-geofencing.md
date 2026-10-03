# ADR-0013 地理围栏：用系统定位自己做半径判定，而不是 Play Services

- 状态: **已接受**（2026-10-03，用户拍板"地理围栏要做"）
- 相关: `REQ-016`、`T-030`、`Q-024`、`Q-027`、`ADR-0012`、上下文地图（"定位服务（外部）→ Worklog = ACL"）

## 背景

`Q-024` 问的是"地理围栏做不做"，因为它的代价看起来很高：
**新第三方依赖**（Play Services Location）+ **位置权限**，而 `T-020` 的发布检查清单里
「`AndroidManifest` 里没有任何 `uses-permission`」是**核实过**的一条。

用户 2026-10-03 拍板：**要做**。而在动手前核实了目标设备，得到一个**决定性事实**：

| 查了什么 | 结果 |
|---|---|
| `pm list packages` 里的 `com.google.android.gms` / `com.android.vending` | **都没有** —— 目标设备（MuMu / Android 12）**没有 Google Play 服务** |
| `dumpsys location` | AOSP 的定位服务**在运行**（passive provider 已注册） |

## 决策 1：用 AOSP `LocationManager`，**不引入 Play Services**

理由不是"省一个依赖"，而是**它在那台设备上根本跑不起来**：
没有 GMS 时 `FusedLocationProviderClient` / `GeofencingClient` 会直接失败。
引入一个在目标设备上不工作的依赖，是**双重代价**（体积 + 不可用）。

于是 Q-024 原本担心的"新依赖"这条**自己消失了** —— 剩下真正要付的代价只有**权限**。

## 决策 2：半径判定自己算（纯函数），不用系统/第三方的围栏 API

- `LocationManager.addProximityAlert` 在 API 29 已废弃；
- 更重要的是：**判定"在不在圈里"是一条规则，规则应当是可单测的纯函数**。
  自己算意味着它跑在 JVM 上、有边界用例（正好在半径上、跨经线、极地附近），
  而不是依赖某个 API 的黑盒行为。

这与 `ADR-0012` 决策 2（门槛归 Payroll）同一条思路：**规则归自己，平台调用只是取数**。

用 Haversine 公式算两点球面距离。⚠️ 它假设地球是球体（误差约 0.3%），
对"几百米的工作地点半径"完全够用 —— 这不是精度妥协，而是**与需求匹配的精度**。

## 决策 3：v1 **只在 App 运行时**记录，不申请后台定位

`ACCESS_BACKGROUND_LOCATION` 是这套权限里最重的一个（要单独申请与说明，且用户很难理解），
而本 App 是**自用侧载**。v1 只在 App 运行时接收定位更新 —— 用户不用它时就不记录。

**代价明说**：App 没打开时不会自动记工时。这不是"以后再说"，是 v1 的**明确范围**，
写进 `REQ-016`。

## 决策 4：系统**只记录**，人**确认**（`RUNNING → FINISHED → CONFIRMED`）

围栏**不是**"在上班"的证据：路过公司、楼下买咖啡都会触发。
而钱是按"哪天算加班"算的（差一倍），所以**不能**让围栏直接把工时算成事实。

好在 `T-027` 已经把这个结构性约束建好了：`AttendedDay` 只认 **`CONFIRMED`** 的时段
（`REQ-014/AC-2`）。于是这里的规则是：

| 事件 | 动作 |
|---|---|
| 进入围栏 | 建一段 `RUNNING` 工时 |
| 离开围栏 | `finish()` → `FINISHED`（**还不是事实**） |
| 用户确认 | `confirm()` → `CONFIRMED`（**这时才算数**） |

**自动记录 + 人工确认** —— 既不用手填起止，又不让定位误差直接影响钱。

## 决策 5：离开不足 10 分钟不算离开（`Q-027`）

拿快递、GPS 漂移都会造成"出门又回来"。若立刻结束并新建一段，一天会被切成十几段，
用户还得逐段确认 —— 那比手填还累。

`GeofenceRules.EXIT_DEBOUNCE = 10 分钟`：离开时长不超过它时，**视为同一段连续工作**。
它是一个具名常量，改一处即可（`Q-027` 已记录，可推翻）。

## 影响

- `:app` 的 `AndroidManifest`：新增 **位置权限**（`ACCESS_FINE_LOCATION` +
  `ACCESS_COARSE_LOCATION`）—— **"这个 App 没有任何权限"这句话从此不再成立**，
  `docs/60-runbooks/release.md` 的检查清单要跟着改
- `feature:worklog`：`Workplace`（地点）、`GeoPoint`、`GeofenceMath`（纯函数）、
  `GeofenceRules`（状态迁移 + 抖动处理）、`LocationSource`（ACL 端口）+ 系统实现
- **隐私**：坐标只落在本机数据库，**不进日志**（`ADR-0010`：不记 PII），不上传（无后端）
- 仍然**没有**后台定位，也**没有**任何第三方依赖
