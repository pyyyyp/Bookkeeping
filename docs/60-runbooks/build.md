# 构建手册

- 最后更新:

## 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | | |
| Android Studio | | |
| Android SDK | | |
| Git | | |

## 本机环境现状（T-001 实测）

| 组件 | 状态 | 位置 |
|---|---|---|
| JDK 21 (21.0.1 LTS) | ✅ 已装 | `D:\program files\java\jdk21` |
| Gradle 9.8.0 | ✅ 已下载到仓库 `.tools/`（**不入库**） | `.tools\gradle-9.8.0\` |
| Android SDK | ✅ 已装（**不入库**） | `.tools\android-sdk\` |
| ├ cmdline-tools | ✅ build 14742923 | `cmdline-tools\latest\` |
| ├ platform | ✅ | `platforms\android-36`（**最高只有 36**） |
| ├ build-tools | ✅ | `build-tools\37.0.0` |
| └ platform-tools (adb) | ✅ | `platform-tools\adb.exe` |
| Android Studio | ❌ 未安装 | — |

> **build-tools 已到 37.0.0，但 platform 最高只有 android-36** → 因此 `compileSdk = 36`。
> build-tools 版本通常由 AGP 自行决定，不必手动指定。

## ⚠️ AGP 9.x 的破坏性变更（T-002 实测踩到）

| 变更 | 症状 | 正确做法 | 状态 |
|---|---|---|---|
| **内置 Kotlin 支持** | 加上 `org.jetbrains.kotlin.android` 插件后构建直接失败：<br>`The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0` | **移除该插件**。Android 模块只声明 `com.android.library` / `com.android.application`。见 [AGP built-in Kotlin](https://kotl.in/gradle/agp-built-in-kotlin) | ⚠️ 持续适用 |
| **路径非 ASCII 检查** | 仓库曾在 `D:\code\安卓相关`，AGP 在插件应用阶段直接拒绝应用 | 仓库已迁移至 `D:\code\Android\jizhangbao`，`android.overridePathCheck` 已移除 | ✅ 已解决 |

> **AGP 8 时代的所有构建模板都不能照抄** —— 这是 ADR-0001 中预设的风险，已实际发生。

## SDK 路径配置

仓库已迁移到纯 ASCII 路径 `D:\code\Android\jizhangbao`，
`local.properties` 恢复正常，与 Android Studio 的默认行为一致：

```properties
sdk.dir=D:/code/Android/jizhangbao/.tools/android-sdk
```

> **为什么用正斜杠**：`.properties` 里反斜杠是转义符，写成 `D:\\code\\...` 也对，
> 但正斜杠更不容易出错，Windows 的 Java 能正确识别。
>
> **历史教训**：迁移前仓库在 `D:\code\安卓相关`，中文路径写进 `local.properties`
> 会因 Java 的 ISO-8859-1 默认编码变成 `?`，Gradle 报 `Directory does not exist`。
> 这是 ADR-0002 建议迁移的实证之一。

环境变量方式同样可用（CI 中更常见）：

```powershell
$env:ANDROID_HOME     = "D:\code\Android\jizhangbao\.tools\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

## 用本地 Gradle 构建（绕过 Wrapper 网络问题）

Gradle Wrapper 首次运行需要从 `services.gradle.org` 下载发行版。
T-001 实测中 **Java 无法连通该地址（Connect timed out），而 PowerShell 可以**——
典型的企业网络/代理差异。此时有两种绕过方式：

```powershell
# 方式一：直接用已下载到 .tools 的本地发行版
.\.tools\gradle-9.8.0\bin\gradle.bat :core:domain:build --console=plain

# 方式二：让 Wrapper 指向本地 zip（仅本机有效，不要把改动提交进仓库）
# 修改 gradle/wrapper/gradle-wrapper.properties 的 distributionUrl 为本地 file:// URI
```

> Wrapper 的 `networkTimeout` 已从 10000ms 提到 60000ms、`retries` 提到 3，
> 以缓解慢网络下的首次下载失败。**这是仓库级配置，不是本机 hack。**

若在公司网络内，建议在 `~/.gradle/init.gradle.kts`（**用户级，不入库**）
配置 Maven 镜像，而不是改项目的 `settings.gradle.kts`——
后者会让开源的使用者也被迫走你的镜像。

## 首次搭建

```bash
git clone <repo>
cd <repo>
./gradlew :core:domain:build     # 首次会下载 Gradle 发行版与依赖
```

`local.properties` 需自行创建（**不入库**）：

```properties
sdk.dir=<你的 Android SDK 路径>
```

## 常用命令

```bash
# 提交前门禁（Android 模块就绪后）
./gradlew detekt lintDebug testDebugUnitTest assembleDebug

# 架构规则（T-003 建立）
./gradlew verifyDomainPurity checkModuleDependencies

# 当前可用（纯 JVM 部分）
./gradlew :core:domain:build
./gradlew :core:domain:test
./gradlew :core:domain:dependencies --configuration compileClasspath   # 核实 R3
```

## 构建变体

| 变体 | 用途 | 说明 |
|---|---|---|
| debug | 日常开发 | 可调试，含日志 |
| release | 发布 | R8 全优化 |

## 构建问题排查

| 现象 | 原因 | 处理 |
|---|---|---|
| `SDK location not found` | 缺 `local.properties` | 创建并填 `sdk.dir` |
| 依赖下载失败 | 网络/镜像 | 配置镜像仓库 |
| 编译内存不足 | 默认堆太小 | `gradle.properties` 调 `org.gradle.jvmargs` |
| 增量构建异常 | 缓存损坏 | `./gradlew clean` 或删 `.gradle/` |

## Mock 服务

<若后端未就绪，此处记录 Mock Server 的启动方式与契约来源。>
