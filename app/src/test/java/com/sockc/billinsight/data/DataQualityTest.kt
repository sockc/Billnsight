package com.sockc.billinsight.data

import com.sockc.billinsight.model.FlowType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataQualityTest {
    @Test fun bulkOnlyAllowsSameDirectionAndPending() {
        val rows = listOf(
            ReviewCandidate(1,"支出",FlowType.PENDING),
            ReviewCandidate(2,"支出",FlowType.PENDING)
        )
        BulkReviewPolicy.validate(rows,FlowType.EXPENSE)
        expectRejected { BulkReviewPolicy.validate(rows,FlowType.INCOME) }
        expectRejected { BulkReviewPolicy.validate(rows + ReviewCandidate(3,"收入",FlowType.PENDING),FlowType.EXPENSE) }
        expectRejected { BulkReviewPolicy.validate(rows + ReviewCandidate(4,"支出",FlowType.EXPENSE),FlowType.EXPENSE) }
        expectRejected { BulkReviewPolicy.validate(rows.take(1)+rows.take(1),FlowType.EXPENSE) }
    }
    @Test fun diagnosticsDistinguishActionableErrorsFromFollowup() {
        val clean = DataAuditReport(2,1,1,0,0,0,0,0,true)
        assertFalse(clean.needsAttention)
        assertTrue(clean.hasFollowUp)
        assertTrue(clean.copy(brokenLinks=1).needsAttention)
        assertTrue(clean.copy(databaseIntegrityOk=false).needsAttention)
    }
    private fun expectRejected(block: () -> Unit) {
        var rejected = false
        try { block() } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
}
