# T-002 Android SDK 环境与 Android 模块骨架

- 状态: 待开始
- 需求: —（工程基础设施）
- 上下文: 全部
- 依赖: T-001
- 分支: `feat/T-002-android-modules`
- 预估: 1 天

## 目标

让 `./gradlew assembleDebug` 真实通过：装上 Android SDK，并把 Android 侧模块骨架建起来。

## 前置问题（开工前必须解决）

| 编号 | 问题 | 影响 |
|---|---|---|
| **Q-007 衍生** | 本机无 Android Studio，SDK 需要下载（cmdline-tools + platform + build-tools，约 500MB–1GB）。**是否允许下载？** | 不解决则无法验证任何 Android 模块 |
| 版本核实 | `compileSdk` / `targetSdk` / `build-tools` 版本需按 AGP 9.4.0 要求核实 | 导致构建失败 |

## 变更清单

- [ ] 安装 Android cmdline-tools，接受 licenses
- [ ] 设置 `ANDROID_HOME`（**只写进本机环境变量与 `local.properties`，不入库**）
- [ ] `local.properties`（加入 `.gitignore` 复核）
- [ ] 约定插件 `android.library.convention`、`android.application.convention`、`android.hilt.convention`
- [ ] `settings.gradle.kts` 加入 Android 模块
- [ ] `:core:common`（Android Library）
- [ ] `:core:ui`（Android Library + 设计系统雏形）
- [ ] `:core:data`（Android Library）
- [ ] `:core:testing`（Android Library）
- [ ] `:app`（Application + Compose 宿主）
- [ ] `docs/60-runbooks/build.md` 补齐「他人 clone 后如何构建」——开源必需

## 验收

- [ ] `./gradlew assembleDebug` 成功产出 APK
- [ ] `./gradlew detekt lintDebug` 无新增问题
- [ ] `./gradlew testDebugUnitTest` 全绿
- [ ] 在真机/模拟器上能启动并显示一个占位首页
- [ ] README 中的构建步骤在干净环境下可复现

## 风险

| 风险 | 对策 |
|---|---|
| AGP 9.x 与 Hilt/Konsist/detekt 不兼容 | 按 ADR-0001 的预设回退规则降级 AGP 并新增 ADR |
| SDK 下载耗时长 | 后台任务执行，先写构建文件 |
| `targetSdk` 与最新上架要求不符 | 本项目不上架，取 AGP 9.4.0 支持的稳定值即可 |

## 完成情况

- 提交:
- 未决:

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| `feature:*` 业务模块 | 需等 Q-012/Q-004 澄清后才能定聚合，见 T-003 | | |
