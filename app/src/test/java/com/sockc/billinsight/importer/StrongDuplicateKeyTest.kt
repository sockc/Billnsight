package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import org.junit.Assert.*
import org.junit.Test

class StrongDuplicateKeyTest {
    private fun tx(
        platform: Platform,
        order: String = "202609280000012345",
        amount: Long = 2550,
        time: Long = 1790553600000L,
        flow: FlowType = FlowType.EXPENSE,
    ) = Transaction(
        platform = platform, occurredAt = time, counterparty = "测试商户",
        description = "商品", directionText = if(flow==FlowType.INCOME)"收入" else "支出",
        amountCent = amount, flowType = flow, category = "购物",
        paymentMethod = "", transactionId = "", merchantOrderId = order,
        sourceFile = "", fingerprint = platform.name + order
    )

    @Test fun exactSharedOrderMatchesAcrossPlatforms() {
        assertTrue(StrongDuplicateKey.isSamePayment(tx(Platform.JD),tx(Platform.WECHAT)))
    }
    @Test fun sameAmountTimeButDifferentOrderMustNotMatch() {
        assertFalse(StrongDuplicateKey.isSamePayment(
            tx(Platform.MEITUAN),tx(Platform.ALIPAY,order="202609280000099999")))
    }
    @Test fun noIdentifierMustNotMatch() {
        assertFalse(StrongDuplicateKey.isSamePayment(
            tx(Platform.DOUYIN,order=""),tx(Platform.ALIPAY,order="")))
    }
    @Test fun maskedOrderMustNotMatch() {
        assertFalse(StrongDuplicateKey.isSamePayment(
            tx(Platform.DOUYIN,order="202609******1234"),
            tx(Platform.ALIPAY,order="202609******1234")))
    }
    @Test fun incomeAndExpenseMustNotMatch() {
        assertFalse(StrongDuplicateKey.isSamePayment(
            tx(Platform.MEITUAN),tx(Platform.WECHAT,flow=FlowType.INCOME)))
    }
    @Test fun sameSourceMustNotMatch() {
        assertFalse(StrongDuplicateKey.isSamePayment(tx(Platform.JD),tx(Platform.JD)))
    }
    @Test fun distantTransactionsMustNotMatch() {
        assertFalse(StrongDuplicateKey.isSamePayment(
            tx(Platform.JD),tx(Platform.ALIPAY,time=1790553600000L + 8L*86400000)))
    }
}
