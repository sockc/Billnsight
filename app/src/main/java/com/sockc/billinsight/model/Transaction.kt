package com.sockc.billinsight.model

enum class Platform { WECHAT, ALIPAY, JD, DOUYIN, MEITUAN, BANK, UNKNOWN }
enum class FlowType {
    EXPENSE, INCOME, TRANSFER, REFUND, IGNORE,
    PENDING, GIFT_EXPENSE, GIFT_INCOME, LOAN_OUT, LOAN_RECOVERY,
    BUSINESS_EXPENSE, BUSINESS_INCOME, CREDIT_REPAYMENT, LOAN_REPAYMENT, LOAN_DISBURSEMENT,
}

fun FlowType.displayName(): String = when (this) {
    FlowType.EXPENSE -> "个人消费"
    FlowType.INCOME -> "个人收入"
    FlowType.TRANSFER -> "资金流转"
    FlowType.REFUND -> "退款/退回"
    FlowType.IGNORE -> "忽略"
    FlowType.PENDING -> "待确认"
    FlowType.GIFT_EXPENSE -> "红包支出"
    FlowType.GIFT_INCOME -> "红包收入"
    FlowType.LOAN_OUT -> "借出款"
    FlowType.LOAN_RECOVERY -> "借款收回"
    FlowType.BUSINESS_EXPENSE -> "经营支出"
    FlowType.BUSINESS_INCOME -> "经营收款"
    FlowType.CREDIT_REPAYMENT -> "信用卡还款"
    FlowType.LOAN_REPAYMENT -> "贷款还款"
    FlowType.LOAN_DISBURSEMENT -> "贷款到账"
}

fun FlowType.countsAsExpense(): Boolean =
    this == FlowType.EXPENSE || this == FlowType.GIFT_EXPENSE

data class Transaction(
    val id: Long = 0,
    val platform: Platform,
    val occurredAt: Long,
    val counterparty: String,
    val description: String,
    val directionText: String,
    val tradeType: String = "",
    val amountCent: Long,
    val flowType: FlowType,
    val category: String,
    val paymentMethod: String,
    val transactionId: String,
    val merchantOrderId: String,
    val sourceFile: String,
    val fingerprint: String,
)

data class CategoryTotal(val category: String, val amountCent: Long, val count: Int)
data class MerchantTotal(val merchant: String, val amountCent: Long, val count: Int)
/** Preview uses the exact same merchant grouping as the save operation. */
data class CategoryEditPreview(
    val eligibleCount: Int = 0,
    val protectedCount: Int = 0,
    val variantCount: Int = 0,
)

data class PlatformCategoryRule(val platform: Platform, val merchant: String, val category: String, val affectedCount: Int)

data class DailyTotal(val dayOfMonth: Int, val amountCent: Long, val count: Int)

data class RecurringExpense(
    val merchant: String,
    val averageMonthlyCent: Long,
    val latestMonthCent: Long,
    val activeMonths: Int,
    val transactionCount: Int,
)

data class DashboardSummary(
    val expenseCent: Long = 0,
    val incomeCent: Long = 0,
    val refundCent: Long = 0,
    val transferCent: Long = 0,
    val withdrawalCent: Long = 0,
    val transactionCount: Int = 0,
    val smallExpenseCent: Long = 0,
    val smallExpenseCount: Int = 0,
    val creditFundedExpenseCent: Long = 0,
    val creditRepaymentCent: Long = 0,
    val creditRepaymentCount: Int = 0,
    val loanRepaymentCent: Long = 0,
    val loanRepaymentCount: Int = 0,
    val loanDisbursementCent: Long = 0,
    val loanPrincipalCent: Long = 0,
    val loanInterestCent: Long = 0,
    val loanFeeCent: Long = 0,
    val loanUnallocatedCent: Long = 0,
    val businessExpenseCent: Long = 0,
    val loanOutCent: Long = 0,
    val linkedRefundCent: Long = 0,
    val linkedShareCent: Long = 0,
    val pendingCount: Int = 0,
    val giftExpenseCent: Long = 0,
    val giftIncomeCent: Long = 0,
    val giftExpenseCount: Int = 0,
    val giftIncomeCount: Int = 0,
) {
    val loanFinanceCostCent: Long get() = loanInterestCent + loanFeeCent
    // Only explicitly credit-card-funded purchases are excluded from today's
    // actual money outflow; they remain part of consumer spending. Repayments
    // are counted when paid, never twice for an identified card purchase.
    val cashOutflowCent: Long get() =
        ((expenseCent - loanFinanceCostCent - creditFundedExpenseCent).coerceAtLeast(0L) +
            creditRepaymentCent + loanRepaymentCent + businessExpenseCent + loanOutCent)
    val netExpenseCent: Long get() = (expenseCent - linkedRefundCent - linkedShareCent).coerceAtLeast(0)
    val shoppingConsumptionCent: Long get() =
        (netExpenseCent - loanFinanceCostCent).coerceAtLeast(0L)
}

data class ImportResult(
    val parsed: Int,
    val inserted: Int,
    val duplicated: Int,
    val ignored: Int,
    val platform: Platform,
    val sourceName: String,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val qrExpenseCount: Int = 0,
    val qrMerchantReviewCount: Int = 0,
)

data class ProductGroup(
    val merchant: String,
    val product: String,
    val amountCent: Long,
    val count: Int,
    val transactions: List<Transaction>,
    val unspecified: Boolean = false,
)

/** A receipt is attributed to an original expense without rewriting either imported transaction. */
data class ExpenseLink(
    val id: Long,
    val expenseId: Long,
    val receiptId: Long,
    val kind: LinkKind,
    val amountCent: Long,
)

enum class LinkKind { REFUND, SHARE }

/** Optional manual detail for one imported repayment; original transaction is immutable. */
data class LoanRepaymentDetail(
    val transactionId: Long,
    val principalCent: Long,
    val interestCent: Long,
    val feeCent: Long,
) {
    val totalCent: Long get() = principalCent + interestCent + feeCent
    val financeCostCent: Long get() = interestCent + feeCent
}

object LoanRepaymentPolicy {
    fun validate(totalCent: Long, principalCent: Long, interestCent: Long, feeCent: Long) {
        require(totalCent > 0 && principalCent >= 0 && interestCent >= 0 && feeCent >= 0) {
            "还款金额及拆分金额不能为负数"
        }
        require(principalCent <= totalCent && interestCent <= totalCent &&
            feeCent <= totalCent && principalCent <= totalCent - interestCent &&
            feeCent == totalCent - principalCent - interestCent) {
            "本金、利息及手续费的总和必须等于本次还款金额"
        }
    }
}
