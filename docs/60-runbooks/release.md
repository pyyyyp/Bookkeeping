# 发布手册

- 最后更新:

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
- [ ] 目标 API 级别符合应用商店最新要求
- [ ] Release 构建体积与上一版本对比无异常增长
- [ ] 关键旅程在真机上手工验证通过

## 签名

| 项 | 位置 | 说明 |
|---|---|---|
| keystore | **不入库**，由密钥管理系统保管 | |
| `keystore.properties` | **不入库**，CI 通过环境变量注入 | |

> 本仓库的 `.gitignore` 已忽略 `*.jks` / `*.keystore` / `keystore.properties`。
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
| | | | |
