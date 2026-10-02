package com.sockc.billinsight.model

enum class Platform { WECHAT, ALIPAY, UNKNOWN }
enum class FlowType {
    EXPENSE, INCOME, TRANSFER, REFUND, IGNORE,
    PENDING, GIFT_EXPENSE, GIFT_INCOME, LOAN_OUT, LOAN_RECOVERY,
    BUSINESS_EXPENSE, BUSINESS_INCOME,
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
    val transactionCount: Int = 0,
    val smallExpenseCent: Long = 0,
    val smallExpenseCount: Int = 0,
    val pendingCount: Int = 0,
    val giftExpenseCent: Long = 0,
    val giftIncomeCent: Long = 0,
    val giftExpenseCount: Int = 0,
    val giftIncomeCount: Int = 0,
)

data class ImportResult(
    val parsed: Int,
    val inserted: Int,
    val duplicated: Int,
    val ignored: Int,
    val platform: Platform,
    val sourceName: String,
    val startAt: Long? = null,
    val endAt: Long? = null,
)
