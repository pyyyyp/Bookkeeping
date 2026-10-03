# T-002 Android SDK 环境与 Android 模块骨架

- 状态: **已完成**（2026-10-02。两档全部达成，含模拟器启动验证）
- 需求: —（工程基础设施）
- 上下文: 全部
- 依赖: T-001
- 分支: `feat/T-002-android-modules`
- 预估: 1 天 | 实际: 第二档约 1 轮（含 5 次构建迭代与 1 次仓库外验证工程）
- 相关决策: `ADR-0002`（路径迁移）、**`ADR-0003`（AGP 9 构建基线）**

## 目标

让 `./gradlew assembleDebug` 真实通过：装上 Android SDK，并把 Android 侧模块骨架建起来。

## 第一步（已完成）：先在一个最小模块上验证工具链

**决策**：没有一次性创建 11 个模块，而是先建一个 5 行的 `:core:common` 并跑通构建。

**理由**：AGP 8 → 9 是大版本变更（ADR-0001 已标记该风险）。
若一次性建 11 个模块后才发现 DSL 变了，11 个文件都要改，且错误信息被淹没。
在最小模块上验证，错误信息是干净的、定位是唯一的。

**这一步用掉了两次失败，每次都是真信息**（见下）。

## 关键发现

### 发现 1：AGP 9.0+ 内置 Kotlin 支持（破坏性变更）

加上 `org.jetbrains.kotlin.android` 插件直接构建失败：

```
The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.
Solution: Remove the 'org.jetbrains.kotlin.android' plugin from this project's build file
```

**AGP 8 时代的所有构建模板都不能照抄。** 已记入 `docs/60-runbooks/build.md`。

### 发现 2：仓库路径含非 ASCII 字符（AGP 直接拒绝）—— ✅ 已解决

`D:\code\安卓相关` 触发 AGP 的路径检查，插件应用阶段即失败。
实验性开关 `android.overridePathCheck=true` 能让最简模块通过，
但已产生一个真实次生故障：中文路径写入 `local.properties` 后变 `?`，Gradle 报目录不存在。

**处置**：新增 `ADR-0002` 并**已实施迁移**。仓库现位于 **`D:\code\Android\jizhangbao`**，
实验性开关已移除，全新构建（清空 `build/` 与 `.gradle/`）通过，
`local.properties` 恢复正常，Gradle 报告 URL 也不再被百分号编码。

**迁移过程本身踩了一个坑**：`Move-Item` 在 `.git` 上失败留下部分移动状态，
补搬时又因目标已存在而把 `gradle-9.8.0` 嵌套成 `gradle-9.8.0\gradle-9.8.0`，导致工具链失效。
最终从 zip 重新解压解决。完整记录见 `ADR-0002` 的「迁移后记」。

## 变更清单

**第一档（工具链验证）**

- [x] 安装 Android cmdline-tools（build 14742923），接受 licenses
- [x] 安装 `platform-tools` / `platforms;android-36` / `build-tools;37.0.0`
- [x] `:core:common` 模块 + 一个真实引用 Android API 的源文件
- [x] `gradle/libs.versions.toml` 加入 agp / android-library / android-application
- [x] 验证 `:core:common:assembleDebug` 通过
- [x] 记录 AGP 9 破坏性变更与非 ASCII 路径问题
- [x] 新增 `ADR-0002` 决策并实施仓库迁移

**第二档（模块骨架与 APK）**

- [x] 核实全部待锁定版本（Compose BOM / Hilt / Room / KSP / coroutines / Retrofit / Coil / datastore / navigation / detekt / Konsist），回填 `tech-baseline.md`
- [x] 补装 `platforms;android-37.0`（Compose BOM 2026.09 要求 compileSdk ≥ 37）
- [x] 引入 `build-logic` 约定插件（`jizhangbao.android.library` / `jizhangbao.android.application`）
- [x] `:core:ui`（Compose 设计系统，Material 3 默认色板 + `TODO(Q-020)`）
- [x] `:core:data`（Room / KSP 装配完成；**不含 `@Database`**，理由见其构建脚本）
- [x] `:core:testing`（模块骨架；**暂无源文件**，理由见其构建脚本）
- [x] `:app`（`@HiltAndroidApp` + `MainActivity` + 占位首页 + 资源）
- [x] `:feature:ledger` / `worklog` / `payroll` / `calendar` / `insight` 五个上下文模块骨架
- [x] 新增 `ADR-0003` 记录 AGP 9 构建基线决策
- [x] `docs/60-runbooks/build.md` 补齐「他人 clone 后如何构建」——**已补**（含 SDK 安装、约定插件说明、Room 验证配方）
- [x] 启动验证（MuMu 模拟器，Android 12 / API 32）—— 已装、已启动、已截图

## 第二档执行记录（2026-10-02）

按「先在小面积上验证、再放大」的同一原则，本轮把风险逐个隔离后验证，而不是一次性建 11 个模块：

| 步骤 | 做法 | 结果 |
|---|---|---|
| 1 | 版本核实 | 全部版本改为读官方仓库 `maven-metadata.xml`，不靠记忆。发现 **KSP 已改独立版本线**、detekt 2.x 仍只有 alpha |
| 2 | 只建 `:core:ui` 并构建 | 先撞上 `compileSdk` 不足（Compose 2026.09 要求 ≥ 37）→ 补装 `android-37.0`；随后 `compileDebugKotlin` 通过，**Compose 在 AGP 9 内置 Kotlin 下可用** |
| 3 | 只建 `:core:data` 并构建 | KSP 任务正常执行、处理器正常加载，但 Room 报 `@Database annotation must specify list of entities` → **不在仓库里编造表结构**，改为在仓库外一次性工程验证 Room 代码生成 |
| 4 | 构建 `:app` | Hilt 的 `hiltAggregateDepsDebug` / `transformDebugClassesWithAsm` 正常执行，**产出 APK** |
| 5 | 全量 `assembleDebug lintDebug` + 测试 | 11 模块 423 个任务通过；lint 0 error / 2 warning；领域层 14 测试 0 失败 |

**过程中修正的两处旧记载**（都属于「文档说的不是事实」）：

1. 「SDK 里 platform 最高只有 36」→ 实际是**当时没装**，`sdkmanager --list` 里有 `android-37.0/37.1/37.2`。
2. 「Java 连不通 `services.gradle.org`，只能用本地 Gradle」→ 本轮 `.\gradlew.bat --version` 成功下载发行版，**Wrapper 已恢复可用**。

## 验收（分档）

**第一档（已达成）**
- [x] `:core:domain:build` 通过（纯 JVM，14 测试全绿）
- [x] `:core:common:assembleDebug` **BUILD SUCCESSFUL**，产出 `common-debug.aar`
- [x] `:core:common` 是 `com.android.library` 模块，**能** `import android.os.Build` ——
      与 `:core:domain` 形成对照，证明 ADR-0001 决策 4 的分层真实成立

**第二档（已完成）**

- [x] `./gradlew assembleDebug` 产出 APK —— **验证: 自动化**，`app-debug.apk` 11.65 MB，
      `aapt2 dump badging` 显示 `package=com.jizhangbao.app` `versionCode=1` `versionName=0.1.0`
      `minSdk=26` `targetSdk=36` `compileSdk=37` `launchable-activity=com.jizhangbao.app.MainActivity`
      `application-label=记账宝`
- [x] `./gradlew testDebugUnitTest` 全绿 —— **验证: 自动化**，`:core:domain:test` 强制重跑
      （`--rerun`，不吃 UP-TO-DATE）：**14 tests / 0 failures / 0 errors**。
      Android 侧 `:app:testDebugUnitTest` 为 `NO-SOURCE`（尚无测试），**这不算通过，只是没有测试**
- [x] `./gradlew assembleDebug lintDebug` —— **验证: 自动化**，BUILD SUCCESSFUL；
      lint **0 error / 2 warning**（`MissingApplicationIcon`、`DataExtractionRules`）
- [x] 11 个模块全部独立构建通过（`:core:domain` + 4 个 `:core:*` + 5 个 `:feature:*` + `:app`）
- [x] `adb install -r` 到已连接的设备，启动 `MainActivity` 并显示占位首页 —— **验证: 手工（有截图）**
      `mCurrentFocus=com.jizhangbao.app/com.jizhangbao.app.MainActivity`，logcat 无 FATAL；
      截图确认渲染出「记账宝 / 工程骨架已就绪，界面待实现」，Compose 与边缘到边缘均正常
- [x] 11 个模块全部独立构建通过（`:core:domain` + 4 个 `:core:*` + 5 个 `:feature:*` + `:app`）

> **关于那台设备（重要更正）**：`adb` 报的属性是 `HUAWEI HBP-AL00`，但 logcat 里能看到
> `product: YXArkNights-12.0`、`engine: NEMUX`、`package: mumu`、x86_64 宿主 + arm64 原生桥，
> 即**这是一台 MuMu 模拟器**（伪装成华为机型），Android 12 / API 32。
> 任务卡的验收写的是「真机**或**模拟器」，因此这条成立；
> 但**不能**因此声称「已在真机上验证过」——真机（尤其是华为的权限与后台策略）仍未验证。

## 风险

| 风险 | 状态 | 对策 |
|---|---|---|
| AGP 9.x 与 Hilt/Konsist/detekt 不兼容 | ✅ Hilt 2.60.1 已实测通过 | Konsist / detekt 仍未验证，归 T-003；不兼容时按 ADR-0001 降级并**新增 ADR** |
| 非 ASCII 路径在 aapt2 / 资源处理阶段出问题 | ✅ 已排除 | 仓库在纯 ASCII 路径，全模块资源处理通过 |
| 仓库路径导致本地配置不可复现 | ✅ 已解决 | `local.properties` 正常；文档给出 `ANDROID_HOME` 方式 |
| **Kotlin 版本静默退回 2.2.10** | ⚠️ **新发现的结构性隐患** | 全靠根 `build.gradle.kts` 里的 Kotlin 插件别名撑着；删掉即静默退化。核实命令与说明已写入 `build.md`，见 ADR-0003 决策 2 |
| `targetSdk (36) < compileSdk (37)` 的欠债 | ⚠️ 已知、刻意 | 本机无可复现测试环境，不单方面改运行时行为；复审条件见 ADR-0003 |
| `:core:data` / `:core:testing` 是空模块 | ⚠️ 已知、刻意 | 内容必须由真实聚合倒推；Room 可用性已在仓外验证 |

## 完成情况

- 提交: `0d844b3` `3616f3a` `d4fdb23`（第一档）、`d6040b1` `32c71be` `5896ae4` `9e25711`（第二档）
- **状态: 已完成**，本分支合并回 `develop`
- 未决（已移交，不阻塞本卡）:
  1. `detekt` / `Konsist` 的 AGP 9 兼容性 —— 归 **T-003**
  2. Q-020 视觉设计（`:core:ui` 现用 Material 3 默认色板，带 `TODO(Q-020)` 单点降级）
  3. 真机（非模拟器）验证 —— 本卡只在 MuMu 模拟器上验证过启动；
     华为真机的后台/权限策略是另一类风险，等有需要时再验
- 环境备注: Gradle 9.8.0 与 Android SDK 均在 `.tools/`（已 gitignore）；
  需要 `platforms;android-37.0`（不再是 36）；Wrapper 已恢复可用

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| `feature:*` 的业务逻辑（聚合 / 用例 / 界面） | 聚合需等 Q-015 / Q-016 等澄清后才能定；**骨架已建，内容不猜** | — | 各上下文的任务卡 |
| `:core:data` 的 `@Database` 与实体 | Room 拒绝空 `entities`；实体必须由真实聚合倒推（R6） | Agent 决策 | 第一个数据层任务卡 |
| `:core:testing` 的内容 | Fake / 测试数据构造器必须由真实领域类型倒推 | Agent 决策 | 第一个领域测试任务卡 |
| `:core:ui` 的品牌色板 | 视觉设计未定（Q-020）；用 Material 3 默认值 + 单点 `TODO(Q-020)` 降级 | — | Q-020 定案后 |
| Retrofit / OkHttp / Coil / DataStore / navigation | 版本已核实但**无消费者**，引入只会增加攻击面与体积 | Agent 决策 | 各自第一个真实需求 |
| 真机启动验证 | 安装到用户个人手机属对用户设备的副作用 | — | 用户确认后 |
| detekt / Konsist | 属 T-003 范围（架构规则机器强制） | — | T-003 |
