package com.sockc.billinsight.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.floor
import kotlin.math.roundToLong

private val inputFormats = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
    DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-M-d H:mm"),
)

fun parseDateTime(raw: String): Long =
    parseDateTimeOrNull(raw) ?: System.currentTimeMillis()

/** Strict parsing for importing financial records; invalid dates must not become today. */
fun parseDateTimeOrNull(raw: String): Long? {
    val text = raw.trim()
    for (formatter in inputFormats) {
        try {
            val dt = LocalDateTime.parse(text, formatter)
            return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
    }

    val serial = text.toDoubleOrNull()
    if (serial != null && serial in 1.0..100000.0) {
        return excelSerialToLocalDateTime(serial, false)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    return null
}

fun excelSerialToDateTimeText(serial: Double, date1904: Boolean): String =
    excelSerialToLocalDateTime(serial, date1904)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

fun excelSerialToLocalDateTime(serial: Double, date1904: Boolean): LocalDateTime {
    require(serial.isFinite() && serial >= 0.0) { "无效的 Excel 日期序列值" }

    val wholeDays = floor(serial).toLong()
    val seconds = ((serial - wholeDays) * 86_400.0).roundToLong()

    val baseDate: LocalDate
    val adjustedDays: Long
    if (date1904) {
        baseDate = LocalDate.of(1904, 1, 1)
        adjustedDays = wholeDays
    } else {
        baseDate = LocalDate.of(1899, 12, 31)
        adjustedDays = wholeDays - if (wholeDays >= 60) 1 else 0
    }

    return baseDate.atStartOfDay()
        .plusDays(adjustedDays)
        .plusSeconds(seconds)
}

fun Long.toDisplayDateTime(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

fun currentYearMonth(): YearMonth = YearMonth.now()
