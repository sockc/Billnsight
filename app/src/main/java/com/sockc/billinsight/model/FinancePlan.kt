package com.sockc.billinsight.model

/** Finance metadata is kept separate from imported and immutable transaction rows. */
enum class FinancePlanKind { OWN, ADVANCE }
enum class FinanceLinkRole { ORIGIN, REPAYMENT, RECOVERY }

data class FinancePlan(
    val id: Long,
    val originTransactionId: Long,
    val platform: Platform,
    val institution: String,
    val title: String,
    val kind: FinancePlanKind,
    val beneficiary: String,
    /** Only an explicitly labelled, shared installment-plan identifier is trusted. */
    val planReference: String?,
    /** NULL means unknown; never infer a whole loan from an individual installment. */
    val totalCent: Long?,
    val termCount: Int?,
    val dueDay: Int?,
    val links: List<FinancePlanLink>,
) {
    val repayments: List<FinancePlanLink> get() =
        links.filter { it.role == FinanceLinkRole.REPAYMENT }
    val recoveries: List<FinancePlanLink> get() =
        links.filter { it.role == FinanceLinkRole.RECOVERY }
    val repaidCent: Long get() = repayments.sumOf { it.transaction.amountCent }
    val recoveredCent: Long get() = recoveries.sumOf { it.transaction.amountCent }
    val remainingCent: Long? get() = totalCent?.let { (it - repaidCent).coerceAtLeast(0) }
    val receivableCent: Long? get() = if (kind == FinancePlanKind.ADVANCE)
        totalCent?.let { (it - recoveredCent).coerceAtLeast(0) } else null
}

data class FinancePlanLink(
    val transaction: Transaction,
    val role: FinanceLinkRole,
    val autoLinked: Boolean,
)

data class FinanceLinkSuggestion(
    val transaction: Transaction,
    val reason: String,
    val exactReference: Boolean,
)
