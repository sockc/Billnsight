package com.sockc.billinsight.analysis

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductAnalysisTest {
    private fun tx(platform: Platform, merchant: String, item: String, amount: Long): Transaction =
        Transaction(
            platform = platform, occurredAt = 1700000000000L,
            counterparty = merchant, description = item, directionText = "支出",
            amountCent = amount, flowType = FlowType.EXPENSE, category = "餐饮",
            paymentMethod = "", transactionId = "", merchantOrderId = "",
            sourceFile = "", fingerprint = "$merchant$item$amount$platform"
        )

    @Test fun sameMerchantProductAcrossPlatformsIsGrouped() {
        val grouped = ProductAnalysis.groups(
            listOf(tx(Platform.WECHAT,"瑞幸咖啡","生椰拿铁",1800),tx(Platform.ALIPAY,"瑞幸咖啡","生椰拿铁",1900))
        )
        assertEquals(1, grouped.size)
        assertEquals(2, grouped.single().count)
        assertEquals(3700L, grouped.single().amountCent)
    }

    @Test fun differentMerchantDoesNotBlindlyMerge() {
        val grouped = ProductAnalysis.groups(
            listOf(tx(Platform.WECHAT,"甲店","午餐",1800),tx(Platform.ALIPAY,"乙店","午餐",1900))
        )
        assertEquals(2, grouped.size)
    }

    @Test fun genericPaymentLabelIsNotPretendedToBeSameProduct() {
        val group = ProductAnalysis.groups(
            listOf(tx(Platform.WECHAT,"甲店","二维码付款",1800))
        ).single()
        assertTrue(group.unspecified)
        assertEquals("商品未注明", group.product)
    }
}
