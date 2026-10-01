package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.util.toYuanText

@Composable
fun AnalysisScreen(state: BillUiState) {
    val max = state.categories.maxOfOrNull { it.amountCent }?.coerceAtLeast(1) ?: 1
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("消费分析", "按真实消费统计；转账、充值、提现和退款不会混进消费。") }
        items(state.categories) { item ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("${item.category} · ${item.count} 笔", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(item.amountCent.toYuanText(), fontWeight = FontWeight.Bold)
                }
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .padding(top = 6.dp)
                        .width((280f * item.amountCent.toFloat() / max.toFloat()).coerceAtLeast(4f).dp)
                        .height(7.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                )
            }
        }
        item {
            Text("高消费商户", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        }
        items(state.merchants.take(10)) { item ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp)) {
                Text("${item.merchant} · ${item.count} 笔", modifier = Modifier.weight(1f))
                Text(item.amountCent.toYuanText())
            }
        }
    }
}
