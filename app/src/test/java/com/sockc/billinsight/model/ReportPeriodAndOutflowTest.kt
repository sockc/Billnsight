package com.sockc.billinsight.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ReportPeriodAndOutflowTest {
    private val today=LocalDate.of(2026,10,3)

    @Test fun lastYearMeansEntirePreviousCalendarYear() {
        assertEquals(
            LocalDate.of(2025,1,1) to LocalDate.of(2025,12,31),
            ReportPeriod.resolve("LAST_YEAR",YearMonth.of(2024,5),today=today)
        )
    }

    @Test fun lastMonthIgnoresPreviouslySelectedHistoricalMonth() {
        assertEquals(
            LocalDate.of(2026,9,1) to LocalDate.of(2026,9,30),
            ReportPeriod.resolve("LAST_MONTH",YearMonth.of(2023,8),today=today)
        )
    }

    @Test fun sevenDaysIncludesToday() {
        assertEquals(
            LocalDate.of(2026,9,27) to today,
            ReportPeriod.resolve("LAST_7",YearMonth.of(2026,10),today=today)
        )
    }

    @Test fun customCanCrossCalendarYears() {
        val start=LocalDate.of(2024,11,15)
        val end=LocalDate.of(2026,2,10)
        assertEquals(start to end,
            ReportPeriod.resolve("CUSTOM",YearMonth.of(2026,10),start,end,today))
    }

    @Test fun rejectsReversedCustomDates() {
        assertThrows(IllegalArgumentException::class.java) {
            ReportPeriod.resolve(
                "CUSTOM",YearMonth.of(2026,10),
                LocalDate.of(2026,10,3),LocalDate.of(2025,1,1),today
            )
        }
    }

    @Test fun historicalFebruaryIncludesLeapDay() {
        assertEquals(
            LocalDate.of(2024,2,1) to LocalDate.of(2024,2,29),
            ReportPeriod.resolve("MONTH",YearMonth.of(2024,2),today=today)
        )
    }

    @Test fun creditCardShoppingCountsAsConsumptionButNotAnotherCashOutflow() {
        val report=DashboardSummary(
            expenseCent=20_000,
            creditFundedExpenseCent=20_000,
            creditRepaymentCent=50_000
        )
        assertEquals(20_000L,report.shoppingConsumptionCent)
        assertEquals(50_000L,report.cashOutflowCent)
    }

    @Test fun loanInterestIsInRepaymentButNotConsumption() {
        val report=DashboardSummary(
            expenseCent=32_000,loanInterestCent=2_000,
            loanRepaymentCent=15_000
        )
        assertEquals(30_000L,report.shoppingConsumptionCent)
        assertEquals(45_000L,report.cashOutflowCent)
    }
}
