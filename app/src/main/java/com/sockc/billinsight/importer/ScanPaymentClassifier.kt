package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Transaction

/** A QR-paid purchase may have an individual, rather than a registered business, as payee. */
object ScanPaymentClassifier {
    private val qrPayment = listOf(
        "二维码付款", "扫码付款", "扫一扫付款", "付款码付款", "扫码支付",
        "二维码支付", "扫二维码", "扫码转账", "扫码消费", "个人收款码", "商家收款码",
        "收款码付款", "收款码支付", "面对面付款", "收钱码付款",
        // Payee-side QR receipt descriptions in a payer's outgoing statement:
        "二维码收款",
    )
    private val generic = setOf(
        "", "/", "-", "--", "未知", "未知商户", "未知收款方",
        "微信支付", "支付宝", "微信", "商户消费", "收款方",
        "二维码付款", "扫码付款", "扫码支付", "收钱码", "个人收款码",
        "二维码收款", "二维码", "收款码", "付款码",
    )
    fun isQrPayment(direction: String, tradeType: String, description: String): Boolean {
        if (!direction.contains("支出")) return false
        val source = "$tradeType $description"
        return qrPayment.any(source::contains)
    }
    fun isQrExpense(tx: Transaction): Boolean =
        tx.flowType == FlowType.EXPENSE &&
            isQrPayment(tx.directionText, tx.tradeType, tx.description)

    fun needsMerchantReview(tx: Transaction, override: String? = null): Boolean =
        isQrExpense(tx) && override.isNullOrBlank() && isGenericCounterparty(tx.counterparty)

    fun isGenericCounterparty(counterparty: String): Boolean {
        val name = counterparty.trim().replace(Regex("""[\s：:（）()]+"""), "")
        return name.lowercase() in generic || name.startsWith("收款方备注") ||
            name.startsWith("转账备注") || name.startsWith("微信用户") ||
            name.startsWith("支付宝用户")
    }
}