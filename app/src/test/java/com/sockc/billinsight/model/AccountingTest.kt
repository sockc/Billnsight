package com.sockc.billinsight.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountingTest {
    @Test fun repaymentCountsAsSpendingButNotNetConsumption() {
        val summary = DashboardSummary(
            expenseCent = 29900,
            creditRepaymentCent = 29900,
            linkedRefundCent = 9900,
            linkedShareCent = 6000,
        )
        assertEquals(59800L, summary.cashOutflowCent)
        assertEquals(14000L, summary.netExpenseCent)
    }

    @Test fun recoveryCannotMakeNetExpenseNegative() {
        val summary = DashboardSummary(expenseCent = 100, linkedRefundCent = 200)
        assertEquals(0L, summary.netExpenseCent)
    }
}
