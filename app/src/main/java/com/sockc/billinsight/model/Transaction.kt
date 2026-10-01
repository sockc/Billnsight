package com.sockc.billinsight.model

enum class Platform { WECHAT, ALIPAY, UNKNOWN }
enum class FlowType { EXPENSE, INCOME, TRANSFER, REFUND, IGNORE }

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
