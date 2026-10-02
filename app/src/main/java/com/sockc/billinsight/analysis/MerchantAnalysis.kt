package com.sockc.billinsight.analysis

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.importer.ScanPaymentClassifier

data class MerchantGroup(
    val key: String,
    val name: String,
    val count: Int,
    val amountCent: Long,
    val transactions: List<Transaction>,
)

/** Only identifiable purchase merchants appear in the purchase leaderboard. */
object MerchantAnalysis {
    private val nonMerchants = listOf(
        "转账备注", "收款方备注", "收款备注", "个人转账", "二维码收款", "微信转账"
    )
    private val paymentPrefix = setOf("先用后付", "花呗", "信用卡", "付款码", "微信支付", "支付宝")
    private val unknown = setOf("", "/", "-", "--", "未知", "未知商户", "商户消费", "二维码付款")

    fun merchantName(tx: Transaction): String? {
        if (tx.flowType != FlowType.EXPENSE) return null
        val raw = tx.counterparty.trim()
        if (raw.isEmpty() || nonMerchants.any { raw.startsWith(it) }) return null
        val qrPurchase=ScanPaymentClassifier.isQrExpense(tx)
        if (!qrPurchase && (tx.tradeType.contains("收款") ||
            tx.tradeType.contains("转账") || tx.tradeType.contains("红包"))) return null
        val pieces = raw.split(Regex("""\s*[·|｜]\s*""")).filter { it.isNotBlank() }
        val filtered = pieces.filter { it !in paymentPrefix }
        val candidate = when {
            filtered.isEmpty() -> raw
            filtered.size == 1 -> filtered.single()
            filtered[0].contains(filtered[1], ignoreCase = true) -> filtered[1]
            filtered[1].contains(filtered[0], ignoreCase = true) -> filtered[0]
            else -> filtered.first()
        }.trim().replace(Regex("""\s+"""), " ")
        if (candidate in unknown || candidate.isBlank() || candidate.startsWith("收款方备注")) return null
        return when {
            candidate.contains("拼多多平台商户") -> "拼多多"
            else -> candidate
        }
    }

    fun groups(
        transactions: List<Transaction>,
        aliases: Map<String,String> = emptyMap(),
        labels: Map<Long,String> = emptyMap(),
    ): List<MerchantGroup> {
        val mapped = transactions.mapNotNull { tx ->
            (labels[tx.id] ?: merchantName(tx))?.let { original ->
                val merged = aliases[original.trim().lowercase()]?.trim()?.takeIf { it.isNotBlank() } ?: original
                merged.lowercase() to (merged to tx)
            }
        }
        return mapped.groupBy({ it.first }, { it.second })
            .map { (key, entries) ->
                val records = entries.map { it.second }.sortedByDescending { it.occurredAt }
                MerchantGroup(
                    key = key, name = entries.first().first,
                    count = records.size, amountCent = records.sumOf { it.amountCent },
                    transactions = records,
                )
            }.sortedWith(compareByDescending<MerchantGroup> { it.amountCent }.thenBy { it.name })
    }
}
