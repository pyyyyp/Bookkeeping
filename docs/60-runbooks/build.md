# 构建手册

- 最后更新:

## 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | | |
| Android Studio | | |
| Android SDK | | |
| Git | | |

## 首次搭建

```bash
git clone <repo>
cd <repo>
./gradlew assembleDebug     # 首次会下载依赖
```

`local.properties` 需自行创建（**不入库**）：

```properties
sdk.dir=<你的 Android SDK 路径>
```

## 常用命令

```bash
# 提交前门禁（必须全绿）
./gradlew detekt lintDebug testDebugUnitTest assembleDebug

# 架构规则
./gradlew verifyDomainPurity checkModuleDependencies

# 单模块
./gradlew :feature:<context>:test
./gradlew :feature:<context>:assembleDebug

# 清理
./gradlew clean
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
