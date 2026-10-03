# 参与开发（CONTRIBUTING）

> **规则不在这里。** 本文件只讲「怎么把这件事跑起来」。
> 操作规则（A→F 工作循环、R1–R12 依赖规则、Git 协议、18 条禁令）在 **`AGENTS.md`**，
> 项目事实在 **`docs/`**。两者冲突时以它们为准。

---

## 1. 环境

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **21** | `JAVA_HOME` 指向它 |
| Android SDK | 至少含 `platforms;android-37.0`、`build-tools;37.0.0`、`platform-tools` | compileSdk 37 由 Compose BOM 的下限决定，见 `docs/30-architecture/ADR-0003` |
| Gradle | **不用单独装** | 用仓库自带的 Wrapper（`gradlew`） |

`local.properties`（**不入库**，需自建）：

```properties
sdk.dir=<你的 Android SDK 路径>
```

装 SDK 组件：

```bash
sdkmanager --install "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
```

---

## 2. 本地门禁 = CI 门禁

CI（`.github/workflows/ci.yml`）跑的就是下面这几条，**顺序也一样**。
本地全绿而 CI 红，基本上只可能是环境差异（比如 SDK 组件没装齐）。

> **Windows**：下面写的是 `./gradlew`（bash 写法），在 PowerShell / cmd 里换成
> `.\gradlew.bat`。两者等价，用的是同一个 Wrapper。

```bash
./gradlew detekt                        # 静态分析（含风格与代码味道）
./gradlew lintDebug                     # Android Lint
./gradlew verifyDomainPurity checkModuleDependencies   # 架构规则 R2/R3/R7/R8
./gradlew testDebugUnitTest             # 单元测试 + Konsist 架构断言
./gradlew assembleDebug                 # 构建 APK
pwsh ./scripts/verify-traceability.ps1  # 追溯矩阵校验
```

> `verifyDomainPurity` 与 `checkModuleDependencies` 也接在每个模块的 `check` 上，
> 所以 `./gradlew build` 会自动带上它们——但 CI 里显式列出，是为了**失败时一眼看出是哪一类问题**。
>
> Windows 上如果 `pwsh` 不在 PATH：用 `powershell -File scripts\verify-traceability.ps1`。
> 脚本必须保存为 **UTF-8 with BOM**：Windows PowerShell 5.1 会把没有 BOM 的 `.ps1` 当 ANSI 读，
> 里面的中文与 emoji 会直接让脚本解析失败。

---

## 3. 追溯矩阵校验

`scripts/verify-traceability.ps1` 检查 `docs/90-trace/traceability.md` 与 `docs/` 是否自洽：

| 规则 | 内容 |
|---|---|
| 规则 1 | 矩阵里出现的每个 `REQ-xxx` 都要有对应文件 |
| 规则 2 | 每个 `REQ-*.md` 里的每条 `AC-x` 都要在矩阵的「需求 → 实现」表里登记 |
| 规则 3 | 矩阵里标 ✅ 的行，必须有非空的「提交」与「测试」列 |
| 规则 4 | 矩阵里出现的每个 `T-xxx` / `ADR-xxxx` 都要有对应文件 |
| 规则 0 | **如果一条编号都没解析到，直接判失败** —— 防止校验自己悄悄失效 |

失败时它会逐条打印 `规则号 + 文件:行号 + 说明`，照着修即可。

两个容易被误判的点：

- HTML 注释块 `<!-- ... -->` 里的内容是**格式示例**，不参与校验；
- `REQ-00x`、`T-00x` 这类**占位写法**不是引用，不要求存在对应文件。

---

## 4. 提交之前

1. 按 `AGENTS.md` 第 8 节的清单自查。
2. 提交信息按 `AGENTS.md` 第 6 节的格式（含 `Refs` / `Domain`）。
3. `git add <逐个文件>`，**不要** `git add .`。
4. 任何提交都必须至少触及一行文档——纯代码提交说明跳过了阶段 B。

---

## 5. ⚠️ 本文件的已知未验证项

CI 流水线**尚未在真实 runner 上跑过**（仓库还没有 GitHub 远端）。已经验证的是：
YAML 语法、每条命令在本机的等价可用性、追溯脚本的 5 个失败场景。
未验证的是：ubuntu runner 上 `android-actions/setup-android` 能否提供
`platforms;android-37.0`。首次推送后请核对日志并回填 `docs/40-tasks/T-004-ci-pipeline.md`。
