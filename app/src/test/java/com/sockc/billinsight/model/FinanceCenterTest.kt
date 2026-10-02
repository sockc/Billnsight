package com.sockc.billinsight.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FinanceCenterTest {
    @Test fun linkedManualPaymentDoesNotDoubleCountImportedRepayment() {
        val imported=Transaction(
            id=7,platform=Platform.WECHAT,occurredAt=1700000000000L,
            counterparty="招商银行",description="还款",directionText="支出",
            amountCent=120000,flowType=FlowType.CREDIT_REPAYMENT,category="信用卡还款",
            paymentMethod="",transactionId="abc",merchantOrderId="",
            sourceFile="",fingerprint="abc"
        )
        val manual=ManualCreditRepayment(1,"招商银行",120000,1700000000000L,"",7)
        val center=CreditCenter(imported=listOf(imported),manual=listOf(manual))
        assertEquals(120000L,center.totalCent)
        assertEquals(1,center.transactionCount)
        assertFalse(manual.countsAsRepayment)
    }
    @Test fun unlinkedManualRepaymentIsCountedWhenNoSourceTransactionExists() {
        val center=CreditCenter(manual=listOf(
            ManualCreditRepayment(1,"建设银行",100000,1700000000000L,"银行卡自动扣款")
        ))
        assertEquals(100000L,center.totalCent)
        assertEquals(1,center.transactionCount)
    }
}
