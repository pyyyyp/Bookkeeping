# T-024 签名与 release 打包

- 状态: **已完成**（签名有效 + 无密钥时构建照常通过，两条都实测）
- 类型: **发布能力**（不改任何业务行为，因此**不新立需求**；`T-020` 的检查清单里写着"要上架时签名是必须先做的独立任务"，本卡就是那一件）
- 需求: 无新增；服务 `docs/60-runbooks/release.md` 的发布流程
- 分支: `feat/T-024-signing`
- 预估: 0.5 轮 | 实际:

> **一个任务 = 一个分支 = 一组内聚提交**，且必须能独立构建通过。

## 动机

`T-020`（v0.1.0 发布）时如实标注过一件事：**签名配置不存在**，
所以发布手册里的「上传、灰度、观察、回滚」几节只是**纸面流程** ——
产出的是**未签名**的 release 包。本卡把那块补上。

## 目标

1. 有密钥 → `assembleRelease` 产出**已签名**、可发布的包；
2. **没有密钥 → 构建照常通过**（干净克隆与 CI 不能因为缺密钥而红）；
3. 密钥与密码**都不入库**。

## 变更清单

- [x] 生成密钥：`.tools/keystore/jizhangbao-release.jks`（RSA 4096，30 年）
- [x] `keystore.properties`（**不入库**，`.gitignore` 已覆盖）
- [x] `AndroidApplicationConventionPlugin`：读 `keystore.properties` 配置 release 签名；
      文件不存在则**跳过并明确说一声**（不是静默产出）
- [x] CI：构建步骤加上 `assembleRelease`（发布路径坏了在 debug 构建里看不见）
- [x] 发布手册的「签名」一节：从"当前没有配置"改成"已配置 + 备份责任 + 怎么重新配置"

## 验收

- [x] `:app:assembleRelease` → **`app-release.apk`**（8.66 MB，**已签名**）
- [x] **签名有效性**：`apksigner verify --print-certs` 输出
      `CN=Jizhangbao…` 且 SHA-256 `de33a614…ce9b` —— 与密钥指纹逐字一致
- [x] 包内版本号仍正确：`aapt2 dump badging` → `versionCode=1 versionName=0.1.0`
- [x] **把 `keystore.properties` 移开再构建**：`BUILD SUCCESSFUL` +
      日志「未找到 keystore.properties：release 包将**不签名**」+ 产物 `app-release-unsigned.apk`
- [x] `git check-ignore -v` 双重确认：`keystore.properties` 与 `.jks` 各自被一条规则挡住
- [x] 门禁：`test` / `detekt` / `lintDebug` / `assembleDebug` / 两条架构校验 / 追溯校验

## 完成情况

- 提交: `feat/T-024-signing` → `develop`（见本轮提交）
- 密钥指纹（**公开信息**，签名身份靠它辨认）：`SHA256 DE:33:A6:14:AA:82:5B:85:DB:85:D1:53:EF:FF:DA:8B:15:74:13:15:B9:D9:70:9B:25:4F:6D:AA:97:66:CE:9B`
- ⚠️ **密钥与密码都只在磁盘上**（`.tools/keystore/` 与 `keystore.properties`），
  **没有**进版本库、**没有**打印进对话。请把它们搬进你的密钥管理器并做离线备份 ——
  签名身份不可更换，丢了就再也发不出更新。
- 未决: 无
- 备注: ⚠️ 三条边界 —— **缺密钥必须能构建**（否则干净克隆与 CI 全红，为了发布把日常弄坏是净损失）；
  **不签名要说出来**（静默产出一个看起来没问题的包更糟）；**密钥是秘密**（不入库、不打印）

## 豁免项

| 豁免内容 | 原因 | 批准人 | 跟踪任务 |
|---|---|---|---|
| 密钥管理器 / CI 注入密码 | 本机自用侧载，没有密钥管理系统；真上架时再补（CI 目前不需要签名） | 用户（`Q-007` 自用侧载） | 需要时另开任务 |
