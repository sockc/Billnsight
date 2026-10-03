package com.sockc.billinsight.data

/**
 * Predicates for ranking personal-spending merchants.
 * The SQL caller supplies all AND operators via joinToString(" AND ").
 * Including a leading "AND" here would crash even an empty ledger.
 */
internal object ExpenseSqlPolicy {
    fun merchantConditions(): MutableList<String> = mutableListOf(
        "occurred_at >= ?",
        "occurred_at < ?",
        "flow_type IN ('EXPENSE','GIFT_EXPENSE')",
        """NOT EXISTS (
            SELECT 1 FROM finance_installment_plans fp
            WHERE fp.origin_transaction_id=transactions.id
              AND fp.kind='ADVANCE'
        )""".trimIndent()
    )
}
