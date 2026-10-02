# 技术基线

- 状态: **待填写**（首次启动问卷后由 Agent 建立）
- 最后更新:

> 本文件记录**版本与选型的权威值**。`gradle/libs.versions.toml` 是唯一的机器可读版本来源；
> 本文件说明**为什么选它**。更换任何一项都必须新增 ADR。

## 版本

| 项 | 版本 | 说明 |
|---|---|---|
| JDK | | 编译与运行 |
| Kotlin | | |
| AGP | | |
| Gradle | | |
| compileSdk | | |
| targetSdk | | |
| minSdk | | 影响可用 API 与用户覆盖 |
| Compose BOM | | 若使用 Compose |

## 选型

| 关注点 | 选型 | 备选 | 决策 ADR |
|---|---|---|---|
| UI | Jetpack Compose / XML | | |
| 依赖注入 | Hilt / Koin / 手写 | | |
| 网络 | Retrofit / Ktor | | |
| 本地存储 | Room / DataStore / SQLDelight | | |
| 序列化 | kotlinx.serialization / Moshi / Gson | | |
| 图片 | Coil / Glide | | |
| 异步 | Coroutines + Flow | | |
| 日志 | Timber / 自研 | | |
| 构建约定 | build-logic 约定插件 | | |
| 静态检查 | detekt / ktlint | | |
| 架构校验 | Konsist / 自定义 Gradle 任务 | | |

## 模块命名空间

| 模块 | namespace / 包名 |
|---|---|
| `:app` | |
| `:core:domain` | |
| `:core:ui` | |
| `:core:data` | |
| `:feature:<context>` | |

## 依赖准入规则

新增任何第三方依赖前必须确认：

- [ ] 新增 ADR 说明理由与被否方案
- [ ] 检查许可证（是否与项目分发方式兼容）
- [ ] 评估包体积影响（记录 Release 体积变化）
- [ ] 确认是否有可替代的平台原生能力
- [ ] 记录到 `libs.versions.toml`，禁止在模块里写死版本号

## 工具链要求

| 工具 | 版本 | 用途 |
|---|---|---|
| Android Studio | | |
| JDK | | |
| Git | | |
| detekt | | |

## 构建命令速查

```bash
./gradlew detekt lintDebug testDebugUnitTest assembleDebug   # 提交前门禁
./gradlew verifyDomainPurity checkModuleDependencies         # 架构规则
./gradlew :feature:<context>:test                            # 单模块测试
./gradlew :app:assembleRelease                              # 发布构建
```
