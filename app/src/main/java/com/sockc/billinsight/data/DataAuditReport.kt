package com.sockc.billinsight.data

/** Read-only diagnostics. Never alters or deletes imported transactions. */
data class DataAuditReport(
    val totalTransactions: Int,
    val pendingTransactions: Int,
    val unmatchedRefunds: Int,
    val nonPositiveAmounts: Int,
    val directionMismatches: Int,
    val duplicatePlatformOrderIds: Int,
    val brokenLinks: Int,
    val expenseDifferenceCent: Long,
    val databaseIntegrityOk: Boolean,
) {
    val needsAttention: Boolean get() =
        !databaseIntegrityOk || nonPositiveAmounts > 0 || directionMismatches > 0 ||
            duplicatePlatformOrderIds > 0 || brokenLinks > 0 || expenseDifferenceCent != 0L
    val hasFollowUp: Boolean get() = pendingTransactions > 0 || unmatchedRefunds > 0
}
