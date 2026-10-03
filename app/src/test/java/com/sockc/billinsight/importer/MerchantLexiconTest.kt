package com.sockc.billinsight.importer

import org.junit.Assert.*
import org.junit.Test

class MerchantLexiconTest {
    @Test fun screenshotMerchantsRecognizedOffline() {
        mapOf(
            "武汉热干面" to "餐饮",
            "隆江猪脚饭" to "餐饮",
            "杨记什锦小吃" to "餐饮",
            "农家味小湘厨社区厨房" to "餐饮",
            "拾贰便利店" to "商超日用",
            "新永佳百货" to "商超日用",
            "志兴生活超市" to "商超日用",
            "小桔充电" to "车辆",
            "广东联合电子服务股份有限公司" to "车辆",
            "中国联合网络通信有限公司" to "生活缴费"
        ).forEach { (merchant, expected) ->
            assertEquals(merchant, expected, MerchantLexicon.suggest(merchant))
        }
    }

    @Test fun prefixesAndCaseNormalize() {
        assertEquals("餐饮", MerchantLexicon.suggest("微信支付-武汉热干面"))
        assertEquals("饮品", MerchantLexicon.suggest("  支付宝：瑞幸咖啡 "))
        assertEquals("餐饮", MerchantLexicon.suggest("微信支付-肯德基"))
    }

    @Test fun genericQrAndPersonalNamesAreNotGuessed() {
        assertNull(MerchantLexicon.suggest("二维码付款", "扫码支付"))
        assertNull(MerchantLexicon.suggest("程耀军", "/"))
        assertNull(MerchantLexicon.suggest("快乐就好", "二维码收款"))
        assertNull(MerchantLexicon.suggest("Tam", "扫码付款"))
    }

    @Test fun descriptionHelpsWithoutInferringAllTopupsAreTransfers() {
        assertEquals("车辆", MerchantLexicon.suggest("广东联合电子", "高速通行费"))
        assertEquals("生活缴费", MerchantLexicon.suggest("未知公司", "话费充值"))
        assertNull(MerchantLexicon.suggest("某人", "充值"))
    }

    @Test fun secondScreenshotAndNaturalShopSuffixes() {
        mapOf(
            "缤纷生鲜汇" to "商超日用",
            "街坊生鲜汇（石井店）" to "商超日用",
            "粤吃越湘" to "餐饮",
            "粤吃越湘·广州园岗店" to "餐饮",
            "叹茶靓点（庆丰店）" to "餐饮",
            "巷口早茶点心" to "餐饮",
            "湘味家常小炒" to "餐饮",
            "街坊生鲜便利店" to "商超日用",
            "城市鲜果汇" to "水果",
            "某某家居五金" to "购物",
            "某某茶饮铺" to "饮品",
            "街坊肉菜市场" to "买菜"
        ).forEach { (merchant, expected) ->
            assertEquals(merchant, expected, MerchantLexicon.suggest(merchant))
        }
        assertEquals("购物",MerchantLexicon.suggest(
            "长壮商贸有限公司","家具三合一连接件柜体安装配件"
        ))
    }

    @Test fun genericOrAmbiguousNamesAreNotGuessed() {
        for(name in listOf("程耀军","快乐就好","广州市白云区石井",
            "湘江银行","茶壶设计工作室","美团收银")) {
            assertNull(name, MerchantLexicon.suggest(name, "二维码收款"))
        }
        assertNull(MerchantLexicon.suggest("未知商户", "充值"))
        assertNull(MerchantLexicon.suggest("扫码付款", "付款给朋友"))
    }

    @Test fun dictionaryIsNotJustAHandfulOfHardcodedScreenshots() {
        assertTrue(MerchantLexicon.termCount >= 700)
    }
}
