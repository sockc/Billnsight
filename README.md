# BillInsight · 账单洞察

> 不要求你天天记账。把微信 / 支付宝账单导进来，告诉你钱到底花去哪了。

BillInsight 是一个 **Android 本地账单分析 App**。它的目标不是替代支付平台，也不是要求用户每消费一次就手动记账，而是把官方导出的微信 / 支付宝个人账单进行本地解析、去重、分类和分析。

## V0.1.2 已实现

- Android 原生 Kotlin + Jetpack Compose
- 微信 XLSX / 支付宝 CSV 表头自动识别
- 支持直接选择微信 XLSX、CSV / TXT
- 支持 ZIP 内的 XLSX / CSV / TXT
- 支持微信 / 支付宝 **加密 ZIP 解压码**
- UTF-8 / GBK / GB18030 编码容错
- SHA-256 指纹去重，重复导入不会重复记账
- 将消费、收入、退款、转账/充值/提现、忽略项分开
- 自动消费分类
- 流水列表
- 手工修正分类，并记住商户规则
- 月度消费汇总
- 分类排行
- 商户排行
- 小额高频消费分析（默认单笔 < ¥50）
- 本月最大支出
- 数据只存 Android App 私有 SQLite 数据库
- GitHub Actions 自动编译验证；可安装 APK 只从固定签名 Release 工作流产出

## 为什么“转账”不能算消费

例如：

1. 银行卡转入微信 ¥1,000
2. 微信支付晚餐 ¥80

真正消费只有 ¥80。如果把资金转入也当成支出，月消费会被重复放大。

BillInsight 的核心口径：

```text
真实消费 = EXPENSE
收入     = INCOME
退款     = REFUND
资金流转 = TRANSFER（不计入消费）
无效记录 = IGNORE
```

## 自动分类

默认分类包括：

```text
餐饮
商超日用
购物
交通
住房
生活缴费
娱乐
医疗
人情
车辆
数码
教育
旅行
经营相关
其他
```

自动分类采用：

```text
用户商户规则 > 内置关键词规则 > 其他
```

例如第一次把“XX便利店”从“其他”改为“商超日用”，以后同商户导入的新流水会自动使用该分类。

## 导入流程

### 微信

通常从微信支付账单申请“用于个人对账”，邮箱收到 ZIP，微信提供解压码。BillInsight 可以直接选择 ZIP，随后输入解压码，无需先手动解压。

### 支付宝

优先导出个人流水。若收到 ZIP，可直接导入；如果得到 CSV 也可直接导入。

> V0.1.2 已支持微信 `.xlsx` 账单直接导入，并兼容 Excel 序列日期。旧版 `.xls` 暂不支持。

## 隐私设计

V0.1.2：

- 不需要账号
- 不需要服务器
- 不上传账单
- `android:allowBackup="false"`
- 数据位于 App 私有目录

后续计划加入：

- 数据库加密
- 指纹 / 生物识别 App 锁
- 加密本地备份与恢复

## 技术栈

- Android Gradle Plugin 9.4.1
- Kotlin 2.4.20
- Jetpack Compose BOM 2026.09.00
- SQLiteOpenHelper（V0.1，减少 ORM/KSP 依赖）
- Zip4j 2.11.6（加密 ZIP）
- minSdk 26 / targetSdk 37
- JDK 17

## 本地构建

推荐 Android Studio 当前稳定版，JDK 17。

如果本机已安装 Gradle 9.6：

```bash
gradle :app:assembleDebug
```

Debug APK 仅用于 CI 编译验证，不再作为安装包发布。

正式安装与升级必须使用固定签名的 Release APK。

首次拉取后也可以生成 Gradle Wrapper：

```bash
gradle wrapper --gradle-version 9.6.0
```

## GitHub Actions

### Android CI

`.github/workflows/android.yml` 用于 Pull Request 和手工检查，自动运行单元测试以及 Debug / Release 编译验证。CI 不发布安装包。

### 自动固定签名 Release

`.github/workflows/android-release.yml` 会在代码推送到 `main` 后自动执行：

1. 读取 `versionName` / `versionCode`
2. 注入永久 JKS
3. 运行单元测试
4. 构建签名 Release APK
5. 用 `apksigner` 校验固定证书 SHA-256
6. 自动上传 Signed APK Artifact
7. 若对应 `vX.Y.Z` Release 尚不存在，自动创建 Git tag 和 GitHub Release 并上传 APK

因此正常开发流程只需要提交代码；**不需要再手工点 Run workflow 构建 APK**。

同一个 `versionName` 后续再次提交代码时，仍会自动生成签名 APK Artifact，但不会覆盖已经发布的正式 GitHub Release。要发布新正式版本时，只需递增 `versionCode` 和 `versionName`。

GitHub Actions Repository Secrets：

```text
KEYSTORE
STOREPASSWORD
KEYALIAS
KEYPASSWORD
```

其中 `KEYSTORE` 是固定 JKS 的 Base64 内容。JKS 不进入仓库。

永久签名证书 SHA-256：

```text
26:88:8F:20:32:33:DB:5E:B9:6C:D9:42:FB:D5:84:FC:E8:DC:1F:6B:F7:5D:78:90:AF:4F:BF:73:9D:84:50:A4
```

Release 工作流在构建后使用 `apksigner` 再次校验 APK，证书指纹不一致会直接失败。

> 永久 JKS 必须离线备份。丢失私钥后，Android 不允许新签名 APK 覆盖升级已有安装。

## Roadmap

详见 [docs/ROADMAP.md](docs/ROADMAP.md)。
