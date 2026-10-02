package com.sockc.billinsight.data

import com.sockc.billinsight.model.FlowType

data class ReviewCandidate(val id: Long, val direction: String, val flowType: FlowType)

object BulkReviewPolicy {
    private val incoming = setOf(
        FlowType.INCOME, FlowType.GIFT_INCOME, FlowType.LOAN_RECOVERY, FlowType.LOAN_DISBURSEMENT,
        FlowType.BUSINESS_INCOME, FlowType.REFUND, FlowType.TRANSFER, FlowType.IGNORE,
    )
    private val outgoing = setOf(
        FlowType.EXPENSE, FlowType.GIFT_EXPENSE, FlowType.LOAN_OUT,
        FlowType.BUSINESS_EXPENSE, FlowType.CREDIT_REPAYMENT, FlowType.LOAN_REPAYMENT,
        FlowType.TRANSFER, FlowType.IGNORE,
    )

    fun validate(rows: List<ReviewCandidate>, target: FlowType) {
        require(rows.size in 1..100) { "每次请选择 1～100 笔待确认交易" }
        require(rows.distinctBy { it.id }.size == rows.size && rows.all { it.id > 0 }) {
            "存在重复或无效流水"
        }
        require(rows.all { it.flowType == FlowType.PENDING }) { "仅可批量处理待确认交易" }
        val directions = rows.map {
            when {
                it.direction.contains("收入") -> "IN"
                it.direction.contains("支出") -> "OUT"
                else -> "UNKNOWN"
            }
        }.toSet()
        require(directions.size == 1 && directions.single() != "UNKNOWN") {
            "请只选择同一收支方向的记录，未知方向请逐笔确认"
        }
        require(target in if (directions.single() == "IN") incoming else outgoing) {
            "该交易性质与选中流水的收支方向不一致"
        }
    }
}
