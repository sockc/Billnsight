package com.sockc.billinsight.analysis

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction

/** Group by exact recorded name and payment platform: identical names on different
 * platforms do NOT prove that the payee is the same person. */
data class CounterpartySummary(
    val name: String,
    val platform: Platform,
    val receivedCent: Long,
    val sentCent: Long,
    val receivedCount: Int,
    val sentCount: Int,
    val records: List<Transaction>,
) {
    val differenceCent: Long get() = receivedCent - sentCent
}

object CounterpartyAnalysis {
    private fun name(tx: Transaction): String =
        tx.counterparty.trim().ifBlank {
            if (tx.directionText.contains("收入")) "未知付款人" else "未知收款人"
        }

    private fun isTransferIn(tx: Transaction): Boolean =
        tx.flowType == FlowType.INCOME && tx.category == "转账收入"

    private fun isTransferOut(tx: Transaction): Boolean =
        tx.flowType == FlowType.EXPENSE && tx.category == "转账支出"

    fun people(rows: List<Transaction>): List<CounterpartySummary> =
        rows.filter { isTransferIn(it) || isTransferOut(it) }
            .groupBy { name(it) to it.platform }
            .map { (key, list) ->
                val incoming = list.filter(::isTransferIn)
                val outgoing = list.filter(::isTransferOut)
                CounterpartySummary(
                    name=key.first,platform=key.second,
                    receivedCent=incoming.sumOf { it.amountCent },
                    sentCent=outgoing.sumOf { it.amountCent },
                    receivedCount=incoming.size,sentCount=outgoing.size,
                    records=list.sortedByDescending { it.occurredAt },
                )
            }.sortedByDescending { it.receivedCent + it.sentCent }

    /** Only recognized income is counted. Refunds and matched AA collections
     * are excluded to prevent double-counting as newly earned income. */
    fun incomeSources(
        rows: List<Transaction>, linkedReceiptIds: Set<Long> = emptySet(),
    ): List<CounterpartySummary> =
        rows.filter {
            it.flowType in setOf(FlowType.INCOME,FlowType.GIFT_INCOME,FlowType.BUSINESS_INCOME) &&
                it.id !in linkedReceiptIds
        }.groupBy { name(it) to it.platform }
            .map { (key, list) ->
                CounterpartySummary(
                    key.first,key.second,
                    list.sumOf { it.amountCent },0L,list.size,0,
                    list.sortedByDescending { it.occurredAt }
                )
            }.sortedByDescending { it.receivedCent }

    fun expenseByCategory(rows:List<Transaction>): Map<String,Long> =
        rows.filter { it.flowType in setOf(FlowType.EXPENSE,FlowType.GIFT_EXPENSE) }
            .groupBy { it.category }
            .mapValues { (_, tx) -> tx.sumOf { it.amountCent } }
};