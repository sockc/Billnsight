package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toDisplayDateTime
import com.sockc.billinsight.util.toYuanText
import java.time.YearMonth

@Composable
fun PageTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        androidx.compose.material3.TextButton(onClick = onPrevious) { Text("‹ 上月") }
        Text("${month.year} 年 ${month.monthValue} 月", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        androidx.compose.material3.TextButton(onClick = onNext) { Text("下月 ›") }
    }
}

@Composable
fun TransactionCard(item: Transaction, trailing: @Composable (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.counterparty.ifBlank { item.description.ifBlank { "未知交易" } }, fontWeight = FontWeight.SemiBold)
                Text(
                    "${item.occurredAt.toDisplayDateTime()} · ${item.category} · ${item.platform.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.description.isNotBlank() && item.description != item.counterparty) {
                    Text(item.description, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(item.amountCent.toYuanText(), fontWeight = FontWeight.Bold)
                Text(item.flowType.name, style = MaterialTheme.typography.labelSmall)
                trailing?.invoke()
            }
        }
    }
}
