package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction

/** Merchant rules are scoped by exact payee, platform and payment direction.
 * Generic QR placeholders can identify unrelated people, so never use them as rules. */
data class MerchantNatureRule(
    val platform:Platform,
    val counterparty:String,
    val direction:String,
    val flowType:FlowType,
    val category:String,
)

object MerchantNaturePolicy {
    fun direction(value:String):String?=when {
        value.contains("收入")->"IN"
        value.contains("支出")->"OUT"
        else->null
    }

    fun eligibleMerchant(tx:Transaction):Boolean =
        tx.counterparty.trim().isNotEmpty() &&
            !ScanPaymentClassifier.isGenericCounterparty(tx.counterparty) &&
            tx.platform!=Platform.UNKNOWN && direction(tx.directionText)!=null &&
            tx.sourceFile!="手动记账"

    /** Ordinary, unlinked transactions only. Explicit refunds and repayments keep
     * their own accounting meaning even when the payee is a known merchant. */
    fun eligibleOldFlow(tx:Transaction):Boolean=tx.flowType in setOf(
        FlowType.EXPENSE,FlowType.INCOME,FlowType.PENDING
    )

    fun allowsDirection(direction:String,flow:FlowType):Boolean=when(direction) {
        "IN"->flow in setOf(FlowType.INCOME,FlowType.BUSINESS_INCOME,
            FlowType.GIFT_INCOME,FlowType.LOAN_RECOVERY)
        "OUT"->flow in setOf(FlowType.EXPENSE,FlowType.BUSINESS_EXPENSE,
            FlowType.GIFT_EXPENSE,FlowType.LOAN_OUT,FlowType.CREDIT_REPAYMENT,
            FlowType.LOAN_REPAYMENT)
        else->false
    }

    fun apply(tx:Transaction,rule:MerchantNatureRule?):Transaction {
        if(rule==null || !eligibleMerchant(tx) || !eligibleOldFlow(tx) ||
            tx.platform!=rule.platform || tx.counterparty.trim()!=rule.counterparty ||
            direction(tx.directionText)!=rule.direction ||
            !allowsDirection(rule.direction,rule.flowType)
        ) return tx
        return tx.copy(flowType=rule.flowType,category=rule.category)
    }
}
