package com.sockc.billinsight.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.sockc.billinsight.model.*
import java.util.Locale

/**
 * Installment overlay on the user's existing imported bills.
 * Original transactions, financial nature and fingerprints are never modified here.
 */
class FinancePlanStore(private val helper: BillDatabase) {
    private fun Cursor.tx() = Transaction(
        id=getLong(getColumnIndexOrThrow("id")),
        platform=runCatching {
            Platform.valueOf(getString(getColumnIndexOrThrow("platform")))
        }.getOrDefault(Platform.UNKNOWN),
        occurredAt=getLong(getColumnIndexOrThrow("occurred_at")),
        counterparty=getString(getColumnIndexOrThrow("counterparty")),
        description=getString(getColumnIndexOrThrow("description")),
        directionText=getString(getColumnIndexOrThrow("direction_text")),
        tradeType=getString(getColumnIndexOrThrow("trade_type")),
        amountCent=getLong(getColumnIndexOrThrow("amount_cent")),
        flowType=runCatching {
            FlowType.valueOf(getString(getColumnIndexOrThrow("flow_type")))
        }.getOrDefault(FlowType.IGNORE),
        category=getString(getColumnIndexOrThrow("category")),
        paymentMethod=getString(getColumnIndexOrThrow("payment_method")),
        transactionId=getString(getColumnIndexOrThrow("transaction_id")),
        merchantOrderId=getString(getColumnIndexOrThrow("merchant_order_id")),
        sourceFile=getString(getColumnIndexOrThrow("source_file")),
        fingerprint=getString(getColumnIndexOrThrow("fingerprint"))
    )

    fun importedTransaction(id: Long): Transaction? {
        if(id<=0)return null
        return helper.readableDatabase.query("transactions",null,"id=?",
            arrayOf(id.toString()),null,null,null
        ).use { c -> if(c.moveToFirst())c.tx() else null }
    }

    fun createFromBill(
        sourceId: Long,
        kind: FinancePlanKind,
        beneficiary: String,
        title: String,
        totalCent: Long?,
        terms: Int?,
        dueDay: Int?,
    ): Long {
        val source=importedTransaction(sourceId) ?: error("找不到原始导入账单")
        require(source.platform!=Platform.UNKNOWN && !source.sourceFile.startsWith("手动")) {
            "请从导入的真实账单中选择分期记录"
        }
        require(source.flowType in setOf(
            FlowType.EXPENSE,FlowType.CREDIT_REPAYMENT,FlowType.LOAN_REPAYMENT
        )){"请先选择购买分期或分期还款账单"}
        val person=beneficiary.trim()
        require(kind!=FinancePlanKind.ADVANCE || person.length in 1..60) {
            "代付分期需要备注对方姓名"
        }
        require(title.trim().length in 1..90) {"请输入分期名称"}
        require(totalCent==null || totalCent>0) {"分期总金额须大于零"}
        require(terms==null || terms in 2..360) {"分期期数须在 2～360 期之间"}
        require(dueDay==null || dueDay in 1..31) {"还款日须在 1～31 日之间"}
        val reference=FinanceReference.explicitReference(source)
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            require(db.rawQuery(
                "SELECT 1 FROM finance_installment_links WHERE transaction_id=? LIMIT 1",
                arrayOf(sourceId.toString())
            ).use { !it.moveToFirst() }) {"这笔账单已关联其他分期"}
            if(reference!=null) {
                require(db.rawQuery(
                    """SELECT 1 FROM finance_installment_plans
                       WHERE platform=? AND plan_reference=? LIMIT 1""",
                    arrayOf(source.platform.name,reference)
                ).use { !it.moveToFirst() }) {
                    "此分期计划编号已存在，请在对应卡片管理账单"
                }
            }
            val time=System.currentTimeMillis()
            val id=db.insertOrThrow("finance_installment_plans",null,
                ContentValues().apply {
                    put("origin_transaction_id",sourceId)
                    put("platform",source.platform.name)
                    put("institution",source.counterparty.trim().ifBlank {
                        source.platform.name
                    })
                    put("title",title.trim())
                    put("kind",kind.name)
                    put("beneficiary",if(kind==FinancePlanKind.ADVANCE)person else "")
                    put("plan_reference",reference)
                    if(totalCent==null)putNull("total_cent") else put("total_cent",totalCent)
                    if(terms==null)putNull("term_count") else put("term_count",terms)
                    if(dueDay==null)putNull("due_day") else put("due_day",dueDay)
                    put("created_at",time)
                    put("updated_at",time)
                })
            addLink(db,id,source,FinanceLinkRole.ORIGIN,false)
            if(source.flowType in setOf(
                FlowType.CREDIT_REPAYMENT,FlowType.LOAN_REPAYMENT
            ) || (FinanceReference.isInstallmentEvidence(source) &&
                (source.tradeType+" "+source.description).contains("还款"))) {
                addLink(db,id,source,FinanceLinkRole.REPAYMENT,false)
            }
            db.setTransactionSuccessful()
            return id
        } finally {db.endTransaction()}
    }

    fun updatePlan(
        id: Long,title: String,beneficiary: String,
        totalCent: Long?,terms: Int?,dueDay: Int?,
    ) {
        val kind=helper.readableDatabase.rawQuery(
            "SELECT kind FROM finance_installment_plans WHERE id=?",
            arrayOf(id.toString())
        ).use { c ->
            require(c.moveToFirst()){"分期已不存在"}
            FinancePlanKind.valueOf(c.getString(0))
        }
        require(title.trim().length in 1..90)
        require(kind!=FinancePlanKind.ADVANCE || beneficiary.trim().length in 1..60)
        require(totalCent==null || totalCent>0)
        require(terms==null || terms in 2..360)
        require(dueDay==null || dueDay in 1..31)
        helper.writableDatabase.update(
            "finance_installment_plans",ContentValues().apply {
                put("title",title.trim())
                put("beneficiary",if(kind==FinancePlanKind.ADVANCE)beneficiary.trim() else "")
                if(totalCent==null)putNull("total_cent") else put("total_cent",totalCent)
                if(terms==null)putNull("term_count") else put("term_count",terms)
                if(dueDay==null)putNull("due_day") else put("due_day",dueDay)
                put("updated_at",System.currentTimeMillis())
            },"id=?",arrayOf(id.toString())
        )
    }

    fun deletePlan(id: Long) {
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("finance_installment_links","plan_id=?",arrayOf(id.toString()))
            db.delete("finance_installment_plans","id=?",arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
    }

    private fun addLink(
        db: SQLiteDatabase,planId: Long, tx: Transaction,
        role: FinanceLinkRole,auto: Boolean
    ) {
        // ORIGIN+REPAYMENT is the only permitted two-role association on one bill.
        val taken=db.rawQuery(
            """SELECT role,plan_id FROM finance_installment_links
               WHERE transaction_id=?""",arrayOf(tx.id.toString())
        ).use { c ->
            buildList {
                while(c.moveToNext())add(c.getString(0) to c.getLong(1))
            }
        }
        require(taken.all {
            it.second==planId && (
                (it.first=="ORIGIN" && role==FinanceLinkRole.REPAYMENT) ||
                (it.first=="REPAYMENT" && role==FinanceLinkRole.ORIGIN) ||
                it.first==role.name
            )
        }){"这笔流水已属于其他分期，不可重复关联"}
        db.insertWithOnConflict(
            "finance_installment_links",null,ContentValues().apply {
                put("plan_id",planId)
                put("transaction_id",tx.id)
                put("role",role.name)
                put("auto_linked",if(auto)1 else 0)
            },SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun linkBill(planId: Long,transactionId: Long,role: FinanceLinkRole) {
        require(role!=FinanceLinkRole.ORIGIN){"原始账单不可手工更换"}
        val plan=plans().firstOrNull {it.id==planId} ?: error("分期记录不存在")
        val tx=importedTransaction(transactionId) ?: error("找不到这笔账单")
        if(role==FinanceLinkRole.REPAYMENT) {
            require(tx.platform==plan.platform) {"请选择相同平台的还款记录"}
            require(tx.flowType in setOf(
                FlowType.EXPENSE,FlowType.CREDIT_REPAYMENT,FlowType.LOAN_REPAYMENT
            )){"这笔账单不是付款或还款"}
        } else {
            require(plan.kind==FinancePlanKind.ADVANCE &&
                tx.flowType in setOf(
                    FlowType.INCOME,FlowType.GIFT_INCOME,FlowType.LOAN_RECOVERY,
                    FlowType.BUSINESS_INCOME
                )){"只能将收到的款项关联到代付分期"}
        }
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            if(role==FinanceLinkRole.RECOVERY) require(db.rawQuery(
                "SELECT 1 FROM transaction_links WHERE receipt_id=? LIMIT 1",
                arrayOf(tx.id.toString())
            ).use {!it.moveToFirst()}) {
                "这笔收入已关联消费退款或 AA 收款，请选择其他收款记录"
            }
            addLink(db,planId,tx,role,false)
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
    }

    fun unlinkBill(planId: Long,transactionId: Long,role: FinanceLinkRole) {
        require(role!=FinanceLinkRole.ORIGIN){"不能移除分期原始账单"}
        helper.writableDatabase.delete("finance_installment_links",
            "plan_id=? AND transaction_id=? AND role=?",
            arrayOf(planId.toString(),transactionId.toString(),role.name))
    }

    fun plans(): List<FinancePlan> {
        val db=helper.readableDatabase
        val meta=db.rawQuery(
            """SELECT id,origin_transaction_id,platform,institution,title,
                      kind,beneficiary,plan_reference,total_cent,term_count,due_day
               FROM finance_installment_plans ORDER BY created_at DESC""",null
        ).use { c ->
            buildList {
                while(c.moveToNext())add(
                    FinancePlan(
                        c.getLong(0),c.getLong(1),
                        Platform.valueOf(c.getString(2)),
                        c.getString(3),c.getString(4),
                        FinancePlanKind.valueOf(c.getString(5)),
                        c.getString(6),if(c.isNull(7))null else c.getString(7),
                        if(c.isNull(8))null else c.getLong(8),
                        if(c.isNull(9))null else c.getInt(9),
                        if(c.isNull(10))null else c.getInt(10),emptyList()
                    )
                )
            }
        }
        if(meta.isEmpty())return meta
        val grouped=db.rawQuery(
            """SELECT l.plan_id,l.role,l.auto_linked,t.*
               FROM finance_installment_links l
               JOIN transactions t ON t.id=l.transaction_id
               ORDER BY t.occurred_at DESC,t.id DESC""",null
        ).use { c ->
            buildMap<Long,MutableList<FinancePlanLink>> {
                while(c.moveToNext()) {
                    val id=c.getLong(0)
                    val role=FinanceLinkRole.valueOf(c.getString(1))
                    val auto=c.getInt(2)!=0
                    getOrPut(id){mutableListOf()}.add(FinancePlanLink(c.tx(),role,auto))
                }
            }
        }
        return meta.map {it.copy(links=grouped[it.id].orEmpty())}
    }

    /**
     * Called when UI refreshes, including after bill import. No amount/date-only matching.
     * Auto-link only explicit identical plan identifiers on the SAME billing platform.
     */
    fun syncVerifiedHistory(): Int {
        val db=helper.writableDatabase
        val ready=plans().filter {it.planReference!=null}
        if(ready.isEmpty())return 0
        var added=0
        db.beginTransaction()
        try {
            ready.forEach { plan ->
                val tx=db.rawQuery(
                    """SELECT t.* FROM transactions t
                       WHERE t.platform=?
                         AND t.id<>?
                         AND NOT EXISTS(
                           SELECT 1 FROM finance_installment_links l
                           WHERE l.transaction_id=t.id)
                       ORDER BY t.occurred_at DESC LIMIT 10000""",
                    arrayOf(plan.platform.name,plan.originTransactionId.toString())
                ).use { c ->
                    buildList {
                        while(c.moveToNext()){
                            val item=c.tx()
                            if(FinanceReference.canAutoLink(
                                plan.platform,plan.planReference,item
                            )) add(item)
                        }
                    }
                }
                tx.forEach {
                    addLink(db,plan.id,it,FinanceLinkRole.REPAYMENT,true)
                    added++
                }
            }
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
        return added
    }

    fun candidates(planId: Long,role: FinanceLinkRole,search: String=""): List<FinanceLinkSuggestion> {
        require(role!=FinanceLinkRole.ORIGIN)
        val plan=plans().firstOrNull {it.id==planId} ?: return emptyList()
        if(role==FinanceLinkRole.RECOVERY && plan.kind!=FinancePlanKind.ADVANCE)
            return emptyList()
        val seed=importedTransaction(plan.originTransactionId) ?: return emptyList()
        val where=if(role==FinanceLinkRole.REPAYMENT)
            "t.platform=? AND t.flow_type IN ('EXPENSE','CREDIT_REPAYMENT','LOAN_REPAYMENT')"
        else "t.flow_type IN ('INCOME','GIFT_INCOME','BUSINESS_INCOME','LOAN_RECOVERY')"
        val args=if(role==FinanceLinkRole.REPAYMENT)
            arrayOf(plan.platform.name) else emptyArray()
        val needle=search.trim().lowercase(Locale.ROOT)
        val found=helper.readableDatabase.rawQuery(
            """SELECT t.* FROM transactions t WHERE $where AND t.id<>?
               AND NOT EXISTS(SELECT 1 FROM finance_installment_links l
                              WHERE l.transaction_id=t.id)
               ORDER BY t.occurred_at DESC LIMIT 10000""".replace(
                "t.id<>?",if(role==FinanceLinkRole.REPAYMENT)"t.id<>?" else "t.id<>?"
            ),args+seed.id.toString()
        ).use { c ->
            buildList {
                while(c.moveToNext())add(c.tx())
            }
        }
        return found.mapNotNull { candidate ->
            val reason=if(role==FinanceLinkRole.REPAYMENT) FinanceReference.candidateReason(
                seed,candidate,plan.planReference
            ) else null
            val matched=needle.isNotBlank() && (
                candidate.counterparty.lowercase(Locale.ROOT).contains(needle) ||
                candidate.description.lowercase(Locale.ROOT).contains(needle) ||
                candidate.amountCent.toString().contains(needle) ||
                candidate.transactionId.lowercase(Locale.ROOT).contains(needle)
            )
            if(role==FinanceLinkRole.REPAYMENT) {
                if(reason==null && !matched)return@mapNotNull null
                FinanceLinkSuggestion(candidate,reason?:"手动搜索结果，需确认",reason=="同一明确分期计划编号")
            } else {
                // Receipts are never inferred as a person's repayment, even if names match.
                if(needle.isBlank())return@mapNotNull FinanceLinkSuggestion(
                    candidate,"请选择实际由对方转来的款项",false
                )
                if(!matched)return@mapNotNull null
                FinanceLinkSuggestion(candidate,"请核对收款人及金额",false)
            }
        }.sortedWith(compareByDescending<FinanceLinkSuggestion>{it.exactReference}
            .thenByDescending{it.transaction.occurredAt}).take(180)
    }

    fun entrustedOriginIds(): Set<Long> =
        helper.readableDatabase.rawQuery(
            """SELECT p.origin_transaction_id FROM finance_installment_plans p
               JOIN transactions t ON t.id=p.origin_transaction_id
               WHERE p.kind='ADVANCE' AND t.flow_type='EXPENSE'""",null
        ).use {c->buildSet {while(c.moveToNext()) add(c.getLong(0))}}

    /** Borrower repaid funds are linked to existing income, not inserted again. */
    fun recoveryReceiptIds(): Set<Long> =
        helper.readableDatabase.rawQuery(
            """SELECT l.transaction_id FROM finance_installment_links l
               JOIN finance_installment_plans p ON p.id=l.plan_id
               WHERE l.role='RECOVERY' AND p.kind='ADVANCE'""",null
        ).use { c ->
            buildSet {while(c.moveToNext())add(c.getLong(0))}
        }
}
