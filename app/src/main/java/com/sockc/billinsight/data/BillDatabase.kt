package com.sockc.billinsight.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.sockc.billinsight.model.CategoryTotal
import com.sockc.billinsight.model.DashboardSummary
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.MerchantTotal
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import java.time.YearMonth
import java.time.ZoneId

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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
                    "transactions",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (id == -1L) duplicate++ else inserted++
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return inserted to duplicate
    }

    fun loadTransactions(limit: Int = 500): List<Transaction> {
        readableDatabase.query(
            "transactions",
            null,
            null,
            null,
            null,
            null,
            "occurred_at DESC",
            limit.toString()
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) add(cursor.toTransaction())
            }
        }
    }

    fun summary(month: YearMonth): DashboardSummary {
        val (start, end) = monthRange(month)
        val sql = """
            SELECT
              COALESCE(SUM(CASE WHEN flow_type='EXPENSE' THEN amount_cent ELSE 0 END), 0) expense,
              COALESCE(SUM(CASE WHEN flow_type='INCOME' THEN amount_cent ELSE 0 END), 0) income,
              COALESCE(SUM(CASE WHEN flow_type='REFUND' THEN amount_cent ELSE 0 END), 0) refund,
              COALESCE(SUM(CASE WHEN flow_type='TRANSFER' THEN amount_cent ELSE 0 END), 0) transfer_amount,
              COUNT(*) total_count,
              COALESCE(SUM(CASE WHEN flow_type='EXPENSE' AND amount_cent < 5000 THEN amount_cent ELSE 0 END), 0) small_amount,
              COALESCE(SUM(CASE WHEN flow_type='EXPENSE' AND amount_cent < 5000 THEN 1 ELSE 0 END), 0) small_count
            FROM transactions
            WHERE occurred_at >= ? AND occurred_at < ?
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf(start.toString(), end.toString())).use { c ->
            c.moveToFirst()
            return DashboardSummary(
                expenseCent = c.getLong(0),
                incomeCent = c.getLong(1),
                refundCent = c.getLong(2),
                transferCent = c.getLong(3),
                transactionCount = c.getInt(4),
                smallExpenseCent = c.getLong(5),
                smallExpenseCount = c.getInt(6),
            )
        }
    }

    fun categoryTotals(month: YearMonth, limit: Int = 20): List<CategoryTotal> {
        val (start, end) = monthRange(month)
        val sql = """
            SELECT category, SUM(amount_cent) amount, COUNT(*) count
            FROM transactions
            WHERE occurred_at >= ? AND occurred_at < ? AND flow_type='EXPENSE'
            GROUP BY category
            ORDER BY amount DESC
            LIMIT ?
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf(start.toString(), end.toString(), limit.toString())).use { c ->
            return buildList {
                while (c.moveToNext()) add(CategoryTotal(c.getString(0), c.getLong(1), c.getInt(2)))
            }
        }
    }

    fun merchantTotals(month: YearMonth, limit: Int = 20): List<MerchantTotal> {
        val (start, end) = monthRange(month)
        val sql = """
            SELECT CASE WHEN counterparty='' THEN '未知商户' ELSE counterparty END merchant,
                   SUM(amount_cent) amount, COUNT(*) count
            FROM transactions
            WHERE occurred_at >= ? AND occurred_at < ? AND flow_type='EXPENSE'
            GROUP BY merchant
            ORDER BY amount DESC
            LIMIT ?
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf(start.toString(), end.toString(), limit.toString())).use { c ->
            return buildList {
                while (c.moveToNext()) add(MerchantTotal(c.getString(0), c.getLong(1), c.getInt(2)))
            }
        }
    }

    fun largestExpenses(month: YearMonth, limit: Int = 10): List<Transaction> {
        val (start, end) = monthRange(month)
        readableDatabase.query(
            "transactions",
            null,
            "occurred_at >= ? AND occurred_at < ? AND flow_type='EXPENSE'",
            arrayOf(start.toString(), end.toString()),
            null,
            null,
            "amount_cent DESC",
            limit.toString()
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) add(cursor.toTransaction())
            }
        }
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
        private const val DB_VERSION = 1
    }
}
