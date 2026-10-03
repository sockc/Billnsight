package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toYuanText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DailyLedger {
    fun group(transactions: List<Transaction>, zone: ZoneId = ZoneId.systemDefault())
        : List<Pair<LocalDate,List<Transaction>>> =
        transactions.groupBy {
            Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
        }.toList().sortedByDescending { it.first }
}

@Composable
fun DailyLedgerHeader(day: LocalDate, transactions: List<Transaction>) {
    val received = transactions.filter { it.directionText.contains("收入") }.sumOf { it.amountCent }
    val paid = transactions.filter { it.directionText.contains("支出") }.sumOf { it.amountCent }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                day.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE",Locale.CHINA)),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Text("${transactions.size} 笔", style = MaterialTheme.typography.bodySmall)
        }
        val sums = buildList<String> {
            if (paid > 0L) add("付款流水 ${paid.toYuanText()}")
            if (received > 0L) add("收款流水 ${received.toYuanText()}")
        }
        if (sums.isNotEmpty()) {
            Text(
                sums.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}
