# T-010 补界面层与数据层的自动化测试

- 状态: **已完成**（界面层 13 条 + 数据层 7 条，都有反向验证或真库证据）
- 需求: `REQ-001`（补验证，不新增功能）
- 上下文: Ledger
- 影响聚合: 无（只加测试）
- 依赖: `T-007`、`T-008`
- 分支: `refactor/T-010-test-gaps`
  （本来想用 `test/`，但 `jizhangbao/AGENTS.md` 第 6 节的允许集合是 `feat|fix|docs|refactor|spike`。
  规则优先于个人偏好（第 0 节第 6/7 条），所以改用 `refactor/`——本卡只加测试、不改生产行为。
  「该不该给规则补一个 `test/`」是规则变更，需要单独提，本卡不顺手改。）
- 预估: 0.5 轮 | 实际:

> **这是一张工程卡，不是需求卡**：它不改变任何用户可见行为，只把此前靠手工冒烟覆盖的两层
> 补上自动化。触发原因是 `T-007`/`T-008` 收尾时如实记录的缺口。

## 目标

把"已知缺口"里最贵的两个补掉：

1. **`LedgerViewModel` 没有任何自动化测试**（`T-007`/`T-008` 只靠模拟器冒烟）——
   它里面有「保存失败时要不要清空输入框」「切方向后原来的分类留不留」「删除失败后确认框留不留」
   这类**编排决定**，写错了会丢用户数据或造成重复提交。
2. **DAO 的 SQL 没有自动化测试**（单元测试用的是内存 fake）——
   `ORDER BY` 的次序与"受影响 0 行"这类行为只有真数据库能验证。

## 变更清单

- [x] 引入 `kotlinx-coroutines-test`（**版本按生产实际解析值对齐**，见 `libs.versions.toml` 的说明）
- [x] `LedgerViewModelTest`：13 条，覆盖初始状态 / 载入 / 保存成功 / 四类失败 / 方向切换 /
      删除的请求-取消-确认-失败
- [x] **反向验证**：故意让 ViewModel 在存储失败时清空金额 → **恰好那一条**测试失败
      （其余 12 条通过）→ 还原
- [x] 数据层：`LedgerEntryDaoTest`（7 条，**真 SQLite**）——排序（含第二排序键）、`LIMIT`、
      删除影响行数 1 / 0、只删目标行。测试专用 `@Database` 住 `androidTest`（生产那个在 `:app`）
- [x] 新依赖的版本**核实过**（Google Maven 的 `maven-metadata.xml`）：
      `androidx.test.ext:junit` **1.3.0**、`androidx.test:runner` **1.7.0**

## 数据层测试：路线与实测结论

**选了路线 A（仪器化测试）**，因为本机有可用模拟器，且不引入"测试专用运行时"这层间接。

⚠️ **但 `./gradlew connectedDebugAndroidTest` 在本机（MuMu / Android 12）跑不通**：
报 `There were failing tests`，而结果 XML 里 `tests=` 是空的、HTML 报告里没有任何失败详情。
查下来的真相是 **测试 APK 根本没被安装**（`adb shell pm list packages` 里只有 `com.jizhangbao.app`）——
不是测试失败，是没跑起来。手动安装则完全正常：

```powershell
# 1) 先构建出测试 APK
.\gradlew.bat :feature:ledger:assembleDebugAndroidTest
# 2) 手动安装（-t 是必须的：测试 APK 是 test-only 包）
adb install -r -t feature\ledger\build\outputs\apk\androidTest\debug\ledger-debug-androidTest.apk
# 3) 直接跑 instrumentation（结果打印到 stdout，不经过 Gradle 的上报层）
adb shell am instrument -w -e class com.jizhangbao.ledger.data.local.LedgerEntryDaoTest `
    com.jizhangbao.ledger.test/androidx.test.runner.AndroidJUnitRunner
# → com.jizhangbao.ledger.data.local.LedgerEntryDaoTest:.......
#   OK (7 tests)
```

**另一条实测结论**：方法名**必须是 ASCII**。用中文方法名时，AGP 的上报层会在
`CompositeTestExecutionListener.executionFinished` 抛 `ArrayIndexOutOfBoundsException:
Index 1 out of bounds for length 0`，同样是"空结果 + 退出码 1"。
（已核实 `debugAndroidTestRuntimeClasspath` 上只有 JUnit **4.13.2**，没有 JUnit 5。）

**因此 CI 里没有这一步**：runner 上没有模拟器；即便有，也要先解决上面这个安装问题。
这是**已知缺口**，不是"忘了加"。

## 验收

- [x] `:feature:ledger:testDebugUnitTest` 全绿：**74 个 Ledger 测试**（全仓 80 = 74 + 6 架构断言）
- [x] 反向验证：注入违规时对应测试真的失败
- [x] 数据层：`OK (7 tests)`（真库，经 `adb shell am instrument` 运行，见上方命令）

## 完成情况

- 提交: 见 `90-trace/traceability.md` 的 T-010 行
- 证据：JVM 侧 80 个测试 0 失败；仪器化侧 7 个测试 `OK`；全仓门禁（detekt / lint /
  testDebugUnitTest / assembleDebug / 两条架构校验）全绿
- 已自行决定并记录：分支类型用 `refactor/`（规则里没有 `test/`，见文件头的说明）
- 未决（**需要用户裁决**）：`connectedDebugAndroidTest` 装不上测试 APK 要不要深挖
  （可能是 MuMu 的 test-only 安装限制），以及要不要给 CI 加模拟器作业
- 备注: 测试只加不算数，**必须反向验证过**才算证据（本卡两处都做了）。
  没有反向验证的测试，与没有测试的区别只是心理安慰
