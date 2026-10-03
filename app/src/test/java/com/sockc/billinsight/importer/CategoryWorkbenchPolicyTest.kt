package com.sockc.billinsight.importer

import com.sockc.billinsight.model.*
import org.junit.Assert.*
import org.junit.Test

class CategoryWorkbenchPolicyTest {
    private fun expense(name:String,detail:String,category:String="其他")=Transaction(
        id=1,platform=Platform.WECHAT,occurredAt=1,
        counterparty=name,description=detail,directionText="支出",
        amountCent=1200,flowType=FlowType.EXPENSE,category=category,
        paymentMethod="零钱",transactionId="",merchantOrderId="",
        sourceFile="wechat.csv",fingerprint="sample"
    )
    private fun check(tx:Transaction,manual:Boolean=false,
        saved:Map<String,String> = emptyMap(),
        product:Map<Triple<Platform,String,String>,String> = emptyMap()
    )=CategoryWorkbenchPolicy.evaluate(tx,manual,saved,emptyMap(),
        emptyMap(),emptyMap(),product)

    @Test fun restaurantUsesMerchantAndExposesMatchingKeyword() {
        val evidence=MerchantLexicon.explain("隆江猪脚饭天河店","付款")
        assertEquals("餐饮",evidence?.category)
        assertTrue(evidence!!.matchedTerm.contains("猪脚饭"))
        assertTrue(evidence.source.startsWith("商户"))
    }
    @Test fun marketplaceAloneNeverMeansShopping() {
        assertNull(MerchantLexicon.suggest("京东商城","京东订单付款"))
        assertNull(MerchantLexicon.suggest("财付通支付科技有限公司","扫码支付"))
    }
    @Test fun marketplaceUsesActualProductEvidence() {
        assertEquals("数码",MerchantLexicon.suggest("京东商城","手机配件"))
        assertEquals("餐饮",MerchantLexicon.suggest("美团","猪脚饭"))
    }
    @Test fun exactProductRuleOverridesMarketplace() {
        val tx=expense("京东商城","手机配件")
        val key=Triple(Platform.WECHAT,
            MerchantCategoryPolicy.normalize(tx.counterparty),
            MerchantLexicon.normalize(tx.description))
        val result=check(tx,product=mapOf(key to "购物"))
        assertEquals("购物",result.proposedCategory)
        assertTrue(result.basis.contains("商品分类"))
    }
    @Test fun paymentPurposeCannotBeChangedByMerchantClassifier() {
        val payment=expense("京东商城","手机配件").copy(
            flowType=FlowType.CREDIT_REPAYMENT,category="信用卡还款")
        assertNull(check(payment).proposedCategory)
        assertFalse(check(payment).needsReview)
    }
    @Test fun manualMeaningfulCategoryRemainsProtected() {
        val result=check(expense("瑞幸咖啡","拿铁",category="餐饮"),true)
        assertTrue(result.manuallyEdited)
        assertTrue(result.basis.contains("已确认"))
    }
    @Test fun savedRuleExplainsWhyItWasUsed() {
        val tx=expense("隆江猪脚饭","付款")
        val result=check(tx,saved=mapOf(
            MerchantCategoryPolicy.normalize(tx.counterparty) to "餐饮"))
        assertEquals("餐饮",result.proposedCategory)
        assertTrue(result.basis.contains("你保存"))
    }
    @Test fun marketMerchantIdentityIsNotTrustedByPlatformLabel() {
        assertTrue(CategoryWorkbenchPolicy.isMixedMerchant("京东商城"))
        assertTrue(CategoryWorkbenchPolicy.isMixedMerchant("财付通支付科技有限公司"))
        assertFalse(CategoryWorkbenchPolicy.isMixedMerchant("隆江猪脚饭"))
    }
}
