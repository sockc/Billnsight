package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.model.CategoryReviewItem

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
        if(tx.flowType!=FlowType.EXPENSE) return CategoryReviewItem(
            tx,null,"","非个人消费",false,manuallyEdited
        )
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
        val term=if(saved!=null) tx.counterparty else hit?.matchedTerm.orEmpty()
        val basis=when {
            manuallyEdited -> "使用你已确认的消费分类"
            product!=null -> "使用你保存的商品分类"
            saved!=null -> "使用你保存的商户分类"
            hit?.conflict==true -> "发现多个类别冲突，需核对：" + term
            hit!=null -> "匹配到：" + term + " · " + hit.source
            mixed -> "综合平台或支付服务商，缺少明确商品描述"
            else -> "尚未找到可靠的分类依据"
        }
        return CategoryReviewItem(
            tx,proposal,term,basis,
            tx.category=="其他" || (!manuallyEdited &&
                (hit?.conflict==true ||
                    (proposal!=null && proposal!=tx.category))),
            manuallyEdited
        )
    }
}
