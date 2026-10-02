package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPaymentClassifierTest {
    private fun classify(direction:String,type:String,merchant:String,desc:String,payment:String="") =
        TransactionClassifier.classify(direction,type,merchant,desc,"支付成功",emptyMap(),payment)

    @Test fun friendPersonalQrAndMerchantQrAreConsumption() {
        assertEquals(FlowType.EXPENSE,
            classify("支出","转账","张三","扫描朋友个人收款码付款").flowType)
        assertEquals(FlowType.EXPENSE,
            classify("支出","二维码付款","路边水果摊","苹果").flowType)
        assertEquals(FlowType.EXPENSE,
            classify("支出","扫码支付","李四","AA晚餐").flowType)
    }
    @Test fun ordinaryFriendTransferIsExpenseAndQrReceiptIsIncome() {
        val sent=classify("支出","微信转账","张三","晚饭")
        assertEquals(FlowType.EXPENSE,sent.flowType)
        assertEquals("转账支出",sent.category)
        val received=classify("收入","二维码收款","张三","收款成功")
        assertEquals(FlowType.INCOME,received.flowType)
        assertEquals("扫码收入",received.category)
    }
    @Test fun cardFundedQrStillCountsAsPurchaseNotCreditRepayment() {
        assertEquals(FlowType.EXPENSE,
            classify("支出","二维码付款","水果店","水果","招商银行信用卡").flowType)
        assertEquals(FlowType.CREDIT_REPAYMENT,
            classify("支出","信用卡还款","招商银行","还款成功").flowType)
    }
    @Test fun genericPayeeRequiresReviewButRealPersonDoesNot() {
        fun tx(payee:String)=Transaction(
            platform=Platform.WECHAT,occurredAt=1,counterparty=payee,
            description="扫码付款",directionText="支出",tradeType="二维码付款",
            amountCent=1500,flowType=FlowType.EXPENSE,category="其他",
            paymentMethod="",transactionId="",merchantOrderId="",
            sourceFile="x",fingerprint=payee,
        )
        assertTrue(ScanPaymentClassifier.needsMerchantReview(tx("二维码付款")))
        assertFalse(ScanPaymentClassifier.needsMerchantReview(tx("张三")))
        assertFalse(ScanPaymentClassifier.needsMerchantReview(tx("二维码付款"),"水果档口"))
    }
    @Test fun manualScanIsAValidQrExpenseButGenericFriendTransferIsNot() {
        assertTrue(ScanPaymentClassifier.isQrPayment(
            "支出","手动扫码消费","早饭"))
        assertFalse(ScanPaymentClassifier.isQrPayment(
            "支出","转账","向个人付款"))
    }

    @Test fun qrWordInRefundDoesNotMakeExpense() {
        assertFalse(ScanPaymentClassifier.isQrPayment("收入","二维码收款","扫码收款"))
        assertEquals(FlowType.REFUND, classify("收入","二维码付款","某人","退款").flowType)
    }
}