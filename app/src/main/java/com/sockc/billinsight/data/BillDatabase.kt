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
import com.sockc.billinsight.analysis.MerchantAnalysis
import com.sockc.billinsight.model.LinkKind
import com.sockc.billinsight.model.ExpenseLink
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
                trade_type TEXT NOT NULL DEFAULT '',
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
        createLinkAndAliasTables(db)
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
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN trade_type TEXT NOT NULL DEFAULT ''")
            // Only upgrade records that are STILL pending. Manually reviewed records
            // keep their original nature and category.
            db.execSQL(
                """
                UPDATE transactions SET flow_type='INCOME', category='收入'
                WHERE flow_type='PENDING' AND direction_text LIKE '%收入%'
                  AND (description LIKE '%二维码收款%' OR description LIKE '%收钱码%'
                       OR description LIKE '%扫码收款%' OR description LIKE '%面对面收款%')
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE transactions SET flow_type='EXPENSE', category='其他'
                WHERE flow_type='PENDING' AND direction_text LIKE '%支出%'
                  AND (description LIKE '%二维码付款%' OR description LIKE '%扫码付款%'
                       OR description LIKE '%扫一扫付款%' OR description LIKE '%扫码支付%')
            """.trimIndent()
            )
        }
        if (oldVersion < 4) {
            createLinkAndAliasTables(db)
            // Old manual classifications cannot be reliably distinguished from automatic
            // ones. Migrate only untouched auto-classified rows with explicit trade type.
            db.execSQL(
                """
                UPDATE transactions SET flow_type='CREDIT_REPAYMENT', category='信用卡还款'
                WHERE flow_type='TRANSFER' AND category='资金流转'
                  AND (trade_type LIKE '%信用卡还款%' OR trade_type LIKE '%还信用卡%'
                       OR (trade_type='' AND (description LIKE '%信用卡还款%' OR description LIKE '%还信用卡%')))
                  AND direction_text LIKE '%支出%'
                """.trimIndent()
            )
            matchRefundsInTransaction(db)
        }
        if (oldVersion < 5) {
            createMerchantAliasesTable(db)
        }
    }

    private fun createMerchantAliasesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS merchant_aliases (
                alias_key TEXT PRIMARY KEY,
                canonical TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createLinkAndAliasTables(db: SQLiteDatabase) {
        createMerchantAliasesTable(db)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transaction_links (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                expense_id INTEGER NOT NULL REFERENCES transactions(id),
                receipt_id INTEGER NOT NULL UNIQUE REFERENCES transactions(id),
                kind TEXT NOT NULL CHECK (kind IN ('REFUND','SHARE')),
                amount_cent INTEGER NOT NULL CHECK (amount_cent > 0),
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_links_expense ON transaction_links(expense_id)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS product_aliases (
                merchant_key TEXT NOT NULL,
                alias_key TEXT NOT NULL,
                canonical TEXT NOT NULL,
                PRIMARY KEY (merchant_key, alias_key)
            )
            """.trimIndent()
        )
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
                    put("trade_type", item.tradeType)
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
                if (id == -1L) {
                    duplicate++
                    // Reimporting an original file can recover the QR transaction type
                    // missing from pre-v0.1.5 databases. Never overwrite manual review.
                    val qrType = item.tradeType.contains("二维码") ||
                        item.tradeType.contains("扫码") ||
                        item.tradeType.contains("收钱码")
                    if (qrType && item.flowType in setOf(FlowType.EXPENSE, FlowType.INCOME)) {
                        writableDatabase.update(
                            "transactions",
                            ContentValues().apply {
                                put("flow_type", item.flowType.name)
                                put("category", item.category)
                                put("trade_type", item.tradeType)
                            },
                            "fingerprint=? AND flow_type='PENDING'",
                            arrayOf(item.fingerprint)
                        )
                    }
                    if (item.flowType == FlowType.CREDIT_REPAYMENT) {
                        writableDatabase.update(
                            "transactions",
                            ContentValues().apply {
                                put("flow_type", "CREDIT_REPAYMENT")
                                put("category", "信用卡还款")
                                put("trade_type", item.tradeType)
                            },
                            "fingerprint=? AND flow_type='TRANSFER' AND category='资金流转'",
                            arrayOf(item.fingerprint),
                        )
                    }
                } else {
                    inserted++
                }
            }
            matchRefundsInTransaction(writableDatabase)
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

    /** Full selected-month history, not limited to the 500 newest entries. */
    fun monthTransactions(month: YearMonth, platform: Platform? = null): List<Transaction> {
        val (start, end) = monthRange(month)
        val selection = "occurred_at >= ? AND occurred_at < ?" +
            if (platform == null) "" else " AND platform=?"
        val args = mutableListOf(start.toString(), end.toString())
        platform?.let { args += it.name }
        readableDatabase.query(
            "transactions", null, selection, args.toTypedArray(), null, null,
            "occurred_at DESC"
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) add(cursor.toTransaction())
            }
        }
    }

    /** Safe bound-parameter search over ALL historical income and expenses. */
    fun searchTransactions(
        query: String,
        platform: Platform? = null,
        flowFilter: String = "ALL",
        limit: Int = 200,
    ): List<Transaction> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        platform?.let {
            where += "platform=?"
            args += it.name
        }
        when (flowFilter) {
            "EXPENSE" -> where += "flow_type IN ('EXPENSE','GIFT_EXPENSE','BUSINESS_EXPENSE','LOAN_OUT','CREDIT_REPAYMENT')"
            "INCOME" -> where += "flow_type IN ('INCOME','GIFT_INCOME','BUSINESS_INCOME','LOAN_RECOVERY','REFUND')"
            "OTHER" -> where += "flow_type IN ('TRANSFER','PENDING','IGNORE')"
        }
        if (query.isNotBlank()) {
            val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            where += "(counterparty LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\' OR trade_type LIKE ? ESCAPE '\\' OR category LIKE ? ESCAPE '\\' OR transaction_id LIKE ? ESCAPE '\\' OR merchant_order_id LIKE ? ESCAPE '\\')"
            repeat(6) { args += "%$escaped%" }
        }
        readableDatabase.query(
            "transactions", null, where.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            args.takeIf { it.isNotEmpty() }?.toTypedArray(), null, null,
            "occurred_at DESC, id DESC", limit.coerceIn(1, 10000).toString()
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) add(cursor.toTransaction())
            }
        }
    }


    /**
     * Automatically match an unlinked refund only when the merchant-order ID
     * uniquely identifies one original purchase. Never guess by similar price.
     */
    private fun matchRefundsInTransaction(db: SQLiteDatabase) {
        db.rawQuery(
            """
            SELECT r.id, r.amount_cent, e.id
            FROM transactions r JOIN transactions e
              ON r.merchant_order_id=e.merchant_order_id
            WHERE r.flow_type='REFUND' AND e.flow_type IN ('EXPENSE','GIFT_EXPENSE')
              AND r.merchant_order_id <> '' AND r.id<>e.id
              AND NOT EXISTS (SELECT 1 FROM transaction_links l WHERE l.receipt_id=r.id)
            ORDER BY r.id DESC
            """.trimIndent(), null
        ).use { cursor ->
            val byReceipt = linkedMapOf<Long, MutableList<Pair<Long,Long>>>()
            while (cursor.moveToNext()) {
                val receiptId = cursor.getLong(0)
                byReceipt.getOrPut(receiptId) { mutableListOf() }
                    .add(cursor.getLong(2) to cursor.getLong(1))
            }
            byReceipt.forEach { (receiptId, candidates) ->
                if (candidates.size == 1) {
                    val (expenseId, amount) = candidates.single()
                    runCatching { createLinkTx(db, expenseId, receiptId, LinkKind.REFUND, amount) }
                }
            }
        }
    }

    fun createLink(expenseId: Long, receiptId: Long, kind: LinkKind, amountCent: Long) {
        writableDatabase.beginTransaction()
        try {
            createLinkTx(writableDatabase, expenseId, receiptId, kind, amountCent)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun createLinkTx(
        db: SQLiteDatabase, expenseId: Long, receiptId: Long,
        kind: LinkKind, amountCent: Long,
    ) {
        require(expenseId != receiptId && amountCent > 0) { "请选择不同的原消费和收款记录" }
        val expense = db.rawQuery(
            "SELECT amount_cent, flow_type FROM transactions WHERE id=?",
            arrayOf(expenseId.toString())
        ).use { c ->
            require(c.moveToFirst()) { "找不到原消费" }
            c.getLong(0) to c.getString(1)
        }
        val receipt = db.rawQuery(
            "SELECT amount_cent, flow_type FROM transactions WHERE id=?",
            arrayOf(receiptId.toString())
        ).use { c ->
            require(c.moveToFirst()) { "找不到退款或分摊收款" }
            c.getLong(0) to c.getString(1)
        }
        require(expense.second in setOf("EXPENSE", "GIFT_EXPENSE")) { "只有个人消费可以关联退款或 AA 分摊" }
        require(receipt.second == if (kind == LinkKind.REFUND) "REFUND" else "INCOME") {
            "请选取正确性质的收款流水"
        }
        val allocated = db.rawQuery(
            "SELECT COALESCE(SUM(amount_cent),0) FROM transaction_links WHERE expense_id=?",
            arrayOf(expenseId.toString())
        ).use { c -> c.moveToFirst(); c.getLong(0) }
        require(amountCent <= receipt.first && amountCent <= expense.first - allocated) {
            "关联金额超过原消费剩余金额或收款金额"
        }
        val inserted = db.insertWithOnConflict(
            "transaction_links", null, ContentValues().apply {
                put("expense_id", expenseId)
                put("receipt_id", receiptId)
                put("kind", kind.name)
                put("amount_cent", amountCent)
                put("created_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_IGNORE
        )
        require(inserted != -1L) { "该收款已关联其他消费" }
    }

    fun deleteLink(linkId: Long) {
        require(linkId > 0)
        writableDatabase.delete("transaction_links", "id=?", arrayOf(linkId.toString()))
    }

    fun linksForMonth(month: YearMonth, platform: Platform? = null): List<ExpenseLink> {
        val (start, end) = monthRange(month)
        val args = mutableListOf(start.toString(), end.toString())
        val where = if (platform == null) "" else " AND t.platform=?"
        platform?.let { args += it.name }
        readableDatabase.rawQuery(
            """
            SELECT l.id,l.expense_id,l.receipt_id,l.kind,l.amount_cent
            FROM transaction_links l JOIN transactions t ON t.id=l.expense_id
            WHERE t.occurred_at >= ? AND t.occurred_at < ?$where
            ORDER BY t.occurred_at DESC
            """.trimIndent(), args.toTypedArray()
        ).use { c ->
            return buildList {
                while (c.moveToNext()) add(
                    ExpenseLink(c.getLong(0), c.getLong(1), c.getLong(2),
                        LinkKind.valueOf(c.getString(3)), c.getLong(4))
                )
            }
        }
    }

    fun linkedReceiptIds(): Set<Long> {
        readableDatabase.rawQuery("SELECT receipt_id FROM transaction_links", null).use { c ->
            return buildSet {
                while (c.moveToNext()) add(c.getLong(0))
            }
        }
    }

    fun merchantAliases(): Map<String, String> {
        readableDatabase.rawQuery(
            "SELECT alias_key,canonical FROM merchant_aliases", null
        ).use { c ->
            return buildMap {
                while (c.moveToNext()) put(c.getString(0), c.getString(1))
            }
        }
    }

    fun saveMerchantAlias(original: String, canonical: String) {
        val key = original.trim().lowercase()
        val target = canonical.trim()
        require(key.isNotBlank() && target.isNotBlank() && target.length <= 80) {
            "商户名称不能为空且不能超过 80 个字符"
        }
        require(key != target.lowercase()) { "新的商户名称与原名称相同" }
        writableDatabase.insertWithOnConflict(
            "merchant_aliases", null,
            ContentValues().apply {
                put("alias_key", key)
                put("canonical", target)
                put("updated_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /** Period applies only to merchant leaderboards, not monthly income/expense cards. */
    fun merchantHistoryTransactions(
        month: YearMonth, platform: Platform?, period: String
    ): List<Transaction> {
        require(period in setOf("MONTH","THREE_MONTHS","ALL"))
        val filters = mutableListOf("flow_type='EXPENSE'")
        val args = mutableListOf<String>()
        if (period != "ALL") {
            val (start, end) = monthRange(if (period == "MONTH") month else month.minusMonths(2))
            filters += "occurred_at>=? AND occurred_at<?"
            args += start.toString()
            args += end.toString()
        }
        platform?.let {
            filters += "platform=?"
            args += it.name
        }
        readableDatabase.query(
            "transactions", null, filters.joinToString(" AND "), args.toTypedArray(),
            null, null, "occurred_at DESC", "10000"
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) add(cursor.toTransaction())
            }
        }
    }

    fun productAliases(): Map<String,String> {
        readableDatabase.rawQuery(
            "SELECT merchant_key,alias_key,canonical FROM product_aliases", null
        ).use { c ->
            return buildMap {
                while (c.moveToNext()) put(c.getString(0) + "|" + c.getString(1), c.getString(2))
            }
        }
    }

    fun saveProductAlias(merchant: String, alias: String, canonical: String) {
        require(merchant.isNotBlank() && alias.isNotBlank() && canonical.isNotBlank())
        writableDatabase.insertWithOnConflict(
            "product_aliases", null, ContentValues().apply {
                put("merchant_key", merchant.trim().lowercase())
                put("alias_key", alias.trim().lowercase())
                put("canonical", canonical.trim())
            }, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun linkedRecovery(month: YearMonth, platform: Platform?): Pair<Long, Long> {
        val (start, end) = monthRange(month)
        val platformSql = if (platform == null) "" else " AND t.platform=?"
        val args = mutableListOf(start.toString(), end.toString())
        platform?.let { args += it.name }
        readableDatabase.rawQuery(
            """
            SELECT COALESCE(SUM(CASE WHEN l.kind='REFUND' THEN l.amount_cent ELSE 0 END),0),
                   COALESCE(SUM(CASE WHEN l.kind='SHARE' THEN l.amount_cent ELSE 0 END),0)
            FROM transaction_links l JOIN transactions t ON t.id=l.expense_id
            WHERE t.occurred_at >= ? AND t.occurred_at < ?$platformSql
            """.trimIndent(), args.toTypedArray()
        ).use { c ->
            c.moveToFirst()
            return c.getLong(0) to c.getLong(1)
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
              COALESCE(SUM(CASE WHEN flow_type='INCOME'
                  AND NOT EXISTS(SELECT 1 FROM transaction_links l
                                 WHERE l.receipt_id=transactions.id)
                THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='REFUND' THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='TRANSFER' THEN amount_cent ELSE 0 END), 0),
              COUNT(*),
              COALESCE(SUM(CASE WHEN flow_type IN ('EXPENSE','GIFT_EXPENSE') AND amount_cent < ? THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type IN ('EXPENSE','GIFT_EXPENSE') AND amount_cent < ? THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='PENDING' THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='GIFT_EXPENSE' THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='GIFT_INCOME' THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='GIFT_EXPENSE' THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='GIFT_INCOME' THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='CREDIT_REPAYMENT' THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='CREDIT_REPAYMENT' THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='BUSINESS_EXPENSE' THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='LOAN_OUT' THEN amount_cent ELSE 0 END), 0)
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
            val linked = linkedRecovery(month, platform)
            return DashboardSummary(
                linkedRefundCent = linked.first,
                linkedShareCent = linked.second,
                creditRepaymentCent = c.getLong(12),
                creditRepaymentCount = c.getInt(13),
                businessExpenseCent = c.getLong(14),
                loanOutCent = c.getLong(15),
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
    fun bulkConfirmPending(ids: List<Long>, flowType: FlowType, category: String): Int {
        require(category.isNotBlank()) { "请选择分类" }
        require(ids.size in 1..100 && ids.distinct().size == ids.size) {
            "每次请选择 1～100 笔不同的待确认交易"
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val placeholders = ids.joinToString(",") { "?" }
            val candidates = mutableListOf<ReviewCandidate>()
            db.rawQuery(
                "SELECT id,direction_text,flow_type FROM transactions WHERE id IN ($placeholders)",
                ids.map { it.toString() }.toTypedArray()
            ).use { c ->
                while (c.moveToNext()) {
                    candidates += ReviewCandidate(
                        c.getLong(0), c.getString(1),
                        FlowType.valueOf(c.getString(2))
                    )
                }
            }
            require(candidates.size == ids.size) { "有流水已被删除，请刷新后重试" }
            BulkReviewPolicy.validate(candidates, flowType)
            val values = ContentValues().apply {
                put("flow_type", flowType.name)
                put("category", category)
            }
            var updated = 0
            ids.forEach { id ->
                updated += db.update(
                    "transactions", values, "id=? AND flow_type='PENDING'",
                    arrayOf(id.toString())
                )
            }
            require(updated == ids.size) { "部分流水状态已变化，请刷新后重试" }
            db.setTransactionSuccessful()
            return updated
        } finally {
            db.endTransaction()
        }
    }

    fun dataAudit(month: YearMonth, platform: Platform?): DataAuditReport {
        val db = readableDatabase
        fun count(sql: String, args: Array<String>? = null): Int =
            db.rawQuery(sql, args).use { it.moveToFirst(); it.getInt(0) }
        val total = count("SELECT COUNT(*) FROM transactions")
        val pending = count("SELECT COUNT(*) FROM transactions WHERE flow_type='PENDING'")
        val unmatched = count(
            """SELECT COUNT(*) FROM transactions r WHERE r.flow_type='REFUND'
               AND NOT EXISTS(SELECT 1 FROM transaction_links l WHERE l.receipt_id=r.id)"""
        )
        val amounts = count("SELECT COUNT(*) FROM transactions WHERE amount_cent<=0")
        val directions = count(
            """SELECT COUNT(*) FROM transactions
               WHERE (flow_type IN ('EXPENSE','GIFT_EXPENSE','BUSINESS_EXPENSE','LOAN_OUT','CREDIT_REPAYMENT')
                      AND direction_text LIKE '%收入%')
                  OR (flow_type IN ('INCOME','GIFT_INCOME','BUSINESS_INCOME','LOAN_RECOVERY')
                      AND direction_text LIKE '%支出%')"""
        )
        val repeated = count(
            """SELECT COALESCE(SUM(num-1),0) FROM
               (SELECT COUNT(*) num FROM transactions WHERE transaction_id<>''
                GROUP BY platform,transaction_id HAVING COUNT(*)>1)"""
        )
        val broken = count(
            """SELECT COUNT(*) FROM transaction_links l
               LEFT JOIN transactions e ON e.id=l.expense_id
               LEFT JOIN transactions r ON r.id=l.receipt_id
               WHERE e.id IS NULL OR r.id IS NULL
                   OR l.amount_cent>e.amount_cent OR l.amount_cent>r.amount_cent"""
        ) + count(
            """SELECT COUNT(*) FROM
               (SELECT l.expense_id FROM transaction_links l
                JOIN transactions e ON e.id=l.expense_id
                GROUP BY l.expense_id HAVING SUM(l.amount_cent)>MAX(e.amount_cent))"""
        )
        val summary = summary(month, platform)
        val categorySum = categoryTotals(month, platform, 10000).sumOf { it.amountCent }
        val good = runCatching {
            db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0)=="ok" }
        }.getOrDefault(false)
        return DataAuditReport(
            totalTransactions=total, pendingTransactions=pending, unmatchedRefunds=unmatched,
            nonPositiveAmounts=amounts, directionMismatches=directions,
            duplicatePlatformOrderIds=repeated, brokenLinks=broken,
            expenseDifferenceCent=summary.expenseCent-categorySum,
            databaseIntegrityOk=good
        )
    }

    fun updateNature(id: Long, flowType: FlowType, category: String) {
        require(id > 0) { "无效流水" }
        require(category.isNotBlank()) { "请选择分类" }
        val linked = readableDatabase.rawQuery(
            "SELECT 1 FROM transaction_links WHERE expense_id=? OR receipt_id=? LIMIT 1",
            arrayOf(id.toString(), id.toString())
        ).use { it.moveToFirst() }
        require(!linked) { "这笔流水已有退款或 AA 关联，请先撤销关联再修改性质" }
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
        tradeType = getString(getColumnIndexOrThrow("trade_type")),
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
        private const val DB_VERSION = 5
    }
}
