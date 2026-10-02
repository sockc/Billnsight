package com.sockc.billinsight.model

import java.util.Locale

/**
 * Recognize an installment-plan ID only when the bill explicitly labels it as a
 * plan/contract/original order. Independent payment transaction IDs are not plan IDs.
 */
object FinanceReference {
    private val labelled = Regex(
        """(?:分期(?:计划|订单|协议)?(?:编号|单号)|借据编号|借款编号|合同编号|原订单号|原交易订单号|installment[ -]?id|plan[ -]?id|contract[ -]?id)\s*[：:=#]?\s*([A-Za-z0-9][A-Za-z0-9-]{7,47})""",
        RegexOption.IGNORE_CASE
    )
    private val installments = Regex(
        """(?:分期|月供|账单分摊|还款|白条|花呗|月付|借呗|第\s*\d+\s*期|installment)""",
        RegexOption.IGNORE_CASE
    )
    private val numberedPeriod = Regex(
        """(?:第\s*(\d{1,3})\s*(?:/|／|共|期[、，/]?)\s*(\d{1,3})\s*期?|\((\d{1,3})/(\d{1,3})\))"""
    )
    fun normalizeReference(value: String?): String? {
        val v=value?.trim()?.uppercase(Locale.ROOT) ?: return null
        if (v.length !in 8..48 || !v.matches(Regex("[A-Z0-9][A-Z0-9-]*")) ||
            v.all { it == '*' || it == 'X' || it == '-' } ||
            v.matches(Regex("0+")) || v.contains("****")
        ) return null
        return v
    }
    fun explicitReference(tx: Transaction): String? =
        labelled.find(tx.tradeType+" "+tx.description)?.groupValues?.getOrNull(1)
            ?.let(::normalizeReference)

    fun isInstallmentEvidence(tx: Transaction): Boolean =
        installments.containsMatchIn(
            tx.tradeType+" "+tx.description+" "+tx.counterparty
        ) && tx.directionText.contains("支出")

    fun totalTerms(tx: Transaction): Int? {
        val m=numberedPeriod.find(tx.description+" "+tx.tradeType) ?: return null
        val groups=m.groupValues
        return (groups[2].takeIf {it.isNotBlank()} ?: groups[4])
            .toIntOrNull()?.takeIf {it in 2..360}
    }

    fun canAutoLink(
        planPlatform: Platform,
        reference: String?,
        candidate: Transaction,
    ): Boolean {
        val trusted=normalizeReference(reference) ?: return false
        if(planPlatform!=candidate.platform || !isInstallmentEvidence(candidate))return false
        if(candidate.flowType !in setOf(
                FlowType.EXPENSE,FlowType.CREDIT_REPAYMENT,FlowType.LOAN_REPAYMENT
            ))return false
        return explicitReference(candidate)==trusted
    }

    /** Weak matches must remain review-only. */
    fun candidateReason(
        seed: Transaction, candidate: Transaction, reference: String?
    ): String? {
        if(candidate.id==seed.id || candidate.platform!=seed.platform)return null
        if(candidate.flowType !in setOf(
                FlowType.EXPENSE,FlowType.CREDIT_REPAYMENT,FlowType.LOAN_REPAYMENT
            ))return null
        if(canAutoLink(seed.platform,reference,candidate))return "同一明确分期计划编号"
        if(!isInstallmentEvidence(candidate))return null
        if(seed.merchantOrderId.isNotBlank() &&
            seed.merchantOrderId==candidate.merchantOrderId &&
            seed.merchantOrderId.length>=10)return "商户订单号相同，需确认是否同一期"
        val seedName=seed.counterparty.trim().lowercase(Locale.ROOT)
        val otherName=candidate.counterparty.trim().lowercase(Locale.ROOT)
        if(seedName.isNotBlank() && seedName==otherName)return "同一平台及收款方，需人工核对"
        return null
    }
}
