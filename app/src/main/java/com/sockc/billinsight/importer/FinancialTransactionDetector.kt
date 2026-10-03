package com.sockc.billinsight.importer

import com.sockc.billinsight.model.FlowType

/** Strong financial evidence wins over generic 不计收支/IGNORE, never over refunds. */
object FinancialTransactionDetector {
    private val repayWords=listOf("自动还款","账单还款","主动还款","还款",
        "偿还","还清","自动代扣","账单扣款","还本付息")
    private val invalid=listOf("退款","退回","撤销","交易关闭","已关闭",
        "失败","未支付","未付款")

    fun detect(direction:String,tradeType:String,counterparty:String,
               description:String,status:String="",paymentMethod:String=""):Classification? {
        val details="$tradeType $counterparty $description"
        if(invalid.any{("$details $status").contains(it,ignoreCase=true)})return null
        if(!direction.contains("收入") && repayWords.any{details.contains(it)}) {
            if(listOf("花呗","信用购","信用付").any{details.contains(it)})
                return Classification(FlowType.CREDIT_REPAYMENT,"花呗/信用购还款")
            if(listOf("借呗","网商贷","微粒贷","房贷","车贷","贷款")
                    .any{details.contains(it)} &&
                !details.contains("信用卡") && !details.contains("贷记卡"))
                return Classification(FlowType.LOAN_REPAYMENT,"贷款还款")
        }
        val wallet= listOf("余额宝","余利宝","零钱通").firstOrNull{details.contains(it)}
        val moved=listOf("转入","转出","划转","互转","转至","转到","转存","转账至")
            .any{details.contains(it)}
        if(wallet!=null && moved &&
            listOf("购买","消费","收益","利息","手续费").none{details.contains(it)}) {
            return Classification(FlowType.TRANSFER,
                when(wallet){"余利宝"->"余利宝资金流转";"余额宝"->"余额宝资金流转";
                    else->"零钱通资金流转"})
        }
        if(CreditRepaymentDetector.isRepayment(direction,tradeType,counterparty,
                description,paymentMethod,status))
            return Classification(FlowType.CREDIT_REPAYMENT,"信用卡还款")
        return null
    }

    fun billingMonth(text:String):String? {
        val match=Regex("""(20\d{2})年(0?[1-9]|1[0-2])月账单""").find(text)?:return null
        return match.groupValues[1]+"年"+match.groupValues[2].padStart(2,'0')+"月账单"
    }
}
