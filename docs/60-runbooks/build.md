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
| Gradle | ⚠️ 未全局安装，已下载到仓库 `.tools/`（**不入库**） | `.tools\gradle-9.8.0\` |
| Android SDK | ❌ **未安装** | 无 `ANDROID_HOME` |
| Android Studio | ❌ 未安装 | — |

**当前能构建什么**：只有 `:core:domain`（纯 Kotlin JVM）。
**当前不能构建什么**：任何 Android 模块——AGP 需要 Android SDK，见 T-002。

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
