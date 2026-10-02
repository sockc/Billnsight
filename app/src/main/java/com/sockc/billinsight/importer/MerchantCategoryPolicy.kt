package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import java.util.Locale

/**
 * The ledger and analysis must use the same merchant identity for category edits.
 * Never guess that two unrelated payees are identical: group only exact normalized
 * names or names explicitly unified in the user's merchant aliases.
 */
object MerchantCategoryPolicy {
    fun normalize(text: String): String =
        text.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    fun key(tx: Transaction, aliases: Map<String, String>): Pair<Platform, String>? {
        if (tx.flowType != FlowType.EXPENSE || tx.platform == Platform.UNKNOWN ||
            tx.sourceFile == "手动记账" ||
            ScanPaymentClassifier.isGenericCounterparty(tx.counterparty)
        ) return null
        val raw = normalize(tx.counterparty)
        if (raw.isBlank()) return null
        val canonical = aliases[raw]?.takeIf { it.isNotBlank() } ?: raw
        val identity = normalize(canonical)
        if (identity.isBlank() || ScanPaymentClassifier.isGenericCounterparty(identity)) return null
        return tx.platform to identity
    }

    /**
     * An intentional merchant-wide fix can repair old "其他" entries, even if an
     * older workflow marked those as manually reviewed. Meaningful manually
     * assigned categories stay protected.
     */
    fun canReplaceHistorical(tx: Transaction, natureModified: Boolean): Boolean =
        tx.flowType == FlowType.EXPENSE &&
            (!natureModified || tx.category == "其他")
}
