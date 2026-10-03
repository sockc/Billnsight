package com.sockc.billinsight.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.sockc.billinsight.importer.CategoryWorkbenchPolicy
import com.sockc.billinsight.importer.MerchantCategoryPolicy
import com.sockc.billinsight.importer.MerchantLexicon
import com.sockc.billinsight.importer.TransactionClassifier
import com.sockc.billinsight.model.*
import java.util.Locale

/**
 * A single source of truth for category review in Home, Ledger and Analysis.
 * Original merchant, original description, fingerprint and transaction nature
 * remain untouched. Automatic edits are previewed and fully snapshotted.
 */
class CategoryWorkbenchStore(private val helper: BillDatabase) {
    private val db get() = helper.writableDatabase

    private fun Cursor.tx() = Transaction(
        id=getLong(getColumnIndexOrThrow("id")),
        platform=Platform.valueOf(getString(getColumnIndexOrThrow("platform"))),
        occurredAt=getLong(getColumnIndexOrThrow("occurred_at")),
        counterparty=getString(getColumnIndexOrThrow("counterparty")),
        description=getString(getColumnIndexOrThrow("description")),
        directionText=getString(getColumnIndexOrThrow("direction_text")),
        tradeType=getString(getColumnIndexOrThrow("trade_type")),
        amountCent=getLong(getColumnIndexOrThrow("amount_cent")),
        flowType=FlowType.valueOf(getString(getColumnIndexOrThrow("flow_type"))),
        category=getString(getColumnIndexOrThrow("category")),
        paymentMethod=getString(getColumnIndexOrThrow("payment_method")),
        transactionId=getString(getColumnIndexOrThrow("transaction_id")),
        merchantOrderId=getString(getColumnIndexOrThrow("merchant_order_id")),
        sourceFile=getString(getColumnIndexOrThrow("source_file")),
        fingerprint=getString(getColumnIndexOrThrow("fingerprint"))
    )
    private data class Rules(
        val aliases:Map<String,String>,
        val generic:Map<String,String>,
        val platform:Map<Pair<Platform,String>,String>,
        val cross:Map<String,String>,
        val product:Map<Triple<Platform,String,String>,String>
    )
    private var cachedRules:Rules?=null
    private fun rules(): Rules {
        cachedRules?.let {return it}
        val aliases=helper.merchantAliases()
            .mapKeys {MerchantCategoryPolicy.normalize(it.key)}
        val generic=helper.merchantRules()
            .mapKeys {MerchantCategoryPolicy.normalize(it.key)}
        val perPlatform=mutableMapOf<Pair<Platform,String>,String>()
        db.rawQuery("SELECT platform,merchant,category FROM platform_category_rules",null)
            .use { c -> while(c.moveToNext()) {
                val platform=runCatching{Platform.valueOf(c.getString(0))}.getOrNull()
                if(platform!=null) {
                    val name=MerchantCategoryPolicy.normalize(c.getString(1))
                    val canonical=MerchantCategoryPolicy.normalize(aliases[name]?:name)
                    perPlatform[platform to canonical]=c.getString(2)
                }
            }}
        val cross=mutableMapOf<String,String>()
        db.rawQuery("SELECT merchant,category FROM cross_platform_category_rules",null)
            .use {c->while(c.moveToNext())cross[c.getString(0)]=c.getString(1)}
        val product=mutableMapOf<Triple<Platform,String,String>,String>()
        db.rawQuery("SELECT platform,merchant,product,category FROM product_category_rules",null)
            .use {c->while(c.moveToNext()){
                val platform=runCatching {Platform.valueOf(c.getString(0))}.getOrNull()
                if(platform!=null) product[Triple(platform,c.getString(1),c.getString(2))]=c.getString(3)
            }}
        return Rules(aliases,generic,perPlatform,cross,product)
            .also {cachedRules=it}
    }
    private fun evaluate(
        tx:Transaction,manuallyEdited:Boolean,r:Rules
    ):CategoryReviewItem=CategoryWorkbenchPolicy.evaluate(
        tx,manuallyEdited,r.generic,r.aliases,r.platform,r.cross,r.product
    )

    private fun sourceTransactions(limit:Int):List<Pair<Transaction,Boolean>> =
        db.query("transactions",null,
            "flow_type='EXPENSE' AND source_file NOT LIKE '手动%'",
            null,null,null,"occurred_at DESC,id DESC",
            limit.coerceIn(1,100000).toString()
        ).use {c->buildList {while(c.moveToNext()) {
            add(c.tx() to (c.getInt(c.getColumnIndexOrThrow("nature_modified"))!=0))
        }}}

    fun reviewPreview(limit:Int=8000):AutoCategoryPreview {
        val total=db.rawQuery(
            "SELECT COUNT(*) FROM transactions WHERE flow_type='EXPENSE' AND source_file NOT LIKE '手动%'",null
        ).use {c->c.moveToFirst();c.getInt(0)}
        val rows=sourceTransactions(limit)
        val r=rules()
        val evaluated=rows.map {(tx,manual)->evaluate(tx,manual,r)}
        val pending=evaluated.filter {it.needsReview}
        val groups=pending.groupBy { item ->
            val tx=item.transaction
            val name=MerchantCategoryPolicy.normalize(tx.counterparty)
            val canonical=MerchantCategoryPolicy.normalize(r.aliases[name]?:name)
            if(CategoryWorkbenchPolicy.isMixedMerchant(tx.counterparty))
                "single:${tx.id}"
            else "${tx.platform.name}:${canonical}:${item.proposedCategory.orEmpty()}"
        }.map {(key,items)->
            CategoryReviewGroup(
                key=key,
                label=items.first().transaction.counterparty.ifBlank {"未知商户"},
                category=items.map {it.proposedCategory}.distinct().singleOrNull(),
                evidence=items.map {it.basis}.distinct().take(2).joinToString("；"),
                items=items,
                batchEligible=!key.startsWith("single:")
            )
        }.sortedWith(compareByDescending<CategoryReviewGroup>{it.count}
            .thenBy{it.label})
        return AutoCategoryPreview(
            scanned=rows.size,
            totalImported=total,
            proposed=evaluated.count {!it.manuallyEdited &&
                it.proposedCategory!=null &&
                it.proposedCategory!=it.transaction.category},
            unresolved=pending.count {it.proposedCategory==null},
            protected=evaluated.count {it.manuallyEdited},
            groups=groups
        )
    }

    /** Counts only rows the specific manual scope can actually update. */
    fun scopeCounts(selected:Transaction):Pair<Int,Int> {
        val r=rules()
        val raw=MerchantCategoryPolicy.normalize(selected.counterparty)
        val canonical=MerchantCategoryPolicy.normalize(r.aliases[raw]?:raw)
        var cross=0
        var product=0
        val wantedProduct=MerchantLexicon.normalize(selected.description)
        db.query("transactions",null,
            "flow_type='EXPENSE' AND id<>?",
            arrayOf(selected.id.toString()),null,null,null
        ).use {c->while(c.moveToNext()){
            val tx=c.tx()
            val manual=c.getInt(c.getColumnIndexOrThrow("nature_modified"))!=0
            if(manual && tx.category!="其他")continue
            val other=MerchantCategoryPolicy.normalize(tx.counterparty)
            val identity=MerchantCategoryPolicy.normalize(r.aliases[other]?:other)
            if(identity!=canonical)continue
            if(tx.platform in setOf(Platform.WECHAT,Platform.ALIPAY) &&
                !CategoryWorkbenchPolicy.isMixedMerchant(tx.counterparty) &&
                !CategoryWorkbenchPolicy.isMixedMerchant(selected.counterparty))cross++
            if(tx.platform==selected.platform &&
                MerchantLexicon.normalize(tx.description)==wantedProduct &&
                wantedProduct.isNotBlank())product++
        }}
        return cross to product
    }

    fun pendingOtherCount():Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM transactions WHERE flow_type='EXPENSE' AND category='其他'",
            null
        ).use {c->c.moveToFirst();c.getInt(0)}

    private fun newBatch(label:String):Long=
        db.insertOrThrow("category_change_batches",null,ContentValues().apply {
            put("label",label.take(120))
            put("changed_at",System.currentTimeMillis())
        })

    private fun saveEvidence(
        tx:Transaction,category:String,term:String,source:String,review:Boolean
    ) {
        db.insertWithOnConflict("classification_evidence",null,
            ContentValues().apply {
                put("transaction_id",tx.id)
                put("normalized_merchant",MerchantCategoryPolicy.normalize(tx.counterparty))
                put("matched_term",term.take(120))
                put("source",source.take(150))
                put("applied_category",category)
                put("needs_review",if(review)1 else 0)
                put("updated_at",System.currentTimeMillis())
            },SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun rememberImportedEvidence(tx:Transaction,category:String):Boolean {
        if(tx.flowType!=FlowType.EXPENSE || tx.id<=0)return false
        val review=evaluate(tx,false,rules())
        saveEvidence(tx,category,review.matchedTerm,
            if(review.proposedCategory==category)review.basis
                else "原始导入分类；需核对",
            category=="其他" || review.proposedCategory!=category)
        return category=="其他" || review.needsReview
    }

    private fun setCategory(
        batch:Long,tx:Transaction,category:String,marked:Boolean,
        term:String,source:String
    ):Int {
        require(category in TransactionClassifier.categories)
        val old=db.query("transactions",arrayOf("category","nature_modified"),
            "id=? AND flow_type='EXPENSE'",arrayOf(tx.id.toString()),
            null,null,null).use {c->
            if(c.moveToFirst())c.getString(0) to c.getInt(1) else null
        }?:return 0
        val afterFlag=if(marked)1 else old.second
        if(old.first==category && old.second==afterFlag)return 0
        db.insertOrThrow("category_change_items",null,ContentValues().apply {
            put("batch_id",batch);put("transaction_id",tx.id)
            put("before_category",old.first);put("before_modified",old.second)
            put("after_category",category);put("after_modified",afterFlag)
        })
        val n=db.update("transactions",ContentValues().apply {
            put("category",category);put("nature_modified",afterFlag)
        },"id=? AND category=? AND nature_modified=?",
            arrayOf(tx.id.toString(),old.first,old.second.toString()))
        if(n==1)saveEvidence(tx,category,term,source,false)
        return n
    }

    private fun saveRuleChange(
        batch:Long,type:String,platform:String,merchant:String,
        before:String?,after:String
    ) {
        db.insertOrThrow("category_rule_changes",null,ContentValues().apply {
            put("batch_id",batch);put("rule_type",type)
            put("platform",platform);put("merchant",merchant)
            if(before==null)putNull("before_category")else put("before_category",before)
            put("after_category",after)
        })
    }
    private fun ruleBefore(table:String,where:String,args:Array<String>):String? =
        db.rawQuery("SELECT category FROM $table WHERE $where",args)
            .use {c->if(c.moveToFirst())c.getString(0) else null}

    /** The only manual category-write path for both Ledger and Analysis. */
    fun changeCategory(id:Long,category:String,scope:String):CategoryChangeBatch {
        require(category in TransactionClassifier.categories)
        require(scope in setOf("SINGLE","MERCHANT","FUTURE","CROSS","PRODUCT"))
        val selected=db.query("transactions",null,"id=?",
            arrayOf(id.toString()),null,null,null).use {c->
            if(c.moveToFirst())c.tx() else null
        }?:error("找不到这笔账单")
        require(selected.flowType==FlowType.EXPENSE){"只有个人消费可设置消费类别"}
        val r=rules()
        val normalized=MerchantCategoryPolicy.normalize(selected.counterparty)
        val canonical=MerchantCategoryPolicy.normalize(
            r.aliases[normalized]?:normalized)
        val mixed=CategoryWorkbenchPolicy.isMixedMerchant(selected.counterparty)
        val permitted=canonical.isNotBlank() && !mixed
        require(scope=="SINGLE" || scope=="PRODUCT" || permitted) {
            "无法安全确认相同商户，请仅修改本笔或使用具体商品规则"
        }
        require(scope!="PRODUCT" || (mixed &&
            MerchantLexicon.canRememberProductDescription(selected.description))) {
            "缺少具体商品描述，不保存综合平台的批量规则"
        }
        require(scope!="CROSS" || selected.platform in setOf(Platform.WECHAT,Platform.ALIPAY)) {
            "跨平台规则只适用于明确确认的微信和支付宝商户"
        }
        val product=MerchantLexicon.normalize(selected.description)
        val plans=mutableListOf<Transaction>()
        db.beginTransaction()
        try {
            val batch=newBatch("人工分类：${selected.counterparty} → $category")
            var changed=0
            if(scope!="FUTURE") {
                val targetScope=when(scope) {
                    "SINGLE"->setOf(selected.id)
                    else ->null
                }
                val platforms=if(scope=="CROSS")
                    setOf(Platform.WECHAT,Platform.ALIPAY) else setOf(selected.platform)
                platforms.forEach {platform->
                    db.query("transactions",null,
                        "platform=? AND flow_type='EXPENSE'",
                        arrayOf(platform.name),null,null,null
                    ).use {c->while(c.moveToNext()) {
                        val tx=c.tx()
                        val manual=c.getInt(c.getColumnIndexOrThrow("nature_modified"))!=0
                        val raw=MerchantCategoryPolicy.normalize(tx.counterparty)
                        val identity=MerchantCategoryPolicy.normalize(r.aliases[raw]?:raw)
                        val same=when {
                            targetScope!=null ->tx.id in targetScope
                            scope=="PRODUCT" ->identity==canonical &&
                                MerchantLexicon.normalize(tx.description)==product
                            else ->identity==canonical &&
                                !CategoryWorkbenchPolicy.isMixedMerchant(tx.counterparty)
                        }
                        if(same && (!manual || tx.category=="其他" || tx.id==selected.id))
                            plans.add(tx)
                    }}
                }
                plans.distinctBy {it.id}.forEach {tx->
                    changed+=setCategory(batch,tx,category,
                        tx.id==selected.id || scope!="SINGLE",
                        if(scope=="PRODUCT")selected.description else selected.counterparty,
                        "使用你保存的分类")
                }
            }
            if(scope in setOf("MERCHANT","FUTURE")) {
                val p=selected.platform.name
                val key=selected.counterparty.trim()
                val prev=ruleBefore("platform_category_rules",
                    "platform=? AND merchant=?",arrayOf(p,key))
                saveRuleChange(batch,"PLATFORM",p,key,prev,category)
                db.insertWithOnConflict("platform_category_rules",null,
                    ContentValues().apply {
                        put("platform",p);put("merchant",key)
                        put("category",category);put("updated_at",System.currentTimeMillis())
                    },SQLiteDatabase.CONFLICT_REPLACE)
            }
            if(scope=="CROSS") {
                val prev=ruleBefore("cross_platform_category_rules",
                    "merchant=?",arrayOf(canonical))
                saveRuleChange(batch,"CROSS","ALL",canonical,prev,category)
                db.insertWithOnConflict("cross_platform_category_rules",null,
                    ContentValues().apply {
                        put("merchant",canonical);put("category",category)
                        put("updated_at",System.currentTimeMillis())
                    },SQLiteDatabase.CONFLICT_REPLACE)
            }
            if(scope=="PRODUCT") {
                val p=selected.platform.name
                val key=canonical+"|"+product
                val prev=ruleBefore("product_category_rules",
                    "platform=? AND merchant=? AND product=?",
                    arrayOf(p,canonical,product))
                saveRuleChange(batch,"PRODUCT",p,key,prev,category)
                db.insertWithOnConflict("product_category_rules",null,
                    ContentValues().apply {
                        put("platform",p);put("merchant",canonical)
                        put("product",product);put("category",category)
                        put("updated_at",System.currentTimeMillis())
                    },SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            return CategoryChangeBatch(batch,changed,"人工分类")
        } finally {db.endTransaction()}
    }

    /** Apply only proposals shown in the preview; never modify manually edited rows. */
    fun applyAutomaticPreview(limit:Int=8000):CategoryChangeBatch {
        val preview=reviewPreview(limit)
        val candidates=preview.groups.flatMap {it.items}
            .filter {!it.manuallyEdited && it.proposedCategory!=null &&
                it.proposedCategory!=it.transaction.category &&
                !it.basis.startsWith("发现多个类别冲突")}
            .distinctBy {it.transaction.id}
        val r=rules()
        db.beginTransaction()
        try {
            val batch=newBatch("批量应用自动分类预览")
            var changed=0
            candidates.forEach { item ->
                val tx=item.transaction
                val current=db.query("transactions",arrayOf("category","nature_modified"),
                    "id=?",arrayOf(tx.id.toString()),null,null,null)
                    .use {c->if(c.moveToFirst())c.getString(0) to c.getInt(1) else null}
                if(current?.first==tx.category && current.second==0) {
                    val latest=evaluate(tx,false,r)
                    if(latest.proposedCategory==item.proposedCategory)
                        changed+=setCategory(batch,tx,item.proposedCategory!!,false,
                            item.matchedTerm,item.basis)
                }
            }
            db.setTransactionSuccessful()
            return CategoryChangeBatch(batch,changed,"自动分类")
        } finally {db.endTransaction()}
    }

    fun evidenceFor(items:List<Transaction>):Map<Long,String> {
        if(items.isEmpty())return emptyMap()
        val r=rules()
        val result=mutableMapOf<Long,String>()
        items.distinctBy {it.id}.chunked(350).forEach {chunk->
            val ids=chunk.map {it.id }
            db.rawQuery(
                """SELECT t.id,t.nature_modified,e.source,e.matched_term,
                          e.applied_category
                   FROM transactions t
                   LEFT JOIN classification_evidence e ON e.transaction_id=t.id
                   WHERE t.id IN (${ids.joinToString(","){"?"}})""",
                ids.map {it.toString()}.toTypedArray()
            ).use {c->
                val byId=chunk.associateBy {it.id}
                while(c.moveToNext()) {
                    val id=c.getLong(0)
                    val tx=byId[id]?:continue
                    val manual=c.getInt(1)!=0
                    val stored=if(!c.isNull(2) &&
                        !c.isNull(4) && c.getString(4)==tx.category)
                        c.getString(2) else null
                    val term=if(!c.isNull(3))c.getString(3) else ""
                    result[id]=when {
                        manual -> "你已手动确认：${tx.category}"
                        stored!=null ->stored+
                            if(term.isBlank())"" else " · 匹配到：$term"
                        else ->evaluate(tx,false,r).basis
                    }
                }
            }
        }
        return result
    }

    fun latestUndo():CategoryChangeBatch? =
        db.rawQuery(
            """SELECT b.id,b.label,
                      (SELECT COUNT(*) FROM category_change_items i
                       WHERE i.batch_id=b.id)+
                      (SELECT COUNT(*) FROM category_rule_changes r
                       WHERE r.batch_id=b.id)
               FROM category_change_batches b
               WHERE b.undone_at IS NULL
               ORDER BY b.id DESC LIMIT 1""",null
        ).use {c->if(c.moveToFirst())CategoryChangeBatch(
            c.getLong(0),c.getInt(2),c.getString(1))else null}

    /** Undo only records still unchanged since this batch was applied. */
    fun undoLatest():Int {
        val batch=latestUndo()?:return 0
        db.beginTransaction()
        try {
            var restored=0
            db.rawQuery(
                """SELECT transaction_id,before_category,before_modified,
                          after_category,after_modified
                   FROM category_change_items WHERE batch_id=?""",
                arrayOf(batch.id.toString())
            ).use {c->while(c.moveToNext()) {
                val id=c.getLong(0)
                val n=db.update("transactions",ContentValues().apply {
                    put("category",c.getString(1))
                    put("nature_modified",c.getInt(2))
                },"id=? AND category=? AND nature_modified=?",
                    arrayOf(id.toString(),c.getString(3),c.getInt(4).toString()))
                if(n==1) {
                    restored++
                    db.delete("classification_evidence",
                        "transaction_id=?",arrayOf(id.toString()))
                }
            }}
            db.rawQuery(
                """SELECT rule_type,platform,merchant,before_category,after_category
                   FROM category_rule_changes WHERE batch_id=?""",
                arrayOf(batch.id.toString())
            ).use {c->while(c.moveToNext()) {
                val type=c.getString(0)
                val p=c.getString(1)
                val key=c.getString(2)
                val before=if(c.isNull(3))null else c.getString(3)
                val after=c.getString(4)
                val table=when(type) {
                    "PLATFORM"->"platform_category_rules"
                    "CROSS"->"cross_platform_category_rules"
                    else->"product_category_rules"
                }
                val where=when(type) {
                    "PLATFORM"->"platform=? AND merchant=?"
                    "CROSS"->"merchant=?"
                    else->"platform=? AND merchant=? AND product=?"
                }
                val args=when(type) {
                    "PLATFORM"->arrayOf(p,key)
                    "CROSS"->arrayOf(key)
                    else->arrayOf(p,key.substringBeforeLast('|'),
                        key.substringAfterLast('|'))
                }
                if(ruleBefore(table,where,args)==after) {
                    if(before==null)db.delete(table,where,args)
                    else db.update(table,ContentValues().apply {
                        put("category",before);put("updated_at",System.currentTimeMillis())
                    },where,args)
                    restored++
                }
            }}
            db.update("category_change_batches",ContentValues().apply {
                put("undone_at",System.currentTimeMillis())
            },"id=?",arrayOf(batch.id.toString()))
            db.setTransactionSuccessful()
            return restored
        } finally {db.endTransaction()}
    }
}
