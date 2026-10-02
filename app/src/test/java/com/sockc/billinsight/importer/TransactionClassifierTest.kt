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

    @Test fun qrReceiptDefaultsToIncome() {
        val actual = TransactionClassifier.classify("收入", "二维码收款", "张三", "收钱码", "已收款", emptyMap())
        assertEquals(FlowType.INCOME, actual.flowType)
    }

    @Test fun selfAccountTopUpIsTransfer() {
        val actual = TransactionClassifier.classify("支出", "零钱充值", "微信零钱", "充值", "成功", emptyMap())
        assertEquals(FlowType.TRANSFER, actual.flowType)
    }

    @Test fun qrPaymentDefaultsToExpenseWithoutKnownMerchantRule() {
        val actual = TransactionClassifier.classify("支出", "二维码付款", "张三", "扫码付款", "支付成功", emptyMap())
        assertEquals(FlowType.EXPENSE, actual.flowType)
        assertEquals("其他", actual.category)
    }

    @Test fun knownMerchantQrPaymentCanUseExplicitRule() {
        val actual = TransactionClassifier.classify("支出", "二维码付款", "XX饭店", "扫码付款", "成功", mapOf("XX饭店" to "餐饮"))
        assertEquals(FlowType.EXPENSE, actual.flowType)
        assertEquals("餐饮", actual.category)
    }
    @Test fun unspecifiedTransferStillNeedsReview() {
        val actual = TransactionClassifier.classify("收入", "转账", "张三", "给你的转账", "已收款", emptyMap())
        assertEquals(FlowType.PENDING, actual.flowType)
    }

    @Test fun qrReceiptWithoutDirectionNeedsReview() {
        val actual = TransactionClassifier.classify("不计收支", "二维码收款", "张三", "二维码收款", "已完成", emptyMap())
        assertEquals(FlowType.PENDING, actual.flowType)
    }

    @Test fun userRuleDoesNotOverrideIncomingQrReceipt() {
        val actual = TransactionClassifier.classify("收入", "收钱码", "XX饭店", "扫码收款", "收款成功", mapOf("XX饭店" to "餐饮"))
        assertEquals(FlowType.INCOME, actual.flowType)
    }

    @Test fun creditCardRepaymentIsCashOutflowNotTransfer() {
        val actual = TransactionClassifier.classify("支出", "信用卡还款", "招商银行", "还信用卡", "成功", emptyMap())
        assertEquals(FlowType.CREDIT_REPAYMENT, actual.flowType)
        assertEquals("信用卡还款", actual.category)
    }

    @Test fun creditCardPurchaseRemainsExpense() {
        val actual = TransactionClassifier.classify("支出", "商户消费", "便利店", "信用卡支付", "成功", emptyMap())
        assertEquals(FlowType.EXPENSE, actual.flowType)
    }

    @Test fun walletTopupStaysTransfer() {
        val actual = TransactionClassifier.classify("支出", "零钱充值", "微信零钱", "充值", "成功", emptyMap())
        assertEquals(FlowType.TRANSFER, actual.flowType)
    }
    @Test fun housingLoanRepaymentIsOutflowNotPurchase() {
        val actual = TransactionClassifier.classify(
            "支出","贷款还款","中国建设银行","2026年10月房贷还款","交易成功",emptyMap()
        )
        assertEquals(FlowType.LOAN_REPAYMENT,actual.flowType)
        assertEquals("贷款还款",actual.category)
    }

    @Test fun consumerLoanRepaymentIsRecognized() {
        val actual = TransactionClassifier.classify(
            "支出","借呗还款","蚂蚁集团","本期分期还款","成功",emptyMap()
        )
        assertEquals(FlowType.LOAN_REPAYMENT,actual.flowType)
    }

    @Test fun loanProceedsAreNotPersonalIncome() {
        val actual = TransactionClassifier.classify(
            "收入","贷款发放","某银行","借款到账","成功",emptyMap()
        )
        assertEquals(FlowType.LOAN_DISBURSEMENT,actual.flowType)
    }

    @Test fun ordinaryPersonalTransferIsNotAutomaticallyLoanRepayment() {
        val actual = TransactionClassifier.classify(
            "支出","转账","张三","归还之前借的钱","成功",emptyMap()
        )
        assertEquals(FlowType.PENDING,actual.flowType)
    }
    @Test fun creditCardInstallmentIsNotMistakenForLoanRepayment() {
        val actual = TransactionClassifier.classify(
            "支出","信用卡分期还款","建设银行","信用卡分期还款","成功",emptyMap()
        )
        assertEquals(FlowType.CREDIT_REPAYMENT,actual.flowType)
    }
    @Test fun repaymentInCounterpartyIsNotLost() {
        val actual = TransactionClassifier.classify(
            direction = "支出", type = "商户消费", merchant = "招商银行信用卡还款",
            description = "账单代扣", status = "支付成功", merchantRules = emptyMap(),
        )
        assertEquals(FlowType.CREDIT_REPAYMENT, actual.flowType)
    }

    @Test fun notCountedQrAndCardFundedPurchaseAreDistinct() {
        val credit = TransactionClassifier.classify(
            "不计收支", "信用卡账单还款", "中国银行", "本期还款", "成功", emptyMap()
        )
        assertEquals(FlowType.CREDIT_REPAYMENT, credit.flowType)
        val purchase = TransactionClassifier.classify(
            "支出", "商户消费", "便利店", "扫码购物", "成功", emptyMap(),
            paymentMethod = "招商银行信用卡"
        )
        assertEquals(FlowType.EXPENSE, purchase.flowType)
    }

    @Test fun repaymentPaymentMethodFieldCanSupplyMissingDescription() {
        val actual = TransactionClassifier.classify(
            "支出", "商户消费", "招行信用卡", "账单付款", "成功", emptyMap(),
            paymentMethod = "信用卡还款"
        )
        assertEquals(FlowType.CREDIT_REPAYMENT, actual.flowType)
    }

    @Test fun ordinaryMortgageMustStayLoanRepayment() {
        val actual = TransactionClassifier.classify(
            "支出", "贷款还款", "中国银行", "每月房贷还款", "成功", emptyMap()
        )
        assertEquals(FlowType.LOAN_REPAYMENT, actual.flowType)
    }
}
