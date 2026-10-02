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
import com.sockc.billinsight.model.CreditCenter
import com.sockc.billinsight.model.ManualCreditRepayment
import com.sockc.billinsight.model.LoanProfile
import com.sockc.billinsight.model.MerchantRule
import com.sockc.billinsight.model.TrendPoint
import com.sockc.billinsight.importer.CreditRepaymentDetector
import com.sockc.billinsight.importer.ScanPaymentClassifier
import com.sockc.billinsight.importer.TransactionClassifier
import com.sockc.billinsight.model.LoanRepaymentDetail
import com.sockc.billinsight.model.LoanRepaymentPolicy
import com.sockc.billinsight.analysis.MerchantAnalysis
import com.sockc.billinsight.model.LinkKind
import com.sockc.billinsight.model.ExpenseLink
import com.sockc.billinsight.model.MerchantTotal
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.RecurringExpense
import com.sockc.billinsight.model.Transaction
import java.time.YearMonth
import java.time.LocalDate
import java.time.Instant
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
                nature_modified INTEGER NOT NULL DEFAULT 0,
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
            "CREATE INDEX idx_transactions_rule ON transactions(counterparty,flow_type,nature_modified)"
        )
        createLinkAndAliasTables(db)
        createLoanDetailsTable(db)
        createFinanceCenterTables(db)
        createScanTables(db)
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
        if (oldVersion < 6) {
            createLoanDetailsTable(db)
            // Move ONLY untouched auto-classified account transfers with explicit
            // loan-repayment trade types. User-selected categories remain intact.
            db.execSQL(
                """
                UPDATE transactions SET flow_type='LOAN_REPAYMENT', category='贷款还款'
                WHERE flow_type='TRANSFER' AND category='资金流转'
                  AND direction_text LIKE '%支出%'
                  AND (trade_type LIKE '%贷款还款%' OR trade_type LIKE '%房贷还款%'
                       OR trade_type LIKE '%车贷还款%' OR trade_type LIKE '%借呗还款%'
                       OR trade_type LIKE '%微粒贷还款%' OR trade_type LIKE '%网商贷还款%'
                       OR trade_type LIKE '%贷款扣款%' OR trade_type LIKE '%分期还款%')
                """.trimIndent()
            )
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN nature_modified INTEGER NOT NULL DEFAULT 0")
            // V6 cannot identify every old manual edit; only unambiguous source
            // descriptions or original trade types are repaired automatically.
            backfillCreditRepayments(db, includeCounterparty = false)
        }
        if (oldVersion < 8) {
            createFinanceCenterTables(db)
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_transactions_rule " +
                    "ON transactions(counterparty,flow_type,nature_modified)"
            )
        }
        if (oldVersion < 9) {
            createScanTables(db)
            migrateOldQrPurchases(db)
        }
        if (oldVersion < 10) {
            migrateAutoTransfers(db)
        }
    }

    /**
     * Only untouched default classifications are eligible. Do not rewrite
     * historic manually reviewed items or account transfers such as withdrawals.
     */
    private fun migrateAutoTransfers(db:SQLiteDatabase):Int {
        val candidates=mutableListOf<Triple<Long,String,String>>()
        db.rawQuery(
            """
            SELECT id,direction_text,trade_type,counterparty,description,
                   payment_method,flow_type,category
            FROM transactions
            WHERE nature_modified=0 AND
              ((flow_type='PENDING' AND category='待确认') OR
               (flow_type='TRANSFER' AND category='资金流转') OR
               (flow_type='EXPENSE' AND category='其他' AND trade_type LIKE '%转账%'))
            """.trimIndent(),null
        ).use { c ->
            while(c.moveToNext()) {
                val updated=TransactionClassifier.classify(
                    direction=c.getString(1),type=c.getString(2),
                    merchant=c.getString(3),description=c.getString(4),
                    status="成功",merchantRules=emptyMap(),
                    paymentMethod=c.getString(5),
                )
                val eligible=(
                    updated.category=="转账收入" && updated.flowType==FlowType.INCOME ||
                    updated.category=="转账支出" && updated.flowType==FlowType.EXPENSE ||
                    updated.category=="扫码收入" && updated.flowType==FlowType.INCOME ||
                    updated.category=="资金提现" && updated.flowType==FlowType.TRANSFER
                )
                if(eligible) candidates+=Triple(
                    c.getLong(0),updated.flowType.name,updated.category
                )
            }
        }
        var updated=0
        candidates.forEach { (id,flow,category) ->
            updated+=db.update("transactions",ContentValues().apply {
                put("flow_type",flow)
                put("category",category)
            },"id=? AND nature_modified=0",arrayOf(id.toString()))
        }
        return updated
    }

    private fun createScanTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scan_merchant_labels (
                transaction_id INTEGER PRIMARY KEY,
                display_name TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS manual_scan_links (
                manual_id INTEGER PRIMARY KEY,
                imported_id INTEGER NOT NULL UNIQUE,
                original_category TEXT NOT NULL,
                linked_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    /** Upgrade only clear outgoing QR evidence still using untouched default classifications. */
    private fun migrateOldQrPurchases(db: SQLiteDatabase): Int {
        val ids=mutableListOf<Long>()
        db.rawQuery(
            """
            SELECT id,direction_text,trade_type,description,flow_type,category
            FROM transactions
            WHERE nature_modified=0 AND
              ((flow_type='PENDING' AND category='待确认') OR
               (flow_type='TRANSFER' AND category='资金流转'))
            """.trimIndent(),null
        ).use { c ->
            while(c.moveToNext()) {
                if(ScanPaymentClassifier.isQrPayment(
                        c.getString(1),c.getString(2),c.getString(3)
                    )) ids+=c.getLong(0)
            }
        }
        val values=ContentValues().apply {
            put("flow_type",FlowType.EXPENSE.name)
            put("category","其他")
        }
        var count=0
        ids.forEach { id ->
            count+=db.update("transactions",values,
                "id=? AND nature_modified=0",arrayOf(id.toString()))
        }
        return count
    }

    private fun createFinanceCenterTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS manual_credit_repayments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                card_name TEXT NOT NULL,
                amount_cent INTEGER NOT NULL CHECK(amount_cent > 0),
                occurred_at INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                linked_transaction_id INTEGER UNIQUE REFERENCES transactions(id),
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_manual_credit_time ON manual_credit_repayments(occurred_at)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS credit_card_aliases (
                source_key TEXT PRIMARY KEY,
                display_name TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS loan_profiles (
                institution TEXT PRIMARY KEY,
                original_amount_cent INTEGER CHECK(original_amount_cent >= 0),
                remaining_principal_cent INTEGER CHECK(remaining_principal_cent >= 0),
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun backfillCreditRepayments(
        db: SQLiteDatabase, includeCounterparty: Boolean
    ): Int {
        val ids = mutableListOf<Long>()
        db.rawQuery(
            """
            SELECT t.id,t.direction_text,t.trade_type,t.counterparty,t.description,
                   t.payment_method,t.flow_type,t.category,
                   EXISTS(SELECT 1 FROM loan_repayment_details d WHERE d.transaction_id=t.id),
                   EXISTS(SELECT 1 FROM transaction_links l
                          WHERE l.expense_id=t.id OR l.receipt_id=t.id)
            FROM transactions t
            WHERE t.nature_modified=0
              AND t.flow_type IN ('TRANSFER','EXPENSE','PENDING','LOAN_REPAYMENT')
              AND t.category IN ('资金流转','其他','待确认','贷款还款')
            """.trimIndent(), null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val flow = runCatching { FlowType.valueOf(cursor.getString(6)) }.getOrNull()
                    ?: continue
                if (!CreditRepaymentDetector.isSafelyAutoCorrectable(
                        flow, cursor.getString(7), cursor.getInt(8) != 0,
                        cursor.getInt(9) != 0
                    )) continue
                if (CreditRepaymentDetector.isRepayment(
                        direction = cursor.getString(1),
                        tradeType = cursor.getString(2),
                        counterparty = if (includeCounterparty) cursor.getString(3) else "",
                        description = cursor.getString(4),
                        paymentMethod = if (includeCounterparty) cursor.getString(5) else ""
                    )) ids += cursor.getLong(0)
            }
        }
        val values = ContentValues().apply {
            put("flow_type", FlowType.CREDIT_REPAYMENT.name)
            put("category", "信用卡还款")
        }
        var updated = 0
        ids.forEach { id ->
            updated += db.update(
                "transactions", values, "id=? AND nature_modified=0",
                arrayOf(id.toString())
            )
        }
        return updated
    }

    fun recheckCreditRepayments(): Int {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val count = backfillCreditRepayments(db, includeCounterparty = true)
            db.setTransactionSuccessful()
            return count
        } finally {
            db.endTransaction()
        }
    }

    private fun createLoanDetailsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS loan_repayment_details (
                transaction_id INTEGER PRIMARY KEY REFERENCES transactions(id),
                principal_cent INTEGER NOT NULL CHECK (principal_cent >= 0),
                interest_cent INTEGER NOT NULL CHECK (interest_cent >= 0),
                fee_cent INTEGER NOT NULL CHECK (fee_cent >= 0),
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
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
                    put("nature_modified", 0)
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
                            "fingerprint=? AND flow_type='PENDING' AND nature_modified=0",
                            arrayOf(item.fingerprint)
                        )
                    }
                    if (item.flowType == FlowType.LOAN_REPAYMENT) {
                        writableDatabase.update(
                            "transactions",
                            ContentValues().apply {
                                put("flow_type", "LOAN_REPAYMENT")
                                put("category", "贷款还款")
                                put("trade_type", item.tradeType)
                            },
                            "fingerprint=? AND flow_type='TRANSFER' AND category='资金流转' AND nature_modified=0",
                            arrayOf(item.fingerprint)
                        )
                    }
                    // Credit defaults are repaired in one batch after importing.
                } else {
                    inserted++
                }
            }
            if (items.any { it.flowType == FlowType.CREDIT_REPAYMENT }) {
                backfillCreditRepayments(writableDatabase, includeCounterparty = true)
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
            "EXPENSE" -> where += "flow_type IN ('EXPENSE','GIFT_EXPENSE','BUSINESS_EXPENSE','LOAN_OUT','CREDIT_REPAYMENT','LOAN_REPAYMENT')"
            "INCOME" -> where += "flow_type IN ('INCOME','GIFT_INCOME','BUSINESS_INCOME','LOAN_RECOVERY','LOAN_DISBURSEMENT','REFUND')"
            "OTHER" -> where += "flow_type IN ('TRANSFER','PENDING','IGNORE')"
        }
        if (query.isNotBlank()) {
            val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            val money=runCatching {
                java.math.BigDecimal(query.trim()).movePointRight(2)
                    .toBigIntegerExact().longValueExact()
            }.getOrNull()
            where += "(counterparty LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\' OR trade_type LIKE ? ESCAPE '\\' OR category LIKE ? ESCAPE '\\' OR transaction_id LIKE ? ESCAPE '\\' OR merchant_order_id LIKE ? ESCAPE '\\'"+
                if(money==null) ")" else " OR amount_cent=?)"
            repeat(6) { args += "%$escaped%" }
            if(money!=null) args+=money.toString()
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

    fun scanMerchantLabels():Map<Long,String> {
        readableDatabase.rawQuery(
            "SELECT transaction_id,display_name FROM scan_merchant_labels",null
        ).use { c ->
            return buildMap {
                while(c.moveToNext()) put(c.getLong(0),c.getString(1))
            }
        }
    }

    fun saveScanMerchantLabel(id:Long,name:String) {
        val label=name.trim()
        require(id>0 && label.length in 1..80 &&
            !ScanPaymentClassifier.isGenericCounterparty(label)) {
            "请输入明确的商户或收款方名称"
        }
        writableDatabase.rawQuery(
            "SELECT flow_type,direction_text,trade_type,description FROM transactions WHERE id=?",
            arrayOf(id.toString())
        ).use { c ->
            require(c.moveToFirst() && c.getString(0)=="EXPENSE" &&
                ScanPaymentClassifier.isQrPayment(
                    c.getString(1),c.getString(2),c.getString(3)
                )) { "只能补充扫码消费的收款方" }
        }
        writableDatabase.insertWithOnConflict(
            "scan_merchant_labels",null,ContentValues().apply {
                put("transaction_id",id)
                put("display_name",label)
                put("updated_at",System.currentTimeMillis())
            },SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun removeScanMerchantLabel(id:Long) {
        writableDatabase.delete("scan_merchant_labels","transaction_id=?",
            arrayOf(id.toString()))
    }

    fun manualScanLinks():Map<Long,Long> {
        readableDatabase.rawQuery("SELECT manual_id,imported_id FROM manual_scan_links",null)
            .use { c ->
                return buildMap {
                    while(c.moveToNext()) put(c.getLong(0),c.getLong(1))
                }
            }
    }

    fun addManualScanExpense(
        merchant:String,amountCent:Long,occurredAt:Long,category:String,note:String
    ):Long {
        require(amountCent>0 && occurredAt>0 && category.isNotBlank())
        require(merchant.trim().length in 1..100)
        val id=writableDatabase.insertOrThrow(
            "transactions",null,ContentValues().apply {
                put("platform",Platform.UNKNOWN.name)
                put("occurred_at",occurredAt)
                put("counterparty",merchant.trim())
                put("description",note.trim().ifBlank { "手动扫码消费" }.take(300))
                put("direction_text","支出")
                put("trade_type","手动扫码消费")
                put("amount_cent",amountCent)
                put("flow_type",FlowType.EXPENSE.name)
                put("category",category)
                put("nature_modified",1)
                put("payment_method","手动登记")
                put("transaction_id","")
                put("merchant_order_id","")
                put("source_file","手动记账")
                put("fingerprint","manual:"+java.util.UUID.randomUUID().toString())
            }
        )
        check(id>0) { "手动账单保存失败" }
        return id
    }

    fun scanHistory(limit:Int=3000):List<Transaction> {
        readableDatabase.query(
            "transactions",null,
            "(flow_type='EXPENSE' OR source_file='手动记账')",
            null,null,null,"occurred_at DESC",
            limit.coerceIn(1,10000).toString()
        ).use { c ->
            return buildList {
                while(c.moveToNext()) {
                    val tx=c.toTransaction()
                    if(ScanPaymentClassifier.isQrPayment(
                            tx.directionText,tx.tradeType,tx.description
                        )) add(tx)
                }
            }
        }
    }

    fun linkManualScanExpense(manualId:Long,importedId:Long) {
        require(manualId!=importedId && manualId>0 && importedId>0)
        val db=writableDatabase
        db.beginTransaction()
        try {
            val m=db.rawQuery(
                "SELECT amount_cent,occurred_at,flow_type,category,source_file " +
                    "FROM transactions WHERE id=?",arrayOf(manualId.toString())
            ).use { c ->
                require(c.moveToFirst() && c.getString(4)=="手动记账" &&
                    c.getString(2)=="EXPENSE") { "只能关联尚未关联的手动扫码消费" }
                Triple(c.getLong(0),c.getLong(1),c.getString(3))
            }
            val existingRefund=db.rawQuery(
                "SELECT 1 FROM transaction_links WHERE expense_id=? OR receipt_id=? LIMIT 1",
                arrayOf(manualId.toString(),manualId.toString())
            ).use { it.moveToFirst() }
            require(!existingRefund) {
                "该手动消费已关联退款或分摊，请先撤销原关联再与正式账单去重"
            }
            val target=db.rawQuery(
                "SELECT amount_cent,occurred_at,flow_type,platform,trade_type,description,direction_text "+
                    "FROM transactions WHERE id=?",arrayOf(importedId.toString())
            ).use { c ->
                require(c.moveToFirst() && c.getString(2)=="EXPENSE" &&
                    c.getString(3)!=Platform.UNKNOWN.name &&
                    ScanPaymentClassifier.isQrPayment(c.getString(6),c.getString(4),c.getString(5))) {
                    "只能关联已经导入的扫码消费"
                }
                c.getLong(0) to c.getLong(1)
            }
            require(m.first==target.first &&
                kotlin.math.abs(m.second-target.second)<=7L*24L*3600L*1000L) {
                "仅可关联金额相等、日期相差不超过七天的消费"
            }
            db.insertOrThrow("manual_scan_links",null,ContentValues().apply {
                put("manual_id",manualId)
                put("imported_id",importedId)
                put("original_category",m.third)
                put("linked_at",System.currentTimeMillis())
            })
            check(db.update("transactions",ContentValues().apply {
                put("flow_type",FlowType.IGNORE.name)
                put("category","已关联原账单")
            },"id=? AND source_file='手动记账' AND flow_type='EXPENSE'",
                arrayOf(manualId.toString()))==1)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun unlinkManualScanExpense(manualId:Long) {
        val db=writableDatabase
        db.beginTransaction()
        try {
            val original=db.rawQuery(
                "SELECT original_category FROM manual_scan_links WHERE manual_id=?",
                arrayOf(manualId.toString())
            ).use { c ->
                require(c.moveToFirst()) { "该笔手动记录尚未关联" }
                c.getString(0)
            }
            db.update("transactions",ContentValues().apply {
                put("flow_type",FlowType.EXPENSE.name)
                put("category",original)
            },"id=? AND source_file='手动记账'",
                arrayOf(manualId.toString()))
            db.delete("manual_scan_links","manual_id=?",arrayOf(manualId.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun deleteManualScanExpense(id:Long) {
        val linked=readableDatabase.rawQuery(
            "SELECT 1 FROM manual_scan_links WHERE manual_id=? LIMIT 1",
            arrayOf(id.toString())
        ).use { it.moveToFirst() }
        require(!linked) { "请先取消原账单关联" }
        val linkedRecovery=readableDatabase.rawQuery(
            "SELECT 1 FROM transaction_links WHERE expense_id=? OR receipt_id=? LIMIT 1",
            arrayOf(id.toString(),id.toString())
        ).use { it.moveToFirst() }
        require(!linkedRecovery) { "请先撤销退款或 AA 关联再删除" }
        require(writableDatabase.delete("transactions",
            "id=? AND source_file='手动记账'",arrayOf(id.toString()))==1) {
            "手动记录不存在"
        }
        writableDatabase.delete("scan_merchant_labels","transaction_id=?",arrayOf(id.toString()))
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

    private fun loanTotals(month: YearMonth, platform: Platform?): LongArray {
        val (start, end) = monthRange(month)
        val extra = if (platform == null) "" else " AND t.platform=?"
        val args = mutableListOf(start.toString(), end.toString())
        platform?.let { args += it.name }
        readableDatabase.rawQuery(
            """
            SELECT COALESCE(SUM(t.amount_cent),0), COUNT(t.id),
                   COALESCE(SUM(d.principal_cent),0),
                   COALESCE(SUM(d.interest_cent),0),
                   COALESCE(SUM(d.fee_cent),0)
            FROM transactions t LEFT JOIN loan_repayment_details d
              ON d.transaction_id=t.id
            WHERE t.flow_type='LOAN_REPAYMENT'
              AND t.occurred_at>=? AND t.occurred_at<?$extra
            """.trimIndent(), args.toTypedArray()
        ).use { c ->
            c.moveToFirst()
            return LongArray(5) { c.getLong(it) }
        }
    }

    fun loanDetails(): Map<Long, LoanRepaymentDetail> {
        readableDatabase.rawQuery(
            "SELECT transaction_id,principal_cent,interest_cent,fee_cent FROM loan_repayment_details",
            null
        ).use { c ->
            return buildMap {
                while (c.moveToNext()) {
                    put(c.getLong(0), LoanRepaymentDetail(
                        c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3)
                    ))
                }
            }
        }
    }

    fun saveLoanDetail(
        transactionId: Long, principalCent: Long, interestCent: Long, feeCent: Long
    ) {
        val repayment = readableDatabase.rawQuery(
            "SELECT amount_cent,flow_type FROM transactions WHERE id=?",
            arrayOf(transactionId.toString())
        ).use { c ->
            require(c.moveToFirst()) { "找不到这笔贷款还款" }
            c.getLong(0) to c.getString(1)
        }
        require(repayment.second == "LOAN_REPAYMENT") { "仅贷款还款支持本金/利息拆分" }
        LoanRepaymentPolicy.validate(repayment.first, principalCent, interestCent, feeCent)
        writableDatabase.insertWithOnConflict(
            "loan_repayment_details", null,
            ContentValues().apply {
                put("transaction_id", transactionId)
                put("principal_cent", principalCent)
                put("interest_cent", interestCent)
                put("fee_cent", feeCent)
                put("updated_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun clearLoanDetail(transactionId: Long) {
        require(transactionId > 0)
        writableDatabase.delete(
            "loan_repayment_details", "transaction_id=?",
            arrayOf(transactionId.toString())
        )
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
              COALESCE(SUM(CASE WHEN flow_type IN ('INCOME','GIFT_INCOME','BUSINESS_INCOME')
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
              COALESCE(SUM(CASE WHEN flow_type='LOAN_OUT' THEN amount_cent ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN flow_type='TRANSFER' AND category='资金提现'
                THEN amount_cent ELSE 0 END), 0)
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
            val loan = loanTotals(month, platform)
            val financeCost = loan[3] + loan[4]
            val disbursed = readableDatabase.rawQuery(
                "SELECT COALESCE(SUM(amount_cent),0) FROM transactions WHERE " +
                    "flow_type='LOAN_DISBURSEMENT' AND occurred_at>=? AND occurred_at<?" +
                    if (platform == null) "" else " AND platform=?",
                mutableListOf(start.toString(),end.toString()).apply {
                    platform?.let { add(it.name) }
                }.toTypedArray()
            ).use { x -> x.moveToFirst(); x.getLong(0) }
            return DashboardSummary(
                loanRepaymentCent = loan[0],
                loanRepaymentCount = loan[1].toInt(),
                loanDisbursementCent = disbursed,
                loanPrincipalCent = loan[2],
                loanInterestCent = loan[3],
                loanFeeCent = loan[4],
                loanUnallocatedCent = loan[0] - loan[2] - financeCost,
                linkedRefundCent = linked.first,
                linkedShareCent = linked.second,
                creditRepaymentCent = c.getLong(12) +
                    if (platform == null) creditManualPayments(month)
                        .filter { it.countsAsRepayment }.sumOf { it.amountCent } else 0L,
                creditRepaymentCount = c.getInt(13) +
                    if (platform == null) creditManualPayments(month)
                        .count { it.countsAsRepayment } else 0,
                businessExpenseCent = c.getLong(14),
                loanOutCent = c.getLong(15),
                withdrawalCent = c.getLong(16),
                expenseCent = c.getLong(0) + financeCost,
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

        val results = readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) add(CategoryTotal(c.getString(0), c.getLong(1), c.getInt(2)))
            }
        }
        val finance = loanTotals(month, platform)
        val cost = finance[3] + finance[4]
        val categories = if (cost > 0) {
            results + CategoryTotal("金融费用", cost, finance[1].toInt())
        } else results
        return categories.sortedByDescending { it.amountCent }.take(limit)
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

        val totals = readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) add(DailyTotal(c.getInt(0), c.getLong(1), c.getInt(2)))
            }
        }.associateBy { it.dayOfMonth }.toMutableMap()
        val extra = if (platform == null) "" else " AND t.platform=?"
        val financeArgs = mutableListOf(start.toString(), end.toString())
        platform?.let { financeArgs += it.name }
        readableDatabase.rawQuery(
            """
            SELECT CAST(strftime('%d',t.occurred_at/1000,'unixepoch','localtime') AS INTEGER),
                   SUM(d.interest_cent+d.fee_cent),COUNT(*)
            FROM transactions t JOIN loan_repayment_details d ON d.transaction_id=t.id
            WHERE t.flow_type='LOAN_REPAYMENT' AND t.occurred_at>=?
              AND t.occurred_at<?$extra
            GROUP BY 1
            HAVING SUM(d.interest_cent+d.fee_cent)>0
            """.trimIndent(), financeArgs.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) {
                val day=c.getInt(0)
                val old=totals[day]
                totals[day]=DailyTotal(
                    day, (old?.amountCent ?: 0L)+c.getLong(1),
                    (old?.count ?: 0)+c.getInt(2)
                )
            }
        }
        return totals.values.sortedBy { it.dayOfMonth }
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
                put("nature_modified", 1)
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
               WHERE (flow_type IN ('EXPENSE','GIFT_EXPENSE','BUSINESS_EXPENSE','LOAN_OUT','CREDIT_REPAYMENT','LOAN_REPAYMENT')
                      AND direction_text LIKE '%收入%')
                  OR (flow_type IN ('INCOME','GIFT_INCOME','BUSINESS_INCOME','LOAN_RECOVERY','LOAN_DISBURSEMENT')
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
        val invalidLoanDetails = count(
            """
            SELECT COUNT(*) FROM loan_repayment_details d
            LEFT JOIN transactions t ON t.id=d.transaction_id
            WHERE t.id IS NULL OR t.flow_type <> 'LOAN_REPAYMENT'
               OR d.principal_cent+d.interest_cent+d.fee_cent <> t.amount_cent
            """.trimIndent()
        )
        val unallocatedLoans = count(
            """
            SELECT COUNT(*) FROM transactions t
            LEFT JOIN loan_repayment_details d ON d.transaction_id=t.id
            WHERE t.flow_type='LOAN_REPAYMENT' AND d.transaction_id IS NULL
            """.trimIndent()
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
            databaseIntegrityOk=good,
            loanBreakdownInvalid=invalidLoanDetails,
            loanUnallocatedCount=unallocatedLoans
        )
    }

    fun updateNature(id: Long, flowType: FlowType, category: String) {
        require(id > 0) { "无效流水" }
        require(category.isNotBlank()) { "请选择分类" }
        val linkedManualScan=readableDatabase.rawQuery(
            "SELECT 1 FROM manual_scan_links WHERE manual_id=? OR imported_id=? LIMIT 1",
            arrayOf(id.toString(),id.toString())
        ).use { it.moveToFirst() }
        require(!linkedManualScan) { "该笔扫码消费已关联手动记录，请先取消关联" }
        val linked = readableDatabase.rawQuery(
            "SELECT 1 FROM transaction_links WHERE expense_id=? OR receipt_id=? LIMIT 1",
            arrayOf(id.toString(), id.toString())
        ).use { it.moveToFirst() }
        require(!linked) { "这笔流水已有退款或 AA 关联，请先撤销关联再修改性质" }
        val linkedManual=readableDatabase.rawQuery(
            "SELECT 1 FROM manual_credit_repayments WHERE linked_transaction_id=? LIMIT 1",
            arrayOf(id.toString())
        ).use { it.moveToFirst() }
        require(!linkedManual || flowType==FlowType.CREDIT_REPAYMENT) {
            "该还款已关联手动补录，请先在信用卡管理中取消关联"
        }
        val split=readableDatabase.rawQuery(
            "SELECT 1 FROM loan_repayment_details WHERE transaction_id=? LIMIT 1",
            arrayOf(id.toString())
        ).use { it.moveToFirst() }
        require(!split || flowType==FlowType.LOAN_REPAYMENT) {
            "已记录本金和利息，请先撤销拆分再修改交易性质"
        }
        writableDatabase.update(
            "transactions",
            ContentValues().apply {
                put("flow_type", flowType.name)
                put("category", category)
                put("nature_modified", 1)
            },
            "id=?",
            arrayOf(id.toString())
        )
    }

    fun updateCategory(id: Long, merchant: String, category: String, rememberMerchant: Boolean) {
        val scanLinked=readableDatabase.rawQuery(
            "SELECT 1 FROM manual_scan_links WHERE manual_id=? LIMIT 1",
            arrayOf(id.toString())
        ).use { it.moveToFirst() }
        require(!scanLinked) { "手动记录已关联正式账单，请先取消关联后修改分类" }
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update(
                "transactions",
                ContentValues().apply {
                    put("category", category)
                    put("nature_modified", 1)
                },
                "id=?",
                arrayOf(id.toString())
            )
            if (rememberMerchant && merchant.isNotBlank() &&
                !ScanPaymentClassifier.isGenericCounterparty(merchant)) {
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
                    ContentValues().apply {
                        put("category", category)
                    },
                    "counterparty=? AND flow_type='EXPENSE' AND nature_modified=0",
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


    fun existingFingerprints(fingerprints: Collection<String>): Set<String> {
        if (fingerprints.isEmpty()) return emptySet()
        return buildSet {
            fingerprints.distinct().chunked(350).forEach { chunk ->
                val marks = chunk.joinToString(",") { "?" }
                readableDatabase.rawQuery(
                    "SELECT fingerprint FROM transactions WHERE fingerprint IN (" + marks + ")",
                    chunk.toTypedArray()
                ).use { c ->
                    while (c.moveToNext()) add(c.getString(0))
                }
            }
        }
    }

    fun rangeTransactions(
        start:Long,end:Long,platform:Platform?=null,limit:Int=3000
    ):List<Transaction> {
        require(start<end)
        val filter="occurred_at>=? AND occurred_at<?" +
            if(platform==null) "" else " AND platform=?"
        val args=mutableListOf(start.toString(),end.toString())
        platform?.let { args+=it.name }
        readableDatabase.query(
            "transactions",null,filter,args.toTypedArray(),null,null,
            "occurred_at DESC, id DESC",limit.coerceIn(1,10000).toString()
        ).use { c ->
            return buildList { while(c.moveToNext()) add(c.toTransaction()) }
        }
    }

    fun trend30(month:YearMonth,platform:Platform?=null):List<TrendPoint> {
        val zone=ZoneId.systemDefault()
        val endDay=if(month==YearMonth.now(zone)) LocalDate.now(zone)
            else month.atEndOfMonth()
        val first=endDay.minusDays(29)
        val start=first.atStartOfDay(zone).toInstant().toEpochMilli()
        val end=endDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val args=mutableListOf(start.toString(),end.toString())
        val platformSql=if(platform==null) "" else " AND t.platform=?"
        platform?.let { args+=it.name }
        val sums=mutableMapOf<LocalDate,Long>()
        readableDatabase.rawQuery(
            """
            SELECT t.occurred_at,t.flow_type,t.amount_cent,
              COALESCE((SELECT SUM(l.amount_cent) FROM transaction_links l
                WHERE l.expense_id=t.id),0),
              COALESCE(d.interest_cent+d.fee_cent,0)
            FROM transactions t LEFT JOIN loan_repayment_details d
              ON d.transaction_id=t.id
            WHERE t.occurred_at>=? AND t.occurred_at<?$platformSql
              AND t.flow_type IN ('EXPENSE','GIFT_EXPENSE','LOAN_REPAYMENT')
            """.trimIndent(),args.toTypedArray()
        ).use { c ->
            while(c.moveToNext()) {
                val date=Instant.ofEpochMilli(c.getLong(0)).atZone(zone).toLocalDate()
                val amount=when(c.getString(1)) {
                    "EXPENSE","GIFT_EXPENSE" -> c.getLong(2)-c.getLong(3)
                    else -> c.getLong(4)
                }
                sums[date]=(sums[date]?:0L)+amount
            }
        }
        return (0L..29L).map { delta ->
            val day=first.plusDays(delta)
            val from=day.atStartOfDay(zone).toInstant().toEpochMilli()
            val until=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            TrendPoint(
                label=day.monthValue.toString()+"/"+day.dayOfMonth,
                startAt=from,endAt=until,expenseCent=(sums[day]?:0L).coerceAtLeast(0)
            )
        }
    }

    fun trend12(month:YearMonth,platform:Platform?=null):List<TrendPoint> =
        (11 downTo 0).map { back ->
            val period=month.minusMonths(back.toLong())
            val (start,end)=monthRange(period)
            TrendPoint(
                label=period.year.toString()+"-"+period.monthValue.toString().padStart(2,'0'),
                startAt=start,endAt=end,
                expenseCent=summary(period,platform).netExpenseCent
            )
        }

    fun creditManualPayments(month: YearMonth? = null): List<ManualCreditRepayment> {
        val where = if (month == null) null else "occurred_at>=? AND occurred_at<?"
        val args = month?.let {
            val (start,end) = monthRange(it)
            arrayOf(start.toString(),end.toString())
        }
        readableDatabase.query(
            "manual_credit_repayments",null,where,args,null,null,
            "occurred_at DESC, id DESC"
        ).use { c ->
            return buildList {
                while (c.moveToNext()) add(ManualCreditRepayment(
                    id=c.getLong(c.getColumnIndexOrThrow("id")),
                    cardName=c.getString(c.getColumnIndexOrThrow("card_name")),
                    amountCent=c.getLong(c.getColumnIndexOrThrow("amount_cent")),
                    occurredAt=c.getLong(c.getColumnIndexOrThrow("occurred_at")),
                    note=c.getString(c.getColumnIndexOrThrow("note")),
                    linkedTransactionId=c.getColumnIndexOrThrow("linked_transaction_id").let {
                        if (c.isNull(it)) null else c.getLong(it)
                    },
                ))
            }
        }
    }

    fun creditCardAliases(): Map<String,String> {
        readableDatabase.rawQuery("SELECT source_key,display_name FROM credit_card_aliases",null)
            .use { c ->
                return buildMap {
                    while (c.moveToNext()) put(c.getString(0),c.getString(1))
                }
            }
    }

    fun renameCreditCard(source: String, displayName: String) {
        val normalized = source.trim().lowercase()
        val display = displayName.trim()
        require(normalized.isNotBlank() && display.length in 1..80) { "请输入有效的信用卡名称" }
        writableDatabase.insertWithOnConflict(
            "credit_card_aliases",null,
            ContentValues().apply {
                put("source_key",normalized)
                put("display_name",display)
            },SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun creditHistory(limit:Int=3000):List<Transaction> {
        readableDatabase.query(
            "transactions",null,"flow_type='CREDIT_REPAYMENT'",null,null,null,
            "occurred_at DESC",limit.coerceIn(1,10000).toString()
        ).use { c ->
            return buildList { while(c.moveToNext()) add(c.toTransaction()) }
        }
    }

    fun loanHistory(limit:Int=3000):List<Transaction> {
        readableDatabase.query(
            "transactions",null,"flow_type IN ('LOAN_REPAYMENT','LOAN_DISBURSEMENT')",
            null,null,null,"occurred_at DESC",limit.coerceIn(1,10000).toString()
        ).use { c ->
            return buildList { while(c.moveToNext()) add(c.toTransaction()) }
        }
    }

    fun creditCenter(month: YearMonth): CreditCenter {
        val (start,end) = monthRange(month)
        val imported = mutableListOf<Transaction>()
        val suspected = mutableListOf<Transaction>()
        readableDatabase.query(
            "transactions",null,
            "occurred_at>=? AND occurred_at<? AND flow_type='CREDIT_REPAYMENT'",
            arrayOf(start.toString(),end.toString()),null,null,
            "occurred_at DESC","3000"
        ).use { c -> while (c.moveToNext()) imported += c.toTransaction() }
        // This list is advisory only; never mutate the ledger based on a fuzzy match.
        readableDatabase.query(
            "transactions",null,
            "occurred_at>=? AND occurred_at<? AND nature_modified=0 AND " +
                "flow_type IN ('TRANSFER','PENDING','LOAN_REPAYMENT','EXPENSE') AND " +
                "(trade_type LIKE '%还款%' OR description LIKE '%还款%' OR " +
                "counterparty LIKE '%信用卡%' OR counterparty LIKE '%贷记卡%')",
            arrayOf(start.toString(),end.toString()),null,null,
            "occurred_at DESC","100"
        ).use { c ->
            while (c.moveToNext()) {
                val row=c.toTransaction()
                if (row.flowType!=FlowType.EXPENSE || row.category=="其他") suspected+=row
            }
        }
        return CreditCenter(imported,creditManualPayments(month),suspected,creditCardAliases())
    }

    fun addManualCreditRepayment(
        name: String, amountCent: Long, occurredAt: Long, note: String
    ): Long {
        require(name.trim().length in 1..80 && amountCent > 0 && occurredAt > 0) {
            "请输入有效的卡片名称、金额及还款日期"
        }
        val inserted=writableDatabase.insertOrThrow(
            "manual_credit_repayments",null,
            ContentValues().apply {
                put("card_name",name.trim())
                put("amount_cent",amountCent)
                put("occurred_at",occurredAt)
                put("note",note.take(300))
                put("created_at",System.currentTimeMillis())
            }
        )
        check(inserted>0)
        return inserted
    }

    fun linkManualCreditRepayment(manualId: Long, transactionId: Long) {
        val db=writableDatabase
        db.beginTransaction()
        try {
            val manual=db.rawQuery(
                "SELECT amount_cent,occurred_at,linked_transaction_id FROM manual_credit_repayments WHERE id=?",
                arrayOf(manualId.toString())
            ).use { c ->
                require(c.moveToFirst() && c.isNull(2)) { "补录已关联或不存在" }
                c.getLong(0) to c.getLong(1)
            }
            val source=db.rawQuery(
                "SELECT amount_cent,occurred_at,flow_type FROM transactions WHERE id=?",
                arrayOf(transactionId.toString())
            ).use { c ->
                require(c.moveToFirst() && c.getString(2)=="CREDIT_REPAYMENT") {
                    "仅可关联已确认的信用卡还款"
                }
                c.getLong(0) to c.getLong(1)
            }
            require(manual.first==source.first) { "关联的还款金额必须完全一致" }
            require(kotlin.math.abs(manual.second-source.second)<=90L*24*3600*1000) {
                "关联的两笔还款日期相差超过 90 天"
            }
            val count=db.update(
                "manual_credit_repayments",
                ContentValues().apply { put("linked_transaction_id",transactionId) },
                "id=? AND linked_transaction_id IS NULL",
                arrayOf(manualId.toString())
            )
            check(count==1) { "该补录已被其他操作修改" }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun unlinkManualCreditRepayment(manualId: Long) {
        writableDatabase.update(
            "manual_credit_repayments",
            ContentValues().apply { putNull("linked_transaction_id") },
            "id=?",arrayOf(manualId.toString())
        )
    }

    fun deleteManualCreditRepayment(manualId: Long) {
        writableDatabase.delete("manual_credit_repayments","id=?",arrayOf(manualId.toString()))
    }

    fun loanProfiles(): List<LoanProfile> {
        val registered=mutableMapOf<String,LoanProfile>()
        readableDatabase.rawQuery(
            "SELECT institution,original_amount_cent,remaining_principal_cent FROM loan_profiles",null
        ).use { c ->
            while (c.moveToNext()) registered[c.getString(0)]=LoanProfile(
                institution=c.getString(0),
                originalAmountCent=if (c.isNull(1)) null else c.getLong(1),
                remainingPrincipalCent=if (c.isNull(2)) null else c.getLong(2),
            )
        }
        readableDatabase.rawQuery(
            """
            SELECT COALESCE(NULLIF(TRIM(t.counterparty),''),'未知贷款机构'),
                   COUNT(*),COALESCE(SUM(t.amount_cent),0),
                   COALESCE(SUM(d.principal_cent),0),
                   COALESCE(SUM(d.interest_cent),0),
                   COALESCE(SUM(d.fee_cent),0),
                   SUM(CASE WHEN d.transaction_id IS NULL THEN 1 ELSE 0 END)
            FROM transactions t LEFT JOIN loan_repayment_details d
              ON d.transaction_id=t.id
            WHERE t.flow_type='LOAN_REPAYMENT'
            GROUP BY 1
            """.trimIndent(),null
        ).use { c ->
            while (c.moveToNext()) {
                val institution=c.getString(0)
                val old=registered[institution]?:LoanProfile(institution)
                registered[institution]=old.copy(
                    transactionCount=c.getInt(1),repaidCent=c.getLong(2),
                    principalCent=c.getLong(3),interestCent=c.getLong(4),
                    feeCent=c.getLong(5),missingSplitCount=c.getInt(6)
                )
            }
        }
        return registered.values.sortedByDescending { it.repaidCent }
    }

    fun saveLoanProfile(institution: String, original: Long?, remaining: Long?) {
        val key=institution.trim()
        require(key.length in 1..100) { "请选择或输入贷款机构" }
        require(original==null || original>0)
        require(remaining==null || remaining>=0)
        writableDatabase.insertWithOnConflict(
            "loan_profiles",null,ContentValues().apply {
                put("institution",key)
                if (original==null) putNull("original_amount_cent") else put("original_amount_cent",original)
                if (remaining==null) putNull("remaining_principal_cent") else put("remaining_principal_cent",remaining)
                put("updated_at",System.currentTimeMillis())
            },SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun deleteLoanProfile(institution: String) {
        writableDatabase.delete("loan_profiles","institution=?",arrayOf(institution))
    }

    fun ruleAffectedCount(merchant:String):Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM transactions WHERE counterparty=? " +
                "AND flow_type='EXPENSE' AND nature_modified=0",
            arrayOf(merchant.trim())
        ).use { c -> c.moveToFirst(); return c.getInt(0) }
    }

    fun categoryRules(): List<MerchantRule> {
        readableDatabase.rawQuery(
            """
            SELECT r.merchant,r.category,
              (SELECT COUNT(*) FROM transactions t WHERE t.counterparty=r.merchant
               AND t.flow_type='EXPENSE' AND t.nature_modified=0) affected
            FROM merchant_rules r ORDER BY LOWER(r.merchant)
            """.trimIndent(),null
        ).use { c ->
            return buildList {
                while (c.moveToNext()) add(MerchantRule(
                    c.getString(0),c.getString(1),c.getInt(2)
                ))
            }
        }
    }

    fun saveCategoryRule(merchant: String, category: String, applyExisting: Boolean): Int {
        val key=merchant.trim()
        require(key.isNotBlank() && key.length<=120 && category.isNotBlank() &&
            !ScanPaymentClassifier.isGenericCounterparty(key)) {
            "通用二维码名称不能记忆为商户，请在扫码消费中单独补名"
        }
        val db=writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                "merchant_rules",null,ContentValues().apply {
                    put("merchant",key)
                    put("category",category)
                    put("updated_at",System.currentTimeMillis())
                },SQLiteDatabase.CONFLICT_REPLACE
            )
            val affected=if (applyExisting) db.update(
                "transactions",ContentValues().apply { put("category",category) },
                "counterparty=? AND flow_type='EXPENSE' AND nature_modified=0",
                arrayOf(key)
            ) else 0
            db.setTransactionSuccessful()
            return affected
        } finally { db.endTransaction() }
    }

    fun deleteCategoryRule(merchant: String) {
        writableDatabase.delete("merchant_rules","merchant=?",arrayOf(merchant))
    }

    fun deleteMerchantAlias(source: String) {
        writableDatabase.delete("merchant_aliases","alias_key=?",arrayOf(source))
    }

    fun deleteProductAlias(key: String) {
        val divider=key.indexOf('|')
        require(divider>0)
        writableDatabase.delete(
            "product_aliases","merchant_key=? AND alias_key=?",
            arrayOf(key.substring(0,divider),key.substring(divider+1))
        )
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
        private const val DB_VERSION = 10
    }
}
