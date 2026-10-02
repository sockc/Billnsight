package com.sockc.billinsight.analysis

import com.sockc.billinsight.model.ProductGroup
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.model.countsAsExpense

/** Products are grouped within each merchant across WeChat and Alipay. */
object ProductAnalysis {
    private val generic = setOf(
        "", "商品", "商户消费", "二维码付款", "扫码付款", "扫一扫付款",
        "二维码收款", "微信支付", "支付宝付款", "交易", "订单", "付款", "收款"
    )

    fun normalizeProduct(text: String): String =
        text.trim().replace('（', '(').replace('）', ')')
            .replace(Regex("\\s+"), " ").lowercase()

    fun groups(transactions: List<Transaction>, aliases: Map<String,String> = emptyMap()): List<ProductGroup> {
        val expenses = transactions.filter { it.flowType.countsAsExpense() }
        val grouped = expenses.groupBy { tx ->
            val merchant = tx.counterparty.trim().ifBlank { "未知商户" }
            val product = normalizeProduct(tx.description)
            merchant.lowercase() to if (product in generic) "" else
                normalizeProduct(aliases[merchant.lowercase() + "|" + product] ?: tx.description)
        }
        return grouped.map { (key, entries) ->
            val unspecified = key.second.isEmpty()
            ProductGroup(
                merchant = entries.first().counterparty.trim().ifBlank { "未知商户" },
                product = if (unspecified) "商品未注明" else
                    aliases[entries.first().counterparty.trim().lowercase() + "|" +
                        normalizeProduct(entries.first().description)] ?: entries.first().description.trim(),
                amountCent = entries.sumOf { it.amountCent },
                count = entries.size,
                transactions = entries.sortedByDescending { it.occurredAt },
                unspecified = unspecified,
            )
        }.sortedByDescending { it.amountCent }
    }
}
