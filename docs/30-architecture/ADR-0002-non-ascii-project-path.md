# ADR-0002 仓库路径含非 ASCII 字符的处理

- 状态: **已接受并已实施**
- 日期: 2026
- 决策者: 用户 + AndroidDDD-Agent
- 相关: ADR-0001、T-002

> **实施结果（已验证）**：仓库已迁移至 `D:\code\Android\jizhangbao`，
> 全新构建（清空 `build/` 与 `.gradle/`）通过，`android.overridePathCheck` 已移除。
> 详见文末「迁移后记」。

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

## 迁移步骤（实际执行）

```
1. ✅ 创建 D:\code\Android
2. ✅ 同卷移动仓库到 D:\code\Android\jizhangbao
3. ✅ 一并移动 .tools（Gradle 9.8.0 + Android SDK，约 1.0 GB）
4. ✅ 重建 local.properties，改用正斜杠的 ASCII 路径
5. ✅ 移除 gradle.properties 中的 android.overridePathCheck
6. ✅ 更新 build.md 与 task 卡中的路径说明
7. ✅ 删除旧目录 D:\code\安卓相关
```

## 迁移后记（实测记录）

**迁移过程本身踩了一个坑，值得记录。**

`Move-Item` 在首次执行时于 `.git` 上失败（权限拒绝），**留下了部分移动的中间状态**：
根文件与 `.git` 已到新位置，而 `core/` `docs/` `gradle/` `.tools/` 还在旧位置。

随后补搬 `.tools/gradle-9.8.0` 时，因为新位置**已存在同名目录**，
`Move-Item` 把它**嵌套**成了 `.tools/gradle-9.8.0/gradle-9.8.0/`，导致工具链失效
（`gradle --version` 退出码 1）。

**处理方式**：没有去解开部分移动的乱麻，而是**从 `gradle-9.8.0-bin.zip` 重新解压**。
理由是确定性——重新解压的结果是可预测的，而修补嵌套目录要依赖对中间状态的猜测。

**教训**：在同一卷上移动大目录时，`Move-Item` 遇到锁会产生部分移动。
更稳的做法是 `robocopy /MOVE`（可重试、可续传），或先复制后校验再删源。

**迁移后的验证结果**

| 项 | 迁移前 | 迁移后 |
|---|---|---|
| AGP 路径检查 | ❌ 需实验性开关 | ✅ 不再需要 |
| `local.properties` | ❌ 中文变 `?`，报目录不存在 | ✅ 正常读取 |
| Gradle Problems Report URL | `file:///D:/code/%E5%AE%89%E5%8D%93%E7%9B%B8%E5%85%B3/...` | `file:///D:/code/Android/jizhangbao/...` |
| `:core:domain:build` | ✅ | ✅（清空 `build/` 与 `.gradle/` 后全新构建） |
| `:core:common:assembleDebug` | ✅ | ✅ 产出 `common-debug.aar` |
| 领域层测试 | 14 全绿 | 14 全绿 |

> 全新构建**不带 `ANDROID_HOME` 环境变量**、只靠 `local.properties` 通过，
> 说明配置已回到 Android 的标准路径上，不再依赖任何本机 hack。
