package com.sockc.billinsight.importer

import com.sockc.billinsight.util.parseAmountToCent
import org.junit.Assert.*
import org.junit.Test

class ImportAmountPolicyTest {
    @Test fun negativeOutflowAndRefundNormalizeWithOriginalNote() {
        val out=ImportAmountPolicy.resolve("-300.19","支出")
        assertEquals(30019L,out.cents)
        assertFalse(out.needsReview)
        assertTrue(out.auditNote.contains("-300.19"))
        val incoming=ImportAmountPolicy.resolve("−12.50","收入")
        assertEquals(1250L,incoming.cents)
        assertFalse(incoming.needsReview)
    }
    @Test fun zeroMalformedAndUnknownDirectionKeepOriginalSignedValue() {
        listOf("0.00" to 0L,"--" to 0L,"-12.35" to -1235L).forEach { (raw,expected) ->
            val r=ImportAmountPolicy.resolve(raw,"不计收支")
            assertEquals(raw,expected,r.cents)
            assertTrue(raw,r.needsReview)
            assertTrue(r.auditNote.contains(raw))
        }
        assertTrue(ImportAmountPolicy.resolve("","支出").needsReview)
    }
    @Test fun localizedSignedValuesAndPositiveRemainExact() {
        assertEquals(-2500L,parseAmountToCent("(25.00)"))
        assertEquals(-100L,parseAmountToCent("−￥1.00"))
        assertEquals(870L,ImportAmountPolicy.resolve("8.70","支出").cents)
    }
    @Test fun extremeNegativeIsNotOverflownByAbs() {
        val r=ImportAmountPolicy.resolve("-92233720368547758.08","支出")
        assertEquals(Long.MIN_VALUE,r.cents)
        assertTrue(r.needsReview)
    }
}
