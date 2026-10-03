package com.sockc.billinsight.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

private val moneyFormat = DecimalFormat("#,##0.00")

fun Long.toYuanText(): String = "¥${moneyFormat.format(this / 100.0)}"

fun parseAmountToCent(raw: String): Long {
    val cleaned = raw
        .replace("¥", "")
        .replace("￥", "")
        .replace(",", "")
        .replace("元", "")
        .replace("−", "-")
        .replace("－", "-")
        .replace("﹣", "-")
        .replace(" ", "")
        .replace("\u00a0", "")
        .trim().let { value ->
            if(value.length>2 && value.startsWith("(") && value.endsWith(")"))
                "-"+value.substring(1,value.length-1)
            else value
        }
    if (cleaned.isBlank()) return 0L
    return runCatching {
        BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
    }.getOrDefault(0L)
}
