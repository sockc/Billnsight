package com.sockc.billinsight.importer

import com.sockc.billinsight.model.*
import org.junit.Assert.*
import org.junit.Test

class MerchantNaturePolicyTest {
    private fun tx(
        payee:String="张记饭店",platform:Platform=Platform.WECHAT,
        direction:String="支出",flow:FlowType=FlowType.EXPENSE,
    )=Transaction(platform=platform,occurredAt=123L,counterparty=payee,
        description="吃饭",directionText=direction,tradeType="商户消费",amountCent=2300,
        flowType=flow,category="其他",paymentMethod="零钱",
        transactionId="",merchantOrderId="",sourceFile="微信账单.csv",fingerprint="unique")

    @Test fun exactMerchantAndDirectionUpdatesOnlyMatchingOrdinaryRows() {
        val rule=MerchantNatureRule(Platform.WECHAT,"张记饭店","OUT",
            FlowType.EXPENSE,"餐饮")
        assertEquals("餐饮",MerchantNaturePolicy.apply(tx(),rule).category)
        assertEquals("其他",MerchantNaturePolicy.apply(tx(platform=Platform.ALIPAY),rule).category)
        assertEquals("其他",MerchantNaturePolicy.apply(tx(direction="收入",
            flow=FlowType.INCOME),rule).category)
    }
    @Test fun specialFlowsAndGenericQrLabelsNeverChange() {
        val rule=MerchantNatureRule(Platform.WECHAT,"张记饭店","OUT",
            FlowType.EXPENSE,"餐饮")
        assertEquals(FlowType.REFUND,MerchantNaturePolicy.apply(
            tx(flow=FlowType.REFUND),rule).flowType)
        assertEquals(FlowType.CREDIT_REPAYMENT,MerchantNaturePolicy.apply(
            tx(flow=FlowType.CREDIT_REPAYMENT),rule).flowType)
        assertFalse(MerchantNaturePolicy.eligibleMerchant(tx(payee="二维码付款")))
    }
    @Test fun selectedDirectionCannotReclassifyIncomeAsOutflow() {
        assertFalse(MerchantNaturePolicy.allowsDirection("IN",FlowType.EXPENSE))
        assertFalse(MerchantNaturePolicy.allowsDirection("OUT",FlowType.INCOME))
    }
}
