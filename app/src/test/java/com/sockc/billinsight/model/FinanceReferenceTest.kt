package com.sockc.billinsight.model

import org.junit.Assert.*
import org.junit.Test

class FinanceReferenceTest {
    private fun bill(
        id:Long=1,platform:Platform=Platform.JD,
        trade:String="白条分期还款",description:String="分期计划编号: JD202610010089 第2/12期",
        merchant:String="京东白条",order:String="PAYMENT000001"
    )=Transaction(
        id=id,platform=platform,occurredAt=1L,counterparty=merchant,
        description=description,directionText="支出",tradeType=trade,
        amountCent=20000,flowType=FlowType.LOAN_REPAYMENT,category="贷款还款",
        paymentMethod="白条",transactionId="P000000001",
        merchantOrderId=order,sourceFile="jd.csv",fingerprint="test_$id"
    )

    @Test fun extractsOnlyExplicitSharedPlanIdentifiers() {
        assertEquals("JD202610010089",FinanceReference.explicitReference(bill()))
        assertEquals(12,FinanceReference.totalTerms(bill()))
        assertNull(FinanceReference.explicitReference(
            bill(description="白条月供第2期，支付订单 PAYMENT000001")
        ))
    }

    @Test fun sameAmountAndPayeeWithoutPlanNumberAreNeverAutoLinked() {
        val seed=bill(description="白条分期第1期")
        val next=bill(2,description="白条分期第2期")
        assertNull(FinanceReference.explicitReference(seed))
        assertFalse(FinanceReference.canAutoLink(
            Platform.JD,FinanceReference.explicitReference(seed),next))
        assertNotNull(FinanceReference.candidateReason(seed,next,null))
    }

    @Test fun sameVerifiedPlanAndPlatformCanLinkMultiplePeriods() {
        val later=bill(2,description="分期计划编号: JD202610010089 第3/12期",
            order="PAYMENT999999")
        assertTrue(FinanceReference.canAutoLink(
            Platform.JD,"JD202610010089",later))
    }

    @Test fun differentPlatformsCannotAutoLink() {
        assertFalse(FinanceReference.canAutoLink(
            Platform.JD,"JD202610010089",bill(2,platform=Platform.ALIPAY)))
    }

    @Test fun ordinaryPaymentTransactionIdIsNotPlanIdentifier() {
        val seed=bill(description="交易号 PAYMENT000001")
        val candidate=bill(2,description="分期还款 第2期",order="PAYMENT000001")
        assertNull(FinanceReference.explicitReference(seed))
        assertFalse(FinanceReference.canAutoLink(Platform.JD,
            FinanceReference.explicitReference(seed),candidate))
    }

    @Test fun repaidAndReceivableAreIndependent() {
        val seed=bill()
        val a=FinancePlanLink(seed,FinanceLinkRole.REPAYMENT,true)
        val recovery=FinancePlanLink(seed.copy(id=3,flowType=FlowType.INCOME,amountCent=7000),
            FinanceLinkRole.RECOVERY,false)
        val p=FinancePlan(10,1,Platform.JD,"京东白条","手机",
            FinancePlanKind.ADVANCE,"小张",null,60000,3,10,listOf(a,recovery))
        assertEquals(40000L,p.remainingCent)
        assertEquals(53000L,p.receivableCent)
        assertEquals(20000L,p.repaidCent)
        assertEquals(7000L,p.recoveredCent)
    }

    @Test fun unknownTotalStaysUnknown() {
        val p=FinancePlan(10,1,Platform.JD,"白条","手机",
            FinancePlanKind.OWN,"",null,null,null,null,emptyList())
        assertNull(p.remainingCent)
    }
}
