package com.sockc.billinsight.analysis

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test

class CounterpartyAnalysisTest {
    private fun row(id:Long,name:String,platform:Platform,category:String,
                    amount:Long,flow:FlowType):Transaction=Transaction(
        id=id,platform=platform,occurredAt=1_700_000_000_000L+id,
        counterparty=name,description=category,directionText=if(flow==FlowType.INCOME)"收入" else "支出",
        tradeType="转账",amountCent=amount,flowType=flow,category=category,
        paymentMethod="",transactionId="",merchantOrderId="",sourceFile="",fingerprint="tx"+id
    )

    @Test fun sameNameAcrossPlatformsStaysSeparate() {
        val rows=listOf(
            row(1,"张三",Platform.WECHAT,"转账收入",10000,FlowType.INCOME),
            row(2,"张三",Platform.WECHAT,"转账支出",5000,FlowType.EXPENSE),
            row(3,"张三",Platform.ALIPAY,"转账收入",20000,FlowType.INCOME)
        )
        val grouped=CounterpartyAnalysis.people(rows)
        assertEquals(2,grouped.size)
        val wechat=grouped.single { it.platform==Platform.WECHAT }
        assertEquals(10000L,wechat.receivedCent)
        assertEquals(5000L,wechat.sentCent)
        assertEquals(5000L,wechat.differenceCent)
        assertEquals(1,wechat.receivedCount)
        assertEquals(1,wechat.sentCount)
    }

    @Test fun shoppingViaFriendsQrDoesNotBecomeTransfer() {
        val rows=listOf(
            row(1,"张三",Platform.WECHAT,"餐饮",1200,FlowType.EXPENSE),
            row(2,"张三",Platform.WECHAT,"转账支出",3000,FlowType.EXPENSE)
        )
        val people=CounterpartyAnalysis.people(rows)
        assertEquals(1,people.single().sentCount)
        assertEquals(3000L,people.single().sentCent)
        assertEquals(1200L,CounterpartyAnalysis.expenseByCategory(rows)["餐饮"])
    }

    @Test fun matchedRecoveryAndRefundDoNotAppearAsIncomeSource() {
        val rows=listOf(
            row(1,"王五",Platform.WECHAT,"转账收入",7000,FlowType.INCOME),
            row(2,"王五",Platform.WECHAT,"收入",1000,FlowType.INCOME),
            row(3,"商店",Platform.ALIPAY,"退款",500,FlowType.REFUND)
        )
        val sources=CounterpartyAnalysis.incomeSources(rows,setOf(2))
        assertEquals(1,sources.size)
        assertEquals(7000L,sources.single().receivedCent)
    }
}
