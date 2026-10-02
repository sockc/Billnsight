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
    @Test fun loanPrincipalIsCashOutflowButInterestIsPersonalExpense() {
        val summary = DashboardSummary(
            expenseCent = 43000,
            loanRepaymentCent = 200000,
            loanPrincipalCent = 170000,
            loanInterestCent = 25000,
            loanFeeCent = 5000,
        )
        assertEquals(213000L,summary.cashOutflowCent)
        assertEquals(43000L,summary.netExpenseCent)
    }

    @Test fun unknownLoanBreakdownDoesNotInventInterest() {
        val summary = DashboardSummary(
            expenseCent = 0,
            loanRepaymentCent = 200000,
            loanUnallocatedCent = 200000,
        )
        assertEquals(200000L,summary.cashOutflowCent)
        assertEquals(0L,summary.netExpenseCent)
    }

    @Test fun loanSplitRequiresExactSumAndNonnegativeEntries() {
        LoanRepaymentPolicy.validate(200000,170000,25000,5000)
        var rejected=false
        try { LoanRepaymentPolicy.validate(200000,170000,20000,5000) }
        catch (_: IllegalArgumentException) { rejected=true }
        org.junit.Assert.assertTrue(rejected)
    }
}
