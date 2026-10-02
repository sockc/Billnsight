package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ImportReviewTest {
    private fun row(fingerprint:String, amount:Long=1000):Transaction=Transaction(
        platform=Platform.WECHAT,occurredAt=1700000000000,
        counterparty="超市",description="购物",directionText="支出",
        amountCent=amount,flowType=FlowType.EXPENSE,category="购物",
        paymentMethod="",transactionId="",merchantOrderId="",
        sourceFile="bill.csv",fingerprint=fingerprint
    )
    @Test fun duplicateChecksIncludeExistingAndWithinSameFile() {
        val original=BillImporter.ParsedBill(
            "bill.csv",Platform.WECHAT,listOf(row("first"),row("second"),row("second"))
        )
        val p=ImportReview.preview(original,setOf("first"))
        assertEquals(1,p.newCount)
        assertEquals(2,p.duplicateCount)
        assertEquals(3,p.total)
    }
    @Test fun invalidAmountsRequireCorrectionBeforeImport() {
        val p=ImportReview.preview(
            BillImporter.ParsedBill("bad.csv",Platform.WECHAT,listOf(row("bad",0))),emptySet()
        )
        assertFalse(p.canCommit)
        assertEquals(1,p.invalidAmountCount)
    }
}
