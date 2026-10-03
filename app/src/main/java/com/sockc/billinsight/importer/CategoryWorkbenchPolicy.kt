package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.model.CategoryReviewItem
import com.sockc.billinsight.model.displayName

/**
 * Only proposes new CONSUMPTION categories after the recorded transaction
 * purpose is known. An incoming receipt or financial transfer is never
 * reclassified because a merchant or product term happens to match it.
 */
object CategoryWorkbenchPolicy {
    fun isMixedMerchant(name: String) =
        MerchantLexicon.requiresProductEvidence(name) ||
            ScanPaymentClassifier.isGenericCounterparty(name)

    fun evaluate(
        tx: Transaction,
        manuallyEdited: Boolean,
        merchantRules: Map<String,String>,
        aliases: Map<String,String>,
        platformRules: Map<Pair<Platform,String>,String>,
        crossPlatformRules: Map<String,String> = emptyMap(),
        productRules: Map<Triple<Platform,String,String>,String> = emptyMap(),
    ): CategoryReviewItem {
        if(tx.flowType==FlowType.PENDING) return CategoryReviewItem(
            tx,null,"","交易用途尚未确认，请先在原始流水确认收入、消费、还款或转账",
            true,manuallyEdited,"交易用途待确认"
        )
        if(tx.flowType!=FlowType.EXPENSE) return CategoryReviewItem(
            tx,null,"","非个人消费",false,manuallyEdited
        )
        val purposeHint=(tx.tradeType+" "+tx.description+" "+
            tx.counterparty).lowercase()
        val signals=listOf("还款","还信用卡","贷款扣款","退款","退回",
            "提现","余额宝","零钱通","账户互转","资金划转")
        if(signals.any {purposeHint.contains(it)}) {
            val newly=TransactionClassifier.classify(
                tx.directionText,tx.tradeType,tx.counterparty,
                tx.description,"",emptyMap(),tx.paymentMethod
            )
            if(newly.flowType in setOf(
                FlowType.CREDIT_REPAYMENT,FlowType.LOAN_REPAYMENT,
                FlowType.REFUND,FlowType.TRANSFER
            )) return CategoryReviewItem(
                tx,null,"","疑似"+newly.flowType.displayName()+
                    "，请先核对交易用途，不自动修改消费分类",
                true,manuallyEdited,newly.flowType.displayName()
            )
        }
        val raw=MerchantCategoryPolicy.normalize(tx.counterparty)
        val canonical=aliases[raw]?.let(MerchantCategoryPolicy::normalize) ?: raw
        val mixed=isMixedMerchant(tx.counterparty)
        val platformRule=if(mixed)null else
            platformRules[tx.platform to canonical] ?: platformRules[tx.platform to raw]
        val cross=if(mixed || tx.platform !in setOf(Platform.WECHAT,Platform.ALIPAY))
            null else crossPlatformRules[canonical]
        val generic=if(mixed)null else merchantRules[canonical] ?: merchantRules[raw]
        val product=productRules[Triple(tx.platform,canonical,
            MerchantLexicon.normalize(tx.description))]
        val saved=(product ?: platformRule ?: cross ?: generic)
            ?.takeUnless {it=="其他"}
        val hit=MerchantLexicon.explain(tx.counterparty,tx.description)
        val proposal=saved ?: hit?.category?.takeUnless {it=="其他"}
        val term=hit?.matchedTerm?.takeIf {it.isNotBlank()} ?:
            if(saved!=null) tx.counterparty else ""
        val basis=when {
            manuallyEdited -> "使用你已确认的消费分类"
            product!=null -> "使用你保存的商品分类 · 匹配到："+term
            saved!=null -> "使用你保存的商户分类 · 匹配到："+term
            hit?.conflict==true -> "发现多个类别冲突，需核对：" + term
            hit!=null -> "匹配到：" + term + " · " + hit.source
            mixed -> "综合平台或支付服务商，缺少明确商品描述"
            else -> "尚未找到可靠的分类依据"
        }
        return CategoryReviewItem(
            tx,proposal,term,basis,
            !manuallyEdited && (tx.category=="其他" ||
                hit?.conflict==true ||
                (proposal!=null && proposal!=tx.category)),
            manuallyEdited
        )
    }
}
