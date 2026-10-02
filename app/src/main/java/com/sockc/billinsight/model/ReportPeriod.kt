package com.sockc.billinsight.model

import java.time.LocalDate
import java.time.YearMonth

/** Inclusive calendar dates. Database queries use start midnight to end + 1 midnight. */
object ReportPeriod {
    fun resolve(
        key: String,
        month: YearMonth,
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null,
        today: LocalDate = LocalDate.now(),
    ): Pair<LocalDate,LocalDate> = when(key) {
        "MONTH" -> month.atDay(1) to month.atEndOfMonth()
        "LAST_MONTH" -> YearMonth.from(today).minusMonths(1).let {
            it.atDay(1) to it.atEndOfMonth()
        }
        "LAST_7" -> today.minusDays(6) to today
        "YEAR" -> LocalDate.of(today.year,1,1) to today
        "LAST_YEAR" -> LocalDate.of(today.year-1,1,1) to
            LocalDate.of(today.year-1,12,31)
        "ALL_HISTORY" -> LocalDate.of(1900,1,1) to LocalDate.of(2100,12,31)
        "CUSTOM" -> {
            require(customStart!=null && customEnd!=null && !customStart.isAfter(customEnd))
            customStart to customEnd
        }
        else -> error("Unsupported report period: $key")
    }
}
