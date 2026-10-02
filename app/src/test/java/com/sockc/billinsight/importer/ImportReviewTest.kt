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
    @Test fun invalidDateBlocksImportInsteadOfTurningIntoToday() {
        val invalid=row("bad-date").copy(occurredAt=0L)
        val preview=ImportReview.preview(
            BillImporter.ParsedBill("bad.csv",Platform.WECHAT,listOf(invalid)),
            emptySet()
        )
        assertFalse(preview.canCommit)
        assertEquals(1,preview.invalidTimeCount)
    }
    @Test fun qrExpenseAndUnnamedMerchantAppearInPreview() {
        val generic=row("qr-generic").copy(
            counterparty="二维码付款",tradeType="二维码付款",
            description="扫码付款",category="其他"
        )
        val friend=row("qr-friend").copy(
            counterparty="张三",tradeType="扫码支付",description="早餐",
            category="餐饮"
        )
        val ordinary=row("transfer").copy(
            flowType=FlowType.PENDING,tradeType="转账",
            counterparty="李四",description="转账"
        )
        val preview=ImportReview.preview(
            BillImporter.ParsedBill(
                "wechat.csv",Platform.WECHAT,listOf(generic,friend,ordinary)
            ),emptySet()
        )
        assertEquals(2,preview.qrExpenseCount)
        assertEquals(1,preview.qrMerchantReviewCount)
        assertEquals(3,preview.newCount)
    }
}
