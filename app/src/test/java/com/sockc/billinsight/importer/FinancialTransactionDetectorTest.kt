package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import org.junit.Assert.*
import org.junit.Test

class FinancialTransactionDetectorTest {
    @Test fun alipayRepaymentPreviouslyIgnored() {
        val c=TransactionClassifier.classify("不计收支","不计收支",
            "花呗|信用购","自动还款-花呗|信用购2023年07月账单",
            "交易成功",emptyMap())
        assertEquals(FlowType.CREDIT_REPAYMENT,c.flowType)
        assertEquals("花呗/信用购还款",c.category)
        assertEquals("2023年07月账单",
            FinancialTransactionDetector.billingMonth("自动还款-花呗|信用购2023年07月账单"))
    }
    @Test fun internetBankTransferToYulibaoIsNotConsumption() {
        val c=TransactionClassifier.classify("不计收支","不计收支",
            "网商银行","支付宝转入到余利宝","交易成功",emptyMap())
        assertEquals(FlowType.TRANSFER,c.flowType)
        assertEquals("余利宝资金流转",c.category)
    }
    @Test fun refuseAmbiguousOrFailedPayments() {
        assertNull(FinancialTransactionDetector.detect("不计收支","账单",
            "花呗","自动还款失败"))
        assertNull(FinancialTransactionDetector.detect("支出","商品购买",
            "商家","用花呗付款购买商品"))
        assertNull(FinancialTransactionDetector.detect("收入","收益",
            "网商银行","余利宝收益"))
        assertNull(FinancialTransactionDetector.detect("支出","转账",
            "网商银行","转给张三"))
    }
}
