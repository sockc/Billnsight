package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.util.toYuanText

@Composable
fun HomeScreen(state: BillUiState, onPrevious: () -> Unit, onNext: () -> Unit, onImport: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("账单洞察", "不要求你天天记账，把账单给我，我告诉你钱去哪了。") }
        item { MonthHeader(state.month, onPrevious, onNext) }
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("本月消费支出", style = MaterialTheme.typography.labelLarge)
                    Text(state.summary.expenseCent.toYuanText(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryMini("收入", state.summary.incomeCent.toYuanText())
                        SummaryMini("退款", state.summary.refundCent.toYuanText())
                        SummaryMini("流水", "${state.summary.transactionCount} 笔")
                    }
                }
            }
        }
        item {
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("导入微信 / 支付宝账单")
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("小额高频", fontWeight = FontWeight.Bold)
                    Text("低于 ¥50 的消费 ${state.summary.smallExpenseCount} 笔，共 ${state.summary.smallExpenseCent.toYuanText()}")
                    Text("很多“钱不知道去哪了”，通常就藏在这里。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("钱主要花在哪", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        if (state.categories.isEmpty()) {
            item { Text("还没有账单，先导入一份 CSV/ZIP。", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.categories.take(6)) { item ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.category} · ${item.count} 笔")
                    Text(item.amountCent.toYuanText(), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SummaryMini(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
