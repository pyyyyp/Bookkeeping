# ADR-0002 仓库路径含非 ASCII 字符的处理

- 状态: **提议**（等待用户决策）
- 日期: 2026
- 决策者: 用户 + AndroidDDD-Agent
- 相关: ADR-0001、T-002

## 背景

仓库当前位于 `D:\code\安卓相关`。T-002 首次构建 Android 模块时，AGP 9.4.0 在
**插件应用阶段**直接拒绝：

```
An exception occurred applying plugin request [id: 'com.android.library', version: '9.4.0']
> Failed to apply plugin 'com.android.internal.library'.
   > Your project path contains non-ASCII characters. This will most likely cause
     the build to fail on Windows. Please move your project to a different directory.
     See http://b.android.com/95744 for details. This warning can be disabled by
     adding the line 'android.overridePathCheck=true' to gradle.properties
```

AGP 官方给出的开关存在，但措辞是 **"most likely cause the build to fail"** ——
即 **AGP 自己也不保证绕过之后能用**。

## 实测证据（T-002 过程中收集）

| 现象 | 结果 |
|---|---|
| 加 `android.overridePathCheck=true` 后 AGP 能否应用 | ✅ 能，并打印 experimental 警告 |
| `:core:common:assembleDebug` 能否成功 | ✅ 成功，产出 `common-debug.aar`（1784 字节） |
| `local.properties` 写入含中文的 `sdk.dir` | ❌ **失败**。以 ASCII 编码写出后中文变为 `?`，Gradle 报 `Directory does not exist` |
| Gradle Problems Report URL | ⚠️ 被百分号编码：`file:///D:/code/%E5%AE%89%E5%8D%93%E7%9B%B8%E5%85%B3/...` |
| 环境变量 `ANDROID_HOME` 方式 | ✅ 可用（绕开了 properties 编码问题） |
| aapt2 / aidl / NDK / 资源路径 | ⚠️ **尚未验证**——这是最大的未知风险 |

**证据小结**：开关能让最简模块通过，但**至少已经出现了一个真实故障**
（`local.properties` 编码），并且最危险的环节（aapt2 资源处理、NDK）还没被测到。

## 决策

**建议把仓库迁移到纯 ASCII 路径**，例如 `D:\code\jizhangbao`。

理由不是「AGP 报警了」，而是：**绕过的代价会在每次 AGP 升级、每个新工具链环节
上重复支付，而迁移是一次性的。**

## 备选方案

| 方案 | 优点 | 缺点 | 评估 |
|---|---|---|---|
| **迁移到 ASCII 路径** | 根治；去掉实验性开关；`local.properties` 正常工作；与所有官方模板一致；对开源使用者无差别 | 需移动目录、重开工作区、重设本机 SDK 路径 | **建议** |
| 保留现路径 + `overridePathCheck` | 零迁移成本 | AGP 官方不保证；已出现 properties 编码故障；aapt2/NDK 风险未验证；每次升 AGP 都要重新验证 | 可接受但欠债 |
| 用 `subst` 映射盘符（如 `S:\`） | 不动原目录 | 仅当前会话有效、重启失效；对 CI 与开源使用者不可复现 | 不采用 |
| 改仓库名为英文并保持父目录 | 部分缓解 | 父目录 `D:\code` 是 ASCII，但**完整路径**才是判据，改子目录名即可达成 | **等价于方案一** |

> 注意：真正需要 ASCII 的是**完整路径**。`D:\code\安卓相关` 的问题在最后一段，
> 因此把目录改名为 `D:\code\jizhangbao` 即可，无需换盘或换父目录。

## 后果

**若迁移**

- 正向：消除一整类 Windows 路径问题；`local.properties` 恢复正常；可移除 `gradle.properties` 中的实验性开关
- 负向：需重开会话工作区；`.tools/`（Gradle 151MB + Android SDK 约 1GB）需要一并移动或重新下载
- 影响面：`docs/60-runbooks/build.md` 的本机路径说明、用户的 IDE 配置

**若不迁移**

- 正向：不动目录
- 负向：`gradle.properties` 永久带着一个 experimental 开关；每次 AGP 大版本升级都要复验；
  `local.properties` 只能靠 `ANDROID_HOME` 环境变量替代，与 Android Studio 的默认行为不一致

## 复审条件

- 出现任何 aapt2 / aidl / NDK / 资源处理相关的路径错误 → **立即迁移**，不要再试开关
- 用户决定把项目开源并期望他人能直接 clone 构建 → 迁移（他人路径大概率是 ASCII，但报错信息会让人困惑）
- AGP 后续版本移除该开关 → 必须迁移

## 迁移步骤（若采纳）

```
1. 关闭当前会话与任何占用该目录的进程
2. 把 D:\code\安卓相关 整体重命名为 D:\code\jizhangbao
3. .tools/android-sdk 可留在原处由 ANDROID_HOME 指向，或一并移动
4. 重开工作区，重新生成本机 local.properties
5. 提交：移除 gradle.properties 中的 android.overridePathCheck
6. 更新 docs/60-runbooks/build.md 与 tech-baseline.md 中的路径说明
```
