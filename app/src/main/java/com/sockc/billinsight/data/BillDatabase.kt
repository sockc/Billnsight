package com.sockc.billinsight.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.sockc.billinsight.model.CategoryTotal
import com.sockc.billinsight.model.DailyTotal
import com.sockc.billinsight.model.DashboardSummary
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.MerchantTotal
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.RecurringExpense
import com.sockc.billinsight.model.Transaction
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToLong

class BillDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                platform TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                counterparty TEXT NOT NULL,
                description TEXT NOT NULL,
                direction_text TEXT NOT NULL,
                amount_cent INTEGER NOT NULL,
                flow_type TEXT NOT NULL,
                category TEXT NOT NULL,
                payment_method TEXT NOT NULL,
                transaction_id TEXT NOT NULL,
                merchant_order_id TEXT NOT NULL,
                source_file TEXT NOT NULL,
                fingerprint TEXT NOT NULL UNIQUE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_transactions_time ON transactions(occurred_at)")
        db.execSQL("CREATE INDEX idx_transactions_flow ON transactions(flow_type)")
        db.execSQL("CREATE INDEX idx_transactions_category ON transactions(category)")
        db.execSQL("CREATE INDEX idx_transactions_platform ON transactions(platform)")
        db.execSQL(
            """
            CREATE TABLE merchant_rules (
                merchant TEXT PRIMARY KEY,
                category TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_platform ON transactions(platform)")
            // Existing fingerprints and transaction IDs are untouched. Only obviously ambiguous
            // old entries are put into review; transfers to one's own account stay excluded.
            db.execSQL(
                """
                UPDATE transactions SET flow_type='PENDING', category='待确认'
                WHERE flow_type='TRANSFER'
                  AND (description LIKE '%转账%' OR description LIKE '%收钱码%' OR description LIKE '%二维码%')
                  AND description NOT LIKE '%充值%'
                  AND description NOT LIKE '%提现%'
                  AND description NOT LIKE '%信用卡还款%'
                  AND description NOT LIKE '%余额宝%'
                  AND description NOT LIKE '%零钱通%'
                  AND description NOT LIKE '%资金转入%'
                  AND description NOT LIKE '%资金转出%'
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE transactions SET flow_type='PENDING', category='待确认'
                WHERE flow_type='EXPENSE' AND category='其他'
                  AND (description LIKE '%二维码付款%' OR description LIKE '%扫码付款%')
                """.trimIndent()
            )
        }
    }

    fun insertAll(items: List<Transaction>): Pair<Int, Int> {
        var inserted = 0
        var duplicate = 0
        writableDatabase.beginTransaction()
        try {
            for (item in items) {
                val values = ContentValues().apply {
                    put("platform", item.platform.name)
                    put("occurred_at", item.occurredAt)
                    put("counterparty", item.counterparty)
                    put("description", item.description)
                    put("direction_text", item.directionText)
                    put("amount_cent", item.amountCent)
                    put("flow_type", item.flowType.name)
                    put("category", item.category)
                    put("payment_method", item.paymentMethod)
                    put("transaction_id", item.transactionId)
                    put("merchant_order_id", item.merchantOrderId)
                    put("source_file", item.sourceFile)
                    put("fingerprint", item.fingerprint)
                }
                val id = writableDatabase.insertWithOnConflict(
                    "transactions", null, values, SQLiteDatabase.CONFLICT_IGNORE
                )
                if (id == -1L) duplicate++ else inserted++
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return inserted to duplicate
    }

    fun loadTransactions(platform: Platform? = null, limit: Int = 500): List<Transaction> {
        val where = if (platform == null) null else "platform=?"
        val args = platform?.let { arrayOf(it.name) }
        readableDatabase.query(
            "transactions", null, where, args, null, null, "occurred_at DESC", limit.toString()
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) add(cursor.toTransaction())
            }
        }
    }

    fun summary(
        month: YearMonth,
        platform: Platform? = null,
        smallThresholdCent: Long = 5_000,
    ): DashboardSummary {
        val (start, end) = monthRange(month)
        val platformClause = if (platform == null) "" else " AND platform=?"
        val sql = """
            SELECT
              COALESCE(SUM(CASE WHEN flow_type IN ('EXPENSE','GIFT_EXPENSE') THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='INCOME' THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='REFUND' THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='TRANSFER' THEN amount_cent ELSE 0 END), 0),
              COUNT(*),
              COALESCE(SUM(CASE WHEN flow_type IN ('EXPENSE','GIFT_EXPENSE') AND amount_cent < ? THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type IN ('EXPENSE','GIFT_EXPENSE') AND amount_cent < ? THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='PENDING' THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='GIFT_EXPENSE' THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='GIFT_INCOME' THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='GIFT_EXPENSE' THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='GIFT_INCOME' THEN 1 ELSE 0 END), 0)
            FROM transactions
            WHERE occurred_at >= ? AND occurred_at < ?$platformClause
        """.trimIndent()
        val args = mutableListOf(
            smallThresholdCent.toString(),
            smallThresholdCent.toString(),
            start.toString(),
            end.toString(),
        )
        platform?.let { args += it.name }

        readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            c.moveToFirst()
            return DashboardSummary(
                expenseCent = c.getLong(0),
                incomeCent = c.getLong(1),
                refundCent = c.getLong(2),
                transferCent = c.getLong(3),
                transactionCount = c.getInt(4),
                smallExpenseCent = c.getLong(5),
                smallExpenseCount = c.getInt(6),
                pendingCount = c.getInt(7),
                giftExpenseCent = c.getLong(8),
                giftIncomeCent = c.getLong(9),
                giftExpenseCount = c.getInt(10),
                giftIncomeCount = c.getInt(11),
            )
        }
    }

    fun categoryTotals(month: YearMonth, platform: Platform? = null, limit: Int = 20): List<CategoryTotal> {
        val (start, end) = monthRange(month)
        val platformClause = if (platform == null) "" else " AND platform=?"
        val sql = """
            SELECT category, SUM(amount_cent), COUNT(*)
            FROM transactions
            WHERE occurred_at >= ? AND occurred_at < ? AND flow_type IN ('EXPENSE','GIFT_EXPENSE')$platformClause
            GROUP BY category
            ORDER BY SUM(amount_cent) DESC
            LIMIT ?
        """.trimIndent()
        val args = mutableListOf(start.toString(), end.toString())
        platform?.let { args += it.name }
        args += limit.toString()

        readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            return buildList {
                while (c.moveToNext()) add(CategoryTotal(c.getString(0), c.getLong(1), c.getInt(2)))
            }
        }
    }

    fun merchantTotals(
        month: YearMonth,
        platform: Platform? = null,
        category: String? = null,
        limit: Int = 20,
    ): List<MerchantTotal> {
        val (start, end) = monthRange(month)
        val conditions = mutableListOf("occurred_at >= ?", "occurred_at < ?", "flow_type IN ('EXPENSE','GIFT_EXPENSE')")
        val args = mutableListOf(start.toString(), end.toString())
        platform?.let {
            conditions += "platform=?"
            args += it.name
        }
        category?.let {
            conditions += "category=?"
            args += it
        }
        args += limit.toString()

        val sql = """
            SELECT CASE WHEN counterparty='' THEN '未知商户' ELSE counterparty END,
                   SUM(amount_cent), COUNT(*)
            FROM transactions
            WHERE ${conditions.joinToString(" AND ")}
            GROUP BY CASE WHEN counterparty='' THEN '未知商户' ELSE counterparty END
            ORDER BY SUM(amount_cent) DESC
            LIMIT ?
        """.trimIndent()

        readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            return buildList {
                while (c.moveToNext()) add(MerchantTotal(c.getString(0), c.getLong(1), c.getInt(2)))
            }
        }
    }

    fun largestExpenses(month: YearMonth, platform: Platform? = null, limit: Int = 10): List<Transaction> {
        val (start, end) = monthRange(month)
        val where = buildString {
            append("occurred_at >= ? AND occurred_at < ? AND flow_type IN ('EXPENSE','GIFT_EXPENSE')")
            if (platform != null) append(" AND platform=?")
        }
        val args = mutableListOf(start.toString(), end.toString())
        platform?.let { args += it.name }

        readableDatabase.query(
            "transactions", null, where, args.toTypedArray(), null, null, "amount_cent DESC", limit.toString()
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) add(cursor.toTransaction())
            }
        }
    }

    fun dailyTotals(month: YearMonth, platform: Platform? = null): List<DailyTotal> {
        val (start, end) = monthRange(month)
        val platformClause = if (platform == null) "" else " AND platform=?"
        val sql = """
            SELECT CAST(strftime('%d', occurred_at / 1000, 'unixepoch', 'localtime') AS INTEGER) AS day_num,
                   SUM(amount_cent), COUNT(*)
            FROM transactions
            WHERE occurred_at >= ? AND occurred_at < ? AND flow_type IN ('EXPENSE','GIFT_EXPENSE')$platformClause
            GROUP BY day_num
            ORDER BY day_num
        """.trimIndent()
        val args = mutableListOf(start.toString(), end.toString())
        platform?.let { args += it.name }

        readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            return buildList {
                while (c.moveToNext()) add(DailyTotal(c.getInt(0), c.getLong(1), c.getInt(2)))
            }
        }
    }

    fun recurringExpenses(
        month: YearMonth,
        platform: Platform? = null,
        lookbackMonths: Long = 4,
        limit: Int = 12,
    ): List<RecurringExpense> {
        val (start, _) = monthRange(month.minusMonths(lookbackMonths - 1))
        val (_, end) = monthRange(month)
        val platformClause = if (platform == null) "" else " AND platform=?"
        val sql = """
            SELECT counterparty,
                   strftime('%Y-%m', occurred_at / 1000, 'unixepoch', 'localtime') AS month_key,
                   SUM(amount_cent), COUNT(*)
            FROM transactions
            WHERE occurred_at >= ? AND occurred_at < ?
              AND flow_type='EXPENSE'
              AND counterparty <> ''
              AND category <> '经营相关'$platformClause
            GROUP BY counterparty, month_key
            ORDER BY counterparty, month_key
        """.trimIndent()

        val args = mutableListOf(start.toString(), end.toString())
        platform?.let { args += it.name }

        data class MonthlySpend(val month: String, val amount: Long, val count: Int)
        val grouped = linkedMapOf<String, MutableList<MonthlySpend>>()
        readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            while (c.moveToNext()) {
                grouped.getOrPut(c.getString(0)) { mutableListOf() }
                    .add(MonthlySpend(c.getString(1), c.getLong(2), c.getInt(3)))
            }
        }

        val currentKey = month.toString()
        return grouped.mapNotNull { (merchant, spends) ->
            if (spends.size < 2) return@mapNotNull null
            val min = spends.minOf { it.amount }
            val max = spends.maxOf { it.amount }
            if (min <= 0L || max > min * 2.5) return@mapNotNull null

            RecurringExpense(
                merchant = merchant,
                averageMonthlyCent = spends.map { it.amount }.average().roundToLong(),
                latestMonthCent = spends.firstOrNull { it.month == currentKey }?.amount ?: 0L,
                activeMonths = spends.size,
                transactionCount = spends.sumOf { it.count },
            )
        }
            .sortedByDescending { it.averageMonthlyCent }
            .take(limit)
    }

    /** Review queue across all imported months; never limited to the latest 500 transactions. */
    fun pendingTransactions(platform: Platform? = null, limit: Int = 200): List<Transaction> {
        val where = "flow_type='PENDING'" + if (platform == null) "" else " AND platform=?"
        readableDatabase.query(
            "transactions", null, where,
            platform?.let { arrayOf(it.name) }, null, null,
            "occurred_at DESC", limit.toString()
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) add(cursor.toTransaction())
            }
        }
    }

    fun pendingTotal(platform: Platform? = null): Int {
        val where = "flow_type='PENDING'" + if (platform == null) "" else " AND platform=?"
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM transactions WHERE $where",
            platform?.let { arrayOf(it.name) }
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    /**
     * Changing a transaction's nature never changes its fingerprint or reimports it.
     * Do not store a merchant-wide nature rule: one person can receive both loans and gifts.
     */
    fun updateNature(id: Long, flowType: FlowType, category: String) {
        require(id > 0) { "无效流水" }
        require(category.isNotBlank()) { "请选择分类" }
        writableDatabase.update(
            "transactions",
            ContentValues().apply {
                put("flow_type", flowType.name)
                put("category", category)
            },
            "id=?",
            arrayOf(id.toString())
        )
    }

    fun updateCategory(id: Long, merchant: String, category: String, rememberMerchant: Boolean) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update(
                "transactions",
                ContentValues().apply { put("category", category) },
                "id=?",
                arrayOf(id.toString())
            )
            if (rememberMerchant && merchant.isNotBlank()) {
                writableDatabase.insertWithOnConflict(
                    "merchant_rules",
                    null,
                    ContentValues().apply {
                        put("merchant", merchant.trim())
                        put("category", category)
                        put("updated_at", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                writableDatabase.update(
                    "transactions",
                    ContentValues().apply { put("category", category) },
                    "counterparty=? AND flow_type='EXPENSE'",
                    arrayOf(merchant.trim())
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun merchantRules(): Map<String, String> {
        readableDatabase.query("merchant_rules", arrayOf("merchant", "category"), null, null, null, null, null)
            .use { c ->
                return buildMap {
                    while (c.moveToNext()) put(c.getString(0), c.getString(1))
                }
            }
    }

    fun transactionCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM transactions", null).use { c ->
        c.moveToFirst(); c.getInt(0)
    }

    private fun monthRange(month: YearMonth): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    private fun Cursor.toTransaction() = Transaction(
        id = getLong(getColumnIndexOrThrow("id")),
        platform = runCatching { Platform.valueOf(getString(getColumnIndexOrThrow("platform"))) }.getOrDefault(Platform.UNKNOWN),
        occurredAt = getLong(getColumnIndexOrThrow("occurred_at")),
        counterparty = getString(getColumnIndexOrThrow("counterparty")),
        description = getString(getColumnIndexOrThrow("description")),
        directionText = getString(getColumnIndexOrThrow("direction_text")),
        amountCent = getLong(getColumnIndexOrThrow("amount_cent")),
        flowType = runCatching { FlowType.valueOf(getString(getColumnIndexOrThrow("flow_type"))) }.getOrDefault(FlowType.IGNORE),
        category = getString(getColumnIndexOrThrow("category")),
        paymentMethod = getString(getColumnIndexOrThrow("payment_method")),
        transactionId = getString(getColumnIndexOrThrow("transaction_id")),
        merchantOrderId = getString(getColumnIndexOrThrow("merchant_order_id")),
        sourceFile = getString(getColumnIndexOrThrow("source_file")),
        fingerprint = getString(getColumnIndexOrThrow("fingerprint")),
    )

    companion object {
        private const val DB_NAME = "bill_insight.db"
        private const val DB_VERSION = 2
    }
}
