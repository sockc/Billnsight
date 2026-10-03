package com.sockc.billinsight.importer

import com.sockc.billinsight.util.parseAmountToCent

/** Signed export amounts are normalized only with an explicit income/outflow direction.
 * Unreadable, zero and ambiguous negative amounts remain signed/zero and PENDING.
 * Original amount text is preserved, and no source file is changed or dropped.
 */
object ImportAmountPolicy {
    data class Resolution(
        val cents: Long,
        val needsReview: Boolean,
        val auditNote: String = "",
    )

    fun resolve(rawAmount: String, direction: String): Resolution {
        val original = parseAmountToCent(rawAmount)
        val raw = rawAmount.trim().replace('\n',' ').replace('\r',' ')
            .take(64).ifBlank { "空白" }
        val clearDirection = direction.contains("支出") || direction.contains("收入")
        if (original < 0 && original != Long.MIN_VALUE && clearDirection) {
            return Resolution(-original,false,
                " [导入原始金额：$raw；已按收支方向归一化]")
        }
        if (original <= 0L) {
            return Resolution(original,true,
                " [导入原始金额：$raw；金额待核对]")
        }
        return Resolution(original,false)
    }
}
