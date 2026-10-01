package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionClassifierTest {
    @Test
    fun transferIsNotExpense() {
        val result = TransactionClassifier.classify(
            direction = "支出",
            type = "转账",
            merchant = "张三",
            description = "转账",
            status = "支付成功",
            merchantRules = emptyMap(),
        )
        assertEquals(FlowType.TRANSFER, result.flowType)
    }

    @Test
    fun foodMerchantIsFood() {
        val result = TransactionClassifier.classify(
            direction = "支出",
            type = "商户消费",
            merchant = "瑞幸咖啡",
            description = "拿铁",
            status = "支付成功",
            merchantRules = emptyMap(),
        )
        assertEquals(FlowType.EXPENSE, result.flowType)
        assertEquals("餐饮", result.category)
    }

    @Test
    fun merchantRuleWins() {
        val result = TransactionClassifier.classify(
            direction = "支出",
            type = "商户消费",
            merchant = "XX便利店",
            description = "日用品",
            status = "支付成功",
            merchantRules = mapOf("XX便利店" to "购物"),
        )
        assertEquals("购物", result.category)
    }
}
