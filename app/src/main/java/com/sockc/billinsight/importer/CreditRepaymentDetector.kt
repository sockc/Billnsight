package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType

/**
 * Recognizes a bill *repayment* rather than a payment made WITH a credit card.
 * The source may describe the destination in merchant/counterparty instead of trade type.
 */
object CreditRepaymentDetector {
    private val card = Regex("信用卡|贷记卡")
    private val payToCard = Regex("(?:还|偿还|归还|还款|扣款|代扣|转入|转账至|转账到|支付至|支付到).{0,10}(?:信用卡|贷记卡)")
    private val cardThenPay = Regex("(?:信用卡|贷记卡).{0,14}(?:还款|自动扣款|自动代扣|账单扣款|还清|本期账单支付)")
    private val clearCardRepayment = Regex("信用卡还款|贷记卡还款|还信用卡|还贷记卡|偿还信用卡|信用卡代还")
    private val repayment = Regex("还款|偿还|归还|代扣|扣款|账单支付|还清")
    private val returns = listOf("退款", "退回", "撤销", "失败", "关闭", "未支付", "未付款")
    private val loanOnly = listOf("房贷", "车贷", "住房贷款", "借呗", "微粒贷", "网商贷", "借款", "贷款")

    fun isRepayment(
        direction: String,
        tradeType: String,
        counterparty: String,
        description: String,
        paymentMethod: String = "",
        status: String = "",
    ): Boolean {
        if (direction.contains("收入")) return false
        val kind = normalize("$tradeType $description")
        val party = normalize(counterparty)
        val payment = normalize(paymentMethod)
        val currentState = normalize(status)
        // A refund or failed transaction must not be counted as a repayment.
        if (returns.any { kind.contains(it) || currentState.contains(it) }) return false
        // The source must identify a CREDIT CARD, not merely a bank/loan.
        val evidence = clearCardRepayment.containsMatchIn(kind) ||
            payToCard.containsMatchIn(kind) ||
            cardThenPay.containsMatchIn(kind) ||
            clearCardRepayment.containsMatchIn(party) ||
            payToCard.containsMatchIn(party) ||
            cardThenPay.containsMatchIn(party) ||
            (card.containsMatchIn(party) && repayment.containsMatchIn(kind)) ||
            (card.containsMatchIn(kind) && repayment.containsMatchIn(party)) ||
            (card.containsMatchIn(kind + " " + party) &&
                (payment.contains("信用卡还款") || payment.contains("贷记卡还款")))
        if (!evidence) return false
        // Do not turn a generic instalment-loan payment into a credit-card bill.
        val cardClearlyNamed = card.containsMatchIn(kind) || card.containsMatchIn(party)
        if (!cardClearlyNamed && loanOnly.any { kind.contains(it) }) return false
        return true
    }

    fun isSafelyAutoCorrectable(
        flowType: FlowType,
        category: String,
        hasLoanSplit: Boolean,
        hasExpenseLink: Boolean,
    ): Boolean {
        if (hasExpenseLink) return false
        return when (flowType) {
            FlowType.TRANSFER -> category == "资金流转"
            FlowType.EXPENSE -> category == "其他"
            FlowType.PENDING -> category == "待确认"
            FlowType.LOAN_REPAYMENT -> category == "贷款还款" && !hasLoanSplit
            // IGNORE may mean failed payment; never rewrite it without source status.
            else -> false
        }
    }

    private fun normalize(text: String): String = text.lowercase()
        .replace(Regex("""[\s　·•：:（）()【】\[\]_—–-]+"""), "")
}
