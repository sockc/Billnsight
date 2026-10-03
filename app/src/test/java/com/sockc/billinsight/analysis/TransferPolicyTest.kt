package com.sockc.billinsight.analysis

import com.sockc.billinsight.model.*
import org.junit.Assert.*
import org.junit.Test

class TransferPolicyTest {
    private fun bill(type:String,description:String="付款",category:String="其他",
                     flow:FlowType=FlowType.EXPENSE) = Transaction(
        id=1,platform=Platform.WECHAT,occurredAt=1,counterparty="张三",
        description=description,directionText=if(flow==FlowType.INCOME)"收入" else "支出",
        tradeType=type,amountCent=2000,flowType=flow,category=category,
        paymentMethod="",transactionId="",merchantOrderId="",sourceFile="wx",
        fingerprint="x"
    )
    @Test fun explicitPurposeSurvivesChangedConsumerCategory() {
        assertEquals("OUT",TransferPolicy.personDirection(bill("转账","午餐","餐饮")))
        assertEquals("IN",TransferPolicy.personDirection(
            bill("转账","收入","其他",FlowType.INCOME)))
    }
    @Test fun genericQrAndInternalFundsAreNotHumanTransfers() {
        assertNull(TransferPolicy.personDirection(bill("扫码付款","转账支出","餐饮")))
        assertNull(TransferPolicy.personDirection(bill("转账","转到余额宝")))
        assertNull(TransferPolicy.personDirection(bill("转账","微信零钱通")))
    }
    @Test fun oldCategoryCanBeBackfilledOnUpgrade() {
        assertEquals("OUT",TransferPolicy.personDirection(
            bill("","转账给朋友","转账支出")))
    }
}
