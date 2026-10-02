package com.sockc.billinsight.analysis

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantAnalysisTest {
    private fun tx(merchant: String, type: FlowType = FlowType.EXPENSE): Transaction =
        Transaction(
            platform = Platform.WECHAT, occurredAt = 123456789L,
            counterparty = merchant, description = "商品", directionText = "支出",
            amountCent = 2000, flowType = type, category = "购物",
            paymentMethod = "", transactionId = "", merchantOrderId = "",
            sourceFile = "", fingerprint = merchant + type.name,
        )

    @Test fun payLaterLabelDoesNotBecomeMerchant() {
        val groups = MerchantAnalysis.groups(listOf(
            tx("先用后付 · 拼多多平台商户"), tx("拼多多")
        ))
        assertEquals(1, groups.size)
        assertEquals("拼多多", groups.single().name)
        assertEquals(4000L, groups.single().amountCent)
    }

    @Test fun transfersAndReceiptsNeverAppearInMerchantRanking() {
        val groups = MerchantAnalysis.groups(listOf(
            tx("转账备注:微信转账 · 爸（海）"),
            tx("收款方备注:二维码收款 · 我们e家"),
            tx("/", FlowType.EXPENSE),
            tx("苏铂超市（白云湖）", FlowType.INCOME),
        ))
        assertEquals(0, groups.size)
    }

    @Test fun duplicateMerchantLabelIsCollapsed() {
        val tx = tx("苏铂超市（白云湖） · 苏铂超市（白云湖）")
        assertEquals("苏铂超市（白云湖）", MerchantAnalysis.merchantName(tx))
    }
    @Test fun userMerchantAliasMergesDifferentSourcesWithoutChangingOriginalTransactions() {
        val one = tx("瑞幸咖啡")
        val two = tx("瑞幸咖啡广州分店")
        val result = MerchantAnalysis.groups(
            listOf(one, two),
            mapOf("瑞幸咖啡广州分店" to "瑞幸咖啡")
        )
        assertEquals(1, result.size)
        assertEquals(2, result.single().count)
        assertEquals(4000L, result.single().amountCent)
        assertEquals("瑞幸咖啡广州分店", two.counterparty)
    }
}
