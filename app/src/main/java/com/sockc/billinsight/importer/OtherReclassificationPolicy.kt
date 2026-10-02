package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType

/**
 * Only a user-confirmed opt-in may revisit a previously edited "其他" expense.
 * Other hand-selected categories, manual ledger entries and non-consumption
 * flows must never be changed by an automatic word-list update.
 */
object OtherReclassificationPolicy {
    fun eligible(
        flow: FlowType,
        category: String,
        sourceFile: String,
        manuallyModified: Boolean,
        includeReviewedOther: Boolean,
    ): Boolean =
        flow == FlowType.EXPENSE && category == "其他" &&
            sourceFile != "手动记账" &&
            (!manuallyModified || includeReviewedOther)
}
