# T-002 Android SDK 环境与 Android 模块骨架

- 状态: **进行中**（第一步「验证 AGP 可用性」已完成，其余模块待 ADR-0002 决策后继续）
- 需求: —（工程基础设施）
- 上下文: 全部
- 依赖: T-001
- 分支: `feat/T-002-android-modules`
- 预估: 1 天 | 实际: 进行中

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

- [x] 安装 Android cmdline-tools（build 14742923），接受 licenses
- [x] 安装 `platform-tools` / `platforms;android-36` / `build-tools;37.0.0`
- [x] `:core:common` 模块 + 一个真实引用 Android API 的源文件
- [x] `gradle/libs.versions.toml` 加入 agp / android-library / android-application
- [x] 验证 `:core:common:assembleDebug` 通过
- [x] 记录 AGP 9 破坏性变更与非 ASCII 路径问题
- [ ] 新增 `ADR-0002` 决策（提议中，待用户裁决）
- [ ] `build-logic` 约定插件（现在有 2 个模块，尚未到 6 个的临界点）
- [ ] `:core:ui`（Compose 设计系统）
- [ ] `:core:data`（Room / Retrofit / DataStore）
- [ ] `:core:testing`（Fake / 测试数据构造器）
- [ ] `:app`（Application + Compose 宿主）
- [ ] `:feature:ledger` / `worklog` / `payroll` / `calendar` / `insight`
- [ ] `docs/60-runbooks/build.md` 补齐「他人 clone 后如何构建」

## 验收（分档）

**第一档（已达成）**
- [x] `:core:domain:build` 通过（纯 JVM，14 测试全绿）
- [x] `:core:common:assembleDebug` **BUILD SUCCESSFUL**，产出 `common-debug.aar`
- [x] `:core:common` 是 `com.android.library` 模块，**能** `import android.os.Build` ——
      与 `:core:domain` 形成对照，证明 ADR-0001 决策 4 的分层真实成立

**第二档（待 ADR-0002 决策后）**
- [ ] `./gradlew assembleDebug` 产出 APK
- [ ] `./gradlew testDebugUnitTest` 全绿
- [ ] 真机/模拟器上能启动并显示占位首页

## 风险

| 风险 | 状态 | 对策 |
|---|---|---|
| AGP 9.x 与 Hilt/Konsist/detekt 不兼容 | ⏳ 未验证（尚未引入） | 逐个验证；不兼容的降级并新增 ADR |
| 非 ASCII 路径在 aapt2 / 资源处理阶段出问题 | ⚠️ **未验证，最大未知风险** | ADR-0002；一旦出现即迁移仓库 |
| 仓库路径导致本地配置不可复现 | ✅ 已发生（local.properties 编码） | 用 `ANDROID_HOME` 环境变量绕过 |

## 完成情况

- 提交: 见提交历史
- 未决: **ADR-0002 待用户决策**（是否迁移仓库路径）
- 环境备注: Gradle 9.8.0 与 Android SDK 均在 `.tools/`（已 gitignore）；
  `local.properties` 因中文编码问题**已删除**，改用 `ANDROID_HOME`

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| `feature:*` 业务模块 | 需等 Q-015/Q-016 澄清后才能定聚合 | — | 待 Q 闭环 |
| `build-logic` 约定插件 | 2 个模块时收益仍低于复杂度 | Agent 决策 | 模块数 ≥ 5 时 |
