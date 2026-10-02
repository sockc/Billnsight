package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Transaction
import kotlin.math.abs

/**
 * Cross-export dedupe is intentionally stricter than a same-amount/time match.
 * Do NOT match on merchant names or times alone: two actual purchases can
 * have identical amounts. Only sufficiently specific shared order IDs qualify.
 */
object StrongDuplicateKey {
    fun identifiers(tx: Transaction): Set<String> =
        listOf(tx.transactionId, tx.merchantOrderId)
            .map { it.trim().replace(" ", "") }
            .filter { id ->
                id.length >= 10 && id.any(Char::isDigit) &&
                    id.none { it == '*' || it == '＊' || it == '…' } &&
                    !id.contains("未知") && !id.contains("无")
            }.toSet()

    fun isSamePayment(a: Transaction, b: Transaction): Boolean {
        if (a.platform == b.platform || a.platform.name == "UNKNOWN" ||
            b.platform.name == "UNKNOWN") return false
        if (a.flowType !in setOf(FlowType.EXPENSE, FlowType.INCOME) ||
            a.flowType != b.flowType || a.amountCent <= 0 ||
            a.amountCent != b.amountCent || a.occurredAt <= 0 || b.occurredAt <= 0) return false
        if (abs(a.occurredAt - b.occurredAt) > 7L * 24 * 60 * 60 * 1000) return false
        return identifiers(a).intersect(identifiers(b)).isNotEmpty()
    }
}
