package com.sockc.billinsight.model

data class ManualCreditRepayment(
    val id: Long,
    val cardName: String,
    val amountCent: Long,
    val occurredAt: Long,
    val note: String,
    val linkedTransactionId: Long? = null,
) {
    val countsAsRepayment: Boolean get() = linkedTransactionId == null
}

data class CreditCenter(
    val imported: List<Transaction> = emptyList(),
    val manual: List<ManualCreditRepayment> = emptyList(),
    val suspected: List<Transaction> = emptyList(),
    val aliases: Map<String,String> = emptyMap(),
) {
    val importedCent: Long get() = imported.sumOf { it.amountCent }
    val manualCent: Long get() = manual.filter { it.countsAsRepayment }.sumOf { it.amountCent }
    val totalCent: Long get() = importedCent + manualCent
    val transactionCount: Int get() = imported.size +
        manual.count { it.countsAsRepayment }
}

data class LoanProfile(
    val institution: String,
    val originalAmountCent: Long? = null,
    val remainingPrincipalCent: Long? = null,
    val repaidCent: Long = 0,
    val principalCent: Long = 0,
    val interestCent: Long = 0,
    val feeCent: Long = 0,
    val missingSplitCount: Int = 0,
    val transactionCount: Int = 0,
)

data class ImportPreview(
    val sourceName: String,
    val platform: Platform,
    val transactions: List<Transaction>,
    val newCount: Int,
    val duplicateCount: Int,
    val pendingCount: Int,
    val creditRepaymentCount: Int,
    val invalidAmountCount: Int,
    val invalidTimeCount: Int,
    val startAt: Long?,
    val endAt: Long?,
    val qrExpenseCount: Int = 0,
    val qrMerchantReviewCount: Int = 0,
) {
    val total: Int get() = transactions.size
    val canCommit: Boolean get() = total > 0 && platform != Platform.UNKNOWN &&
        invalidTimeCount == 0
    val requiresAmountConfirmation: Boolean get() = invalidAmountCount > 0
    val invalidAmountSamples: List<Transaction> get() =
        transactions.filter { it.amountCent<=0L }.take(12)
    val samples: List<Transaction> get() = transactions.take(12)
}

data class MerchantRule(
    val merchant: String,
    val category: String,
    val affectedCount: Int = 0,
)

data class TrendPoint(
    val label: String,
    val startAt: Long,
    val endAt: Long,
    val expenseCent: Long,
)
