package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.ImportPreview
import com.sockc.billinsight.model.Transaction

/** Pure, testable preview. Nothing is persisted until the user confirms. */
object ImportReview {
    fun preview(parsed: BillImporter.ParsedBill, existing: Set<String>): ImportPreview {
        // Count both previously imported fingerprints and duplicates within this file.
        val unique=parsed.transactions.map { it.fingerprint }.toSet()
        val newCount=unique.count { it !in existing }
        return ImportPreview(
            sourceName=parsed.sourceName,
            platform=parsed.platform,
            transactions=parsed.transactions,
            newCount=newCount,
            duplicateCount=parsed.transactions.size-newCount,
            pendingCount=parsed.transactions.count { it.flowType==FlowType.PENDING },
            creditRepaymentCount=parsed.transactions.count {
                it.flowType==FlowType.CREDIT_REPAYMENT
            },
            invalidAmountCount=parsed.transactions.count {
                it.amountCent<=0L && it.flowType!=FlowType.IGNORE
            },
            invalidTimeCount=parsed.transactions.count {
                it.occurredAt<=0L && it.flowType!=FlowType.IGNORE
            },
            startAt=parsed.transactions.minOfOrNull { it.occurredAt },
            endAt=parsed.transactions.maxOfOrNull { it.occurredAt }
        )
    }
}
