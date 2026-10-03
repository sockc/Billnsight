package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import org.junit.Assert.*
import org.junit.Test

class MerchantCategoryPolicyTest {
    private fun tx(
        merchant:String="瑞幸咖啡",
        platform:Platform=Platform.WECHAT,
        category:String="其他",
        flow:FlowType=FlowType.EXPENSE,
        source:String="微信账单.csv",
    ) = Transaction(
        platform=platform,occurredAt=1L,counterparty=merchant,
        description="",directionText="支出",amountCent=1900L,
        flowType=flow,category=category,paymentMethod="余额",
        transactionId="",merchantOrderId="",sourceFile=source,fingerprint="sample"
    )

    @Test fun normalizesWhitespaceAndCaseWithinSameSource() {
        val aliases=emptyMap<String,String>()
        assertEquals(
            MerchantCategoryPolicy.key(tx("  ABC  COFFEE "),aliases),
            MerchantCategoryPolicy.key(tx("abc coffee"),aliases)
        )
    }

    @Test fun explicitAliasesMergeKnownNames() {
        val aliases=mapOf("瑞幸咖啡广州店" to "瑞幸咖啡",
            "瑞幸咖啡天河店" to "瑞幸咖啡")
            .mapKeys {MerchantCategoryPolicy.normalize(it.key)}
        assertEquals(
            MerchantCategoryPolicy.key(tx("瑞幸咖啡广州店"),aliases),
            MerchantCategoryPolicy.key(tx("瑞幸咖啡天河店"),aliases)
        )
    }

    @Test fun differentSourcesDoNotBulkReclassifyEachOther() {
        assertNotEquals(
            MerchantCategoryPolicy.key(tx(platform=Platform.WECHAT),emptyMap()),
            MerchantCategoryPolicy.key(tx(platform=Platform.ALIPAY),emptyMap())
        )
    }

    @Test fun unknownGenericAndManualRecordsNeverGetMerchantWideRule() {
        assertNull(MerchantCategoryPolicy.key(tx(merchant="二维码付款"),emptyMap()))
        assertNull(MerchantCategoryPolicy.key(tx(platform=Platform.UNKNOWN),emptyMap()))
        assertNull(MerchantCategoryPolicy.key(tx(source="手动记账"),emptyMap()))
    }

    @Test fun manuallyConfirmedOtherIsProtectedFromMerchantWideChanges() {
        assertFalse(MerchantCategoryPolicy.canReplaceHistorical(tx(category="其他"),true))
        assertTrue(MerchantCategoryPolicy.canReplaceHistorical(tx(category="其他"),false))
        assertTrue(MerchantCategoryPolicy.canReplaceHistorical(tx(category="餐饮"),false))
    }

    @Test fun meaningfulManualCategoryAndNonExpenseRemainProtected() {
        assertFalse(MerchantCategoryPolicy.canReplaceHistorical(tx(category="医疗"),true))
        assertFalse(MerchantCategoryPolicy.canReplaceHistorical(
            tx(flow=FlowType.CREDIT_REPAYMENT),false))
    }
}
