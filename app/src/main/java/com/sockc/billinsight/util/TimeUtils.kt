package com.sockc.billinsight.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val inputFormats = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
)

fun parseDateTime(raw: String): Long {
    val text = raw.trim()
    for (formatter in inputFormats) {
        try {
            val dt = LocalDateTime.parse(text, formatter)
            return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
    }
    return System.currentTimeMillis()
}

fun Long.toDisplayDateTime(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

fun currentYearMonth(): YearMonth = YearMonth.now()
