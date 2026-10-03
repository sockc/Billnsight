package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType

data class Classification(val flowType: FlowType, val category: String)

object TransactionClassifier {
    val categories = listOf(
        "餐饮", "商超日用", "购物", "交通", "住房", "生活缴费", "娱乐", "医疗",
        "人情", "车辆", "数码", "教育", "旅行", "水果", "买菜", "饮品", "加油", "经营相关", "其他"
    )

    fun classify(
        direction: String,
        type: String,
        merchant: String,
        description: String,
        status: String,
        merchantRules: Map<String, String>,
        paymentMethod: String = "",
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
        FinancialTransactionDetector.detect(
            direction,type,merchant,description,status,paymentMethod
        )?.let{return it}
        // Repayment destination can appear in the merchant field or payment method.
        // Do not confuse a purchase paid WITH a credit card with paying off its bill.
        if (CreditRepaymentDetector.isRepayment(
                direction, type, merchant, description, paymentMethod, status
            )) {
            return Classification(FlowType.CREDIT_REPAYMENT, "信用卡还款")
        }
        // Explicit loan disbursements are liabilities, not earned personal income.
        val loanDisbursement = listOf(
            "贷款发放", "贷款放款", "贷款到账", "借款到账",
            "借呗放款", "微粒贷放款", "网商贷放款", "借款发放"
        ).any { kind.contains(it) }
        if (incoming && loanDisbursement) {
            return Classification(FlowType.LOAN_DISBURSEMENT, "贷款到账")
        }
        // Do not confuse a credit-card bill payment with loan instalments.
        val loanRepayment = listOf(
            "贷款还款", "房贷还款", "车贷还款", "借呗还款",
            "微粒贷还款", "网商贷还款", "分期还款", "贷款扣款",
            "贷款本息", "还本付息", "偿还贷款"
        ).any { kind.contains(it) }
        if (outgoing && loanRepayment) {
            return Classification(FlowType.LOAN_REPAYMENT, "贷款还款")
        }
        // Known movements between accounts are not personal consumption.
        // Withdrawals/recharges only move funds between accounts, not income or expense.
        if (listOf("提现", "提现到银行卡", "转出到银行卡").any { kind.contains(it) }) {
            return Classification(FlowType.TRANSFER, "资金提现")
        }
        // A mobile top-up, charging order or prepaid service is consumption.
        // Only explicit wallet/account reloads are internal fund movements.
        val wallet = "$merchant $type $description".lowercase()
        val accountRecharge = kind.contains("充值") &&
            listOf("微信零钱", "支付宝余额", "余额宝", "零钱通", "钱包余额", "银行卡余额")
                .any { wallet.contains(it) }
        if (accountRecharge || listOf("余额宝", "零钱通", "资金转入", "资金转出")
                .any { kind.contains(it) }) {
            return Classification(FlowType.TRANSFER, "资金流转")
        }
        // Explicit QR receipts/payments have a default direction. The user can still
        // change their nature per transaction (e.g. business proceeds or repayment).
        val qrReceipt = listOf("二维码收款", "收钱码", "面对面收款", "个人收款码", "扫码收款", "收款码收款")
            .any { kind.contains(it) }
        val qrPayment = ScanPaymentClassifier.isQrPayment(direction,type,description)
        if (qrReceipt && incoming) return Classification(FlowType.INCOME, "扫码收入")
        // Paying a friend's personal collection QR is normally a purchase too.
        // Transfer wording does not overrule explicit outgoing QR evidence.
        // Outgoing payer statements may describe the payee's "二维码收款".
        // That does not turn our outgoing payment into incoming QR income.
        if ((qrPayment || qrReceipt) && outgoing) {
            val rule = merchant.takeUnless {
                ScanPaymentClassifier.isGenericCounterparty(it) ||
                MerchantLexicon.requiresProductEvidence(it)
            }
                ?.let { name -> merchantRules.entries.firstOrNull {
                    MerchantCategoryPolicy.normalize(it.key) == MerchantCategoryPolicy.normalize(name)
                }?.value }
            return Classification(FlowType.EXPENSE,
                rule?.takeUnless { it == "其他" } ?: MerchantLexicon.suggest(merchant, description) ?:
                    if(MerchantLexicon.requiresProductEvidence(merchant)) "其他" else categoryFor(all))
        }
        if (qrReceipt || kind.contains("二维码付款") || kind.contains("扫码支付")) {
            return Classification(FlowType.PENDING, "待确认")
        }
        if (kind.contains("转账") || type.contains("转账")) {
            return when {
                incoming -> Classification(FlowType.INCOME, "转账收入")
                outgoing -> Classification(FlowType.EXPENSE, "转账支出")
                else -> Classification(FlowType.PENDING, "待确认")
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
        if (!ScanPaymentClassifier.isGenericCounterparty(merchant) &&
            !MerchantLexicon.requiresProductEvidence(merchant)) {
            merchantRules.entries.firstOrNull {
                MerchantCategoryPolicy.normalize(it.key) == MerchantCategoryPolicy.normalize(merchant)
            }?.value?.takeUnless { it == "其他" }?.let { return Classification(FlowType.EXPENSE, it) }
        }
        return Classification(FlowType.EXPENSE,
            MerchantLexicon.suggest(merchant, description) ?:
                    if(MerchantLexicon.requiresProductEvidence(merchant)) "其他" else categoryFor(all))
    }

    private fun categoryFor(text: String): String = when {
        has(text, "水果", "果园", "果业", "果蔬店", "榴莲", "荔枝", "西瓜", "水果摊") -> "水果"
        has(text, "菜市场", "买菜", "菜摊", "蔬菜", "菜场", "生鲜市场") -> "买菜"
        has(text, "奶茶", "咖啡", "瑞幸", "喜茶", "奈雪", "饮品", "果茶") -> "饮品"
        has(text, "加油站", "加油", "汽油") -> "加油"
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
