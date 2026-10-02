package com.sockc.billinsight.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimeUtilsTest {
    @Test fun malformedFinancialTimestampIsRejected() {
        assertNull(parseDateTimeOrNull("不是日期"))
        assertNull(parseDateTimeOrNull(""))
    }
    @Test fun standardDateIsParsed() {
        val result=parseDateTimeOrNull("2026-10-02 18:05:00")
        assertNotNull(result)
        assertEquals(2026,Instant.ofEpochMilli(result!!).atZone(ZoneId.systemDefault()).year)
    }
}
