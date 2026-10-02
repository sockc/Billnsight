package com.sockc.billinsight.ui.screens

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DailyLedgerTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private fun tx(year:Int,month:Int,day:Int,hour:Int,id:Long): Transaction=Transaction(
        id=id,platform=Platform.WECHAT,
        occurredAt=LocalDateTime.of(year,month,day,hour,0).atZone(zone).toInstant().toEpochMilli(),
        counterparty="商家",description="",directionText="支出",
        amountCent=100,flowType=FlowType.EXPENSE,category="购物",
        paymentMethod="",transactionId="",merchantOrderId="",sourceFile="",fingerprint="f$id"
    )
    @Test fun groupingUsesLocalCalendarDaysEvenAcrossMonths() {
        val days=DailyLedger.group(
            listOf(tx(2026,10,1,0,1),tx(2026,9,30,23,2),tx(2026,10,1,18,3)),zone
        )
        assertEquals(2,days.size)
        assertEquals(2,days.first().second.size)
        assertEquals(2026,days.first().first.year)
        assertEquals(10,days.first().first.monthValue)
    }
}
