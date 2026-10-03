package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import org.junit.Assert.*
import org.junit.Test

class OtherReclassificationPolicyTest {
    private fun can(
        category: String = "其他", edited: Boolean = false,
        source: String = "微信账单.csv", flow: FlowType = FlowType.EXPENSE,
        includeReviewed: Boolean = false
    ) = OtherReclassificationPolicy.eligible(
        flow, category, source, edited, includeReviewed
    )

    @Test fun defaultOnlyChecksUnmodifiedImportedOther() {
        assertTrue(can())
        assertFalse(can(edited=true))
        assertFalse(can(source="手动记账"))
        assertFalse(can(category="餐饮"))
        assertFalse(can(flow=FlowType.CREDIT_REPAYMENT))
        assertFalse(can(flow=FlowType.TRANSFER))
    }

    @Test fun explicitOptInOnlyRechecksPreviouslyEditedOther() {
        assertTrue(can(edited=true,includeReviewed=true))
        assertFalse(can(category="医疗",edited=true,includeReviewed=true))
        assertFalse(can(category="其他",source="手动记账",edited=true,includeReviewed=true))
        assertFalse(can(category="其他",flow=FlowType.LOAN_REPAYMENT,includeReviewed=true))
    }

    @Test fun recognizableMerchantsAreNotTheCauseOfSkippedHistory() {
        assertEquals("商超日用",MerchantLexicon.suggest("拾贰便利店","收款方备注:二维码收款"))
        assertEquals("商超日用",MerchantLexicon.suggest("新永佳百货"))
        assertEquals("商超日用",MerchantLexicon.suggest("等你淘便利店"))
        assertNull(MerchantLexicon.suggest("程耀军","/"))
        assertNull(MerchantLexicon.suggest("快乐就好","收款方备注:二维码收款"))
    }
}
