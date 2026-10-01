package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun DiscoverScreen(state: BillUiState) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("钱去哪了", "专门找小额高频、大额支出和高频商户。") }
        item {
            InsightCard(
                title = "小额高频",
                main = "${state.summary.smallExpenseCount} 笔 · ${state.summary.smallExpenseCent.toYuanText()}",
                detail = "统计单笔低于 ¥50 的真实消费。",
            )
        }
        item {
            InsightCard(
                title = "转账与资金流转",
                main = state.summary.transferCent.toYuanText(),
                detail = "这部分不算消费，避免银行卡→微信→付款被重复计算。",
            )
        }
        item { Text("本月最大支出", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp)) }
        items(state.largestExpenses) { TransactionCard(it) }
        item { Text("高频 / 高额商户", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp)) }
        items(state.merchants.take(10)) { item ->
            InsightCard(item.merchant, item.amountCent.toYuanText(), "${item.count} 笔消费")
        }
    }
}

@Composable
private fun InsightCard(title: String, main: String, detail: String) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(main, style = MaterialTheme.typography.headlineSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
