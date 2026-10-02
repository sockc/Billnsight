package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType

data class Classification(val flowType: FlowType, val category: String)

object TransactionClassifier {
    val categories = listOf(
        "餐饮", "商超日用", "购物", "交通", "住房", "生活缴费", "娱乐", "医疗",
        "人情", "车辆", "数码", "教育", "旅行", "经营相关", "其他"
    )

    fun classify(
        direction: String,
        type: String,
        merchant: String,
        description: String,
        status: String,
        merchantRules: Map<String, String>,
    ): Classification {
        val all = "$direction $type $merchant $description $status".lowercase()
        val kind = "$type $description".lowercase()
        val state = status.lowercase()
        val incoming = direction.contains("收入")
        val outgoing = direction.contains("支出")

        if (listOf("交易关闭", "已关闭", "失败", "未支付").any { state.contains(it) }) {
            return Classification(FlowType.IGNORE, "忽略")
        }
        if (listOf("退款", "退回", "refund").any { kind.contains(it) || state.contains(it) }) {
            return Classification(FlowType.REFUND, "退款")
        }
        if (kind.contains("红包") || type.contains("红包")) {
            return when {
                incoming -> Classification(FlowType.GIFT_INCOME, "红包收入")
                outgoing -> Classification(FlowType.GIFT_EXPENSE, "人情")
                else -> Classification(FlowType.PENDING, "待确认")
            }
        }
        // Known movements between accounts are not personal consumption.
        if (listOf("充值", "提现", "信用卡还款", "余额宝", "零钱通", "资金转入", "资金转出")
                .any { kind.contains(it) }) {
            return Classification(FlowType.TRANSFER, "资金流转")
        }
        // Do not guess the purpose of a payment to another person or a personal QR code.
        if (listOf("转账", "二维码收款", "收钱码", "面对面收款", "个人收款码")
                .any { kind.contains(it) }) {
            return Classification(FlowType.PENDING, "待确认")
        }
        if (listOf("二维码付款", "扫码付款", "扫一扫付款").any { kind.contains(it) } &&
            merchant.trim().isBlank()
        ) {
            return Classification(FlowType.PENDING, "待确认")
        }
        if (kind.contains("二维码付款") || kind.contains("扫码付款")) {
            val knownCategory = merchantRules[merchant.trim()]
            return if (outgoing && knownCategory != null) {
                Classification(FlowType.EXPENSE, knownCategory)
            } else {
                Classification(FlowType.PENDING, "待确认")
            }
        }

        val flow = when {
            outgoing -> FlowType.EXPENSE
            incoming -> FlowType.INCOME
            direction.contains("不计收支") -> FlowType.IGNORE
            type.contains("支出") -> FlowType.EXPENSE
            type.contains("收入") -> FlowType.INCOME
            else -> FlowType.IGNORE
        }
        if (flow != FlowType.EXPENSE) {
            return Classification(flow, if (flow == FlowType.INCOME) "收入" else "忽略")
        }
        merchantRules[merchant.trim()]?.let { return Classification(FlowType.EXPENSE, it) }
        return Classification(FlowType.EXPENSE, categoryFor(all))
    }

    private fun categoryFor(text: String): String = when {
        has(text, "美团外卖", "饿了么", "餐饮", "饭店", "餐厅", "小吃", "麦当劳", "肯德基", "瑞幸", "咖啡", "奶茶", "喜茶", "奈雪", "烧烤", "火锅") -> "餐饮"
        has(text, "便利店", "超市", "商超", "百货", "日用品", "生活用品") -> "商超日用"
        has(text, "淘宝", "天猫", "京东", "拼多多", "唯品会", "购物", "商场", "服饰", "鞋", "包") -> "购物"
        has(text, "滴滴", "高德打车", "出租车", "公交", "地铁", "12306", "铁路", "机票", "航空", "共享单车") -> "交通"
        has(text, "房租", "物业", "租金", "住房", "公寓") -> "住房"
        has(text, "电费", "水费", "燃气", "话费", "宽带", "中国移动", "中国联通", "中国电信", "生活缴费") -> "生活缴费"
        has(text, "游戏", "电影", "影院", "ktv", "视频会员", "音乐会员", "娱乐") -> "娱乐"
        has(text, "医院", "药房", "药店", "诊所", "挂号", "医疗", "体检") -> "医疗"
        has(text, "红包", "礼物", "礼金", "份子", "人情") -> "人情"
        has(text, "加油", "停车", "高速", "etc", "洗车", "维修", "汽车", "充电桩") -> "车辆"
        has(text, "apple", "小米", "华为", "oppo", "vivo", "手机", "电脑", "数码") -> "数码"
        has(text, "学费", "培训", "课程", "教育", "学校", "书店") -> "教育"
        has(text, "酒店", "民宿", "携程", "飞猪", "去哪儿", "景区", "旅行", "旅游") -> "旅行"
        has(text, "服务器", "云服务", "域名", "货款", "采购", "进货") -> "经营相关"
        else -> "其他"
    }

    private fun has(text: String, vararg keywords: String) = keywords.any { text.contains(it.lowercase()) }
}
