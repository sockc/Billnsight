package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditRepaymentDetectorTest {
    @Test fun clearRepaymentVariants() {
        for (label in listOf(
            "信用卡还款", "信用卡账单还款", "信用卡自动还款", "招商银行信用卡本期还款",
            "还信用卡", "还款至信用卡", "还款到贷记卡", "信用卡分期还款",
            "贷记卡代扣还款", "支付至招商银行信用卡", "本期账单支付到信用卡",
        )) {
            assertTrue(label,CreditRepaymentDetector.isRepayment("支出",label,"银行",""))
        }
    }

    @Test fun merchantOnlyAndGenericTradeTypeAreSupported() {
        assertTrue(CreditRepaymentDetector.isRepayment(
            "支出","转账","信用卡还款服务","银行卡还款"
        ))
        assertTrue(CreditRepaymentDetector.isRepayment(
            "支出","转账","招商银行信用卡中心","账单还款"
        ))
        assertTrue(CreditRepaymentDetector.isRepayment(
            "不计收支","商户消费","建设银行信用卡还款",""
        ))
        assertTrue(CreditRepaymentDetector.isRepayment(
            "支出","银行转账","招行贷记卡","自动代扣"
        ))
    }

    @Test fun cannotMistakeCardPurchaseForRepayment() {
        for (label in listOf(
            "信用卡支付", "信用卡消费", "信用卡刷卡", "信用卡付款", "信用卡分期消费",
            "信用卡手续费", "信用卡账单查询"
        )) {
            assertFalse(label,CreditRepaymentDetector.isRepayment("支出","商户消费","便利店",label,"信用卡"))
        }
        assertFalse(CreditRepaymentDetector.isRepayment(
            "支出","商户消费","招商银行信用卡中心","开通信用卡"
        ))
    }

    @Test fun refundsIncomeAndLoansAreNotRepayments() {
        assertFalse(CreditRepaymentDetector.isRepayment(
            "收入","信用卡还款","招商银行",""
        ))
        assertFalse(CreditRepaymentDetector.isRepayment(
            "支出","信用卡还款","招商银行","还款退款",status="已退款"
        ))
        assertFalse(CreditRepaymentDetector.isRepayment(
            "支出","贷款还款","建设银行","房贷还款"
        ))
        assertFalse(CreditRepaymentDetector.isRepayment(
            "支出","分期还款","招商银行","消费贷款"
        ))
    }

    @Test fun backfillRequiresUntouchedDefaultCategoryAndNoExistingLinkOrLoanSplit() {
        assertTrue(CreditRepaymentDetector.isSafelyAutoCorrectable(
            FlowType.TRANSFER,"资金流转",false,false
        ))
        assertTrue(CreditRepaymentDetector.isSafelyAutoCorrectable(
            FlowType.EXPENSE,"其他",false,false
        ))
        assertTrue(CreditRepaymentDetector.isSafelyAutoCorrectable(
            FlowType.LOAN_REPAYMENT,"贷款还款",false,false
        ))
        assertFalse(CreditRepaymentDetector.isSafelyAutoCorrectable(
            FlowType.EXPENSE,"餐饮",false,false
        ))
        assertFalse(CreditRepaymentDetector.isSafelyAutoCorrectable(
            FlowType.LOAN_REPAYMENT,"贷款还款",true,false
        ))
        assertFalse(CreditRepaymentDetector.isSafelyAutoCorrectable(
            FlowType.TRANSFER,"资金流转",false,true
        ))
        assertFalse(CreditRepaymentDetector.isSafelyAutoCorrectable(
            FlowType.IGNORE,"忽略",false,false
        ))
    }
}
