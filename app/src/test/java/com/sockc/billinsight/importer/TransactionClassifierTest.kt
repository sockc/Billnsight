package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionClassifierTest {
    @Test
    fun personToPersonTransferNeedsReview() {
        val result = TransactionClassifier.classify(
            direction = "支出",
            type = "转账",
            merchant = "张三",
            description = "转账",
            status = "支付成功",
            merchantRules = emptyMap(),
        )
        assertEquals(FlowType.PENDING, result.flowType)
    }

    @Test
    fun foodMerchantIsFood() {
        val result = TransactionClassifier.classify(
            direction = "支出",
            type = "商户消费",
            merchant = "瑞幸咖啡",
            description = "拿铁",
            status = "支付成功",
            merchantRules = emptyMap(),
        )
        assertEquals(FlowType.EXPENSE, result.flowType)
        assertEquals("餐饮", result.category)
    }

    @Test
    fun merchantRuleWins() {
        val result = TransactionClassifier.classify(
            direction = "支出",
            type = "商户消费",
            merchant = "XX便利店",
            description = "日用品",
            status = "支付成功",
            merchantRules = mapOf("XX便利店" to "购物"),
        )
        assertEquals("购物", result.category)
    }
    @Test fun redPacketOutgoingIsGiftExpense() {
        val actual = TransactionClassifier.classify("支出", "微信红包", "朋友", "生日红包", "支付成功", emptyMap())
        assertEquals(FlowType.GIFT_EXPENSE, actual.flowType)
    }

    @Test fun redPacketIncomingIsGiftIncome() {
        val actual = TransactionClassifier.classify("收入", "微信红包", "朋友", "红包", "已入账", emptyMap())
        assertEquals(FlowType.GIFT_INCOME, actual.flowType)
    }

    @Test fun returnedRedPacketIsRefund() {
        val actual = TransactionClassifier.classify("收入", "微信红包", "朋友", "红包退回", "已退款", emptyMap())
        assertEquals(FlowType.REFUND, actual.flowType)
    }

    @Test fun unknownQrReceiptNeedsReview() {
        val actual = TransactionClassifier.classify("收入", "二维码收款", "张三", "收钱码", "已收款", emptyMap())
        assertEquals(FlowType.PENDING, actual.flowType)
    }

    @Test fun selfAccountTopUpIsTransfer() {
        val actual = TransactionClassifier.classify("支出", "零钱充值", "微信零钱", "充值", "成功", emptyMap())
        assertEquals(FlowType.TRANSFER, actual.flowType)
    }

    @Test fun qrPaymentNeedsReviewWithoutKnownMerchantRule() {
        val actual = TransactionClassifier.classify("支出", "二维码付款", "张三", "扫码付款", "支付成功", emptyMap())
        assertEquals(FlowType.PENDING, actual.flowType)
    }

    @Test fun knownMerchantQrPaymentCanUseExplicitRule() {
        val actual = TransactionClassifier.classify("支出", "二维码付款", "XX饭店", "扫码付款", "成功", mapOf("XX饭店" to "餐饮"))
        assertEquals(FlowType.EXPENSE, actual.flowType)
        assertEquals("餐饮", actual.category)
    }
}
