package com.sockc.billinsight.analysis

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Transaction

/** Financial purpose survives a later edit to the consumption category. */
object TransferPolicy {
    fun personDirection(tx: Transaction): String? {
        if (tx.flowType !in setOf(FlowType.EXPENSE, FlowType.INCOME)) return null
        val evidence = (tx.tradeType + " " + tx.description).lowercase()
        val originalType = tx.tradeType.lowercase()
        val self = tx.counterparty.lowercase() + " " + evidence
        if (listOf("余额宝", "零钱通", "本人账户", "我的账户", "账户互转",
                "资金划转", "余利宝", "提现到", "信用卡还款", "贷款还款")
                .any { self.contains(it) }) return null
        if (listOf("退款", "退回", "还款", "借款", "红包").any { evidence.contains(it) })
            return null
        if (originalType.contains("二维码") || originalType.contains("扫码")) return null
        val explicitlyTransfer = originalType.contains("转账") ||
                (originalType.isBlank() && evidence.contains("转账"))
        val legacy = tx.category == "转账支出" || tx.category == "转账收入"
        if (!explicitlyTransfer && !legacy) return null
        return when {
            tx.flowType == FlowType.INCOME && tx.directionText.contains("收入") -> "IN"
            tx.flowType == FlowType.EXPENSE && tx.directionText.contains("支出") -> "OUT"
            else -> null
        }
    }
}

data class TransferPerson(
    val key: String,
    val name: String,
    val platforms: String,
    val sentCent: Long,
    val receivedCent: Long,
    val sentCount: Int,
    val receivedCount: Int,
) {
    val count: Int get() = sentCount + receivedCount
    val totalCent: Long get() = sentCent + receivedCent
    val differenceCent: Long get() = receivedCent - sentCent
}

data class TransferTotals(
    val sentCent: Long = 0,
    val receivedCent: Long = 0,
    val sentCount: Int = 0,
    val receivedCount: Int = 0,
) {
    val totalCent: Long get() = sentCent + receivedCent
    val differenceCent: Long get() = receivedCent - sentCent
}
