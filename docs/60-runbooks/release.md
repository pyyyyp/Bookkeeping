# 发布手册

- 最后更新: 2026-10-03（v0.1.0）

## 版本号规则

SemVer `MAJOR.MINOR.PATCH`，`versionCode` 单调递增。

| 变更 | 版本递增 |
|---|---|
| 不兼容的用户可见变更 | MAJOR |
| 向后兼容的新功能 | MINOR |
| 向后兼容的缺陷修复 | PATCH |

## 发布前检查清单

- [ ] `develop` 上 CI 全绿
- [ ] `docs/90-trace/traceability.md` 中本版本范围内的需求全部为 ✅
- [ ] 无未解决的 P0 缺陷
- [ ] `open-questions.md` 中无影响本版本验收的阻塞项
- [ ] 版本号与 `versionCode` 已更新
- [ ] 更新日志已整理（按 REQ 编号列出用户可见变更）
- [ ] 隐私政策 / 权限说明与实现一致
      ⚠️ 从 `T-030`（`REQ-016` 地理围栏）起，本 App **不再"没有任何权限"**。实际声明的只有：
      `ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION`（**没有**后台定位）。
      发布前对一遍 `app/src/main/AndroidManifest.xml`，并确认版本说明里写了"为什么要位置"，
      以及"App 没打开时不记录"这个范围（`ADR-0013` 决策 3）。
- [ ] 目标 API 级别符合应用商店最新要求
- [ ] Release 构建体积与上一版本对比无异常增长
- [ ] 关键旅程在真机上手工验证通过

## 签名

| 项 | 位置 | 说明 |
|---|---|---|
| keystore | **不入库**，由密钥管理系统保管 | `.tools/keystore/jizhangbao-release.jks`（RSA 4096 / 30 年） |
| `keystore.properties` | **不入库**，CI 通过环境变量注入 | 仓库根；**缺它也能构建**，只是 release 包不签名 |

> ✅ **现状（`T-024`）**：签名**已配置**。有 `keystore.properties` → `assembleRelease` 产出
> **已签名**的 `app-release.apk`；没有它 → 构建**照常通过**，产物是 `app-release-unsigned.apk`，
> 并且**日志里会明说**（静默产出一个看起来没问题的包更糟）。
> 日常开发用 debug 包，不受影响；**干净克隆与 CI 都不需要密钥**。
>
> ⚠️ **签名身份不可更换**：同一个 applicationId 一旦用某个密钥发布，后续版本必须用**同一个**密钥，
> 否则商店与系统都会拒绝覆盖安装。**密钥丢了 = 再也发不出更新** ——
> 请把 `.jks` 搬进密钥管理器并做离线备份（本仓库既没有密钥管理系统，也还没有 CI 注入）。
> 当前密钥指纹（**公开**信息，签名身份靠它辨认）：
> `SHA256 DE:33:A6:14:AA:82:5B:85:DB:85:D1:53:EF:FF:DA:8B:15:74:13:15:B9:D9:70:9B:25:4F:6D:AA:97:66:CE:9B`
>
> 重新配置：`keytool -genkeypair -keystore <新路径>.jks -alias jizhangbao -keyalg RSA -keysize 4096 -validity 10950`
> 再按下面四个键写 `keystore.properties`：`storeFile` / `storePassword` / `keyAlias` / `keyPassword`。

> 本仓库的 `.gitignore` 已忽略 `*.jks` / `*.keystore` / `keystore.properties`
> （`T-024` 用 `git check-ignore -v` 实测确认，两条规则各管一个）。
> **提交密钥后必须轮换，清理历史不能替代轮换。**

## 发布流程

```bash
git switch develop
git switch -c release/x.y.z
# 更新版本号、更新日志
./gradlew bundleRelease
# 上传、灰度、观察
git switch main
git merge --no-ff release/x.y.z
git tag -a vX.Y.Z -m "vX.Y.Z: 覆盖 REQ-001, REQ-004, ..."
```

## 灰度与回滚

| 阶段 | 比例 | 观察时长 | 回滚条件 |
|---|---|---|---|
| 内部测试 | | | |
| 灰度 | 5% | | 崩溃率 > |
| 全量 | 100% | | |

**回滚方式**：应用商店下架新版本 + 发布上一版本；服务端开关降级。

## 发布记录

| 版本 | 日期 | 覆盖需求 | 备注 |
|---|---|---|---|
| v0.1.0 | 2026-10-03 | `REQ-001` ~ `REQ-009`（57 条 AC） | 首个可用版本；自用侧载（未配置签名）。`develop` 上 CI run #59 全绿，追溯矩阵 57/57 ✅。检查清单逐项证据见 `docs/40-tasks/T-020-release-prep.md` |
