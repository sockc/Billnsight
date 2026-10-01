# 导入适配策略

BillInsight 不假定微信和支付宝永远使用同一列顺序。

## V0.1.2 支持格式

- 微信 XLSX
- CSV / TXT
- ZIP 内的 XLSX / CSV / TXT
- 加密 ZIP
- 旧版 XLS 暂不支持

XLSX 使用轻量本地解析，不依赖 Apache POI。读取：

```text
xl/workbook.xml
xl/styles.xml
xl/sharedStrings.xml
xl/worksheets/sheet*.xml
```

兼容 sharedStrings、inlineStr、稀疏单元格、数值金额，以及 Excel 1900 / 1904 日期系统。

解析顺序：

1. 判断文件是 XLSX 还是普通 ZIP
2. 普通 ZIP 解压并寻找 XLSX / CSV / TXT
3. XLSX 读取工作表、共享字符串、样式和日期系统
4. CSV 自动识别 UTF-8 / GB18030 / GBK
5. 在前 80 行搜索账单表头
6. 使用表头别名定位字段
7. 判断微信 / 支付宝平台
8. 解析流水、分类并生成去重指纹
9. 事务写入 SQLite

## 已兼容表头别名

时间：

```text
交易时间 / 交易创建时间 / 付款时间 / 创建时间
```

金额：

```text
金额(元) / 金额（元） / 金额 / 交易金额
```

交易对方：

```text
交易对方 / 对方 / 商户名称 / 交易商户
```

平台未来调整列顺序时，只要字段名称仍能命中别名，就无需修改解析器。
