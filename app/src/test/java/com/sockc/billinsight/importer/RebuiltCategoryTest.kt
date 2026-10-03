package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import org.junit.Assert.*
import org.junit.Test

class RebuiltCategoryTest {
    @Test fun screenshotMerchantKeywordsMatchOffline() {
        mapOf(
            "拾贰便利店" to "商超日用",
            "新永佳百货" to "商超日用",
            "等你淘便利店" to "商超日用",
            "武汉热干面" to "餐饮",
            "隆江猪脚饭" to "餐饮",
            "小桔充电" to "车辆",
            "中国联合网络通信有限公司" to "生活缴费"
        ).forEach { (name,category) ->
            assertEquals(name,category,MerchantLexicon.suggest(name))
        }
        assertNull(MerchantLexicon.suggest("谭秀华","二维码付款"))
    }

    @Test fun reviewedOtherRequiresExplicitUserChoice() {
        fun eligible(category: String="其他", edited:Boolean=false,source:String="微信账单.csv",
                     include:Boolean=false,flow:FlowType=FlowType.EXPENSE) =
            OtherReclassificationPolicy.eligible(flow,category,source,edited,include)
        assertTrue(eligible())
        assertFalse(eligible(edited=true))
        assertTrue(eligible(edited=true,include=true))
        assertFalse(eligible(category="餐饮",edited=true,include=true))
        assertFalse(eligible(flow=FlowType.CREDIT_REPAYMENT,include=true))
        assertFalse(eligible(flow=FlowType.TRANSFER,include=true))
        assertFalse(eligible(source="手动记账",include=true))
    }
}
