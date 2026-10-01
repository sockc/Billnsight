# 数据口径与模型

## transactions

核心字段：

- `platform`: WECHAT / ALIPAY / UNKNOWN
- `occurred_at`: 交易时间，epoch milliseconds
- `counterparty`: 交易对方 / 商户
- `description`: 商品或交易说明
- `amount_cent`: 金额，单位“分”
- `flow_type`: EXPENSE / INCOME / TRANSFER / REFUND / IGNORE
- `category`: 消费分类
- `transaction_id`: 平台交易单号
- `merchant_order_id`: 商户订单号
- `fingerprint`: SHA-256 唯一指纹

所有金额使用整数“分”，不使用 Float/Double 作为账务存储。

## merchant_rules

用户修正商户分类后记录：

```text
merchant -> category
```

新导入流水先查用户规则，再使用内置关键词分类。

## 去重

优先使用平台交易单号；缺少时使用：

```text
平台 + 时间 + 对方 + 商品 + 收支方向 + 金额
```

生成 SHA-256 指纹并设置数据库 UNIQUE 约束。
