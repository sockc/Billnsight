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
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.util.toYuanText
import kotlin.math.abs

@Composable
fun HomeScreen(
    state: BillUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onImport: () -> Unit,
    onPlatformChange: (Platform?) -> Unit,
) {
    val current = state.summary.expenseCent
    val previous = state.previousSummary.expenseCent
    val delta = current - previous
    val percent = if (previous > 0) delta * 100.0 / previous else null

    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("账单洞察", "不要求天天记账，导入账单后直接看钱去了哪里。") }
        item { MonthHeader(state.month, onPrevious, onNext) }
        item { SourceFilterRow(state.platformFilter, onPlatformChange) }
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("${platformLabel(state.platformFilter)} · 本月消费", style = MaterialTheme.typography.labelLarge)
                    Text(
                        current.toYuanText(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    val compareText = when {
                        previous == 0L && current == 0L -> "本月和上月都暂无消费"
                        previous == 0L -> "上月暂无可比较消费"
                        delta > 0 -> "比上月多 ${abs(delta).toYuanText()}（+${"%.1f".format(percent)}%）"
                        delta < 0 -> "比上月少 ${abs(delta).toYuanText()}（${"%.1f".format(percent)}%）"
                        else -> "与上月持平"
                    }
                    Text(
                        compareText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("导入提示", fontWeight = FontWeight.Bold)
                    Text("微信：直接选择官方 XLSX 账单", style = MaterialTheme.typography.bodySmall)
                    Text("支付宝：选择 CSV / XLSX；压缩包也可直接导入", style = MaterialTheme.typography.bodySmall)
                    Text("重复导入会自动去重。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("小额高频", fontWeight = FontWeight.Bold)
                    Text(
                        "低于 ¥${state.smallThresholdYuan} 的消费 ${state.summary.smallExpenseCount} 笔，共 ${state.summary.smallExpenseCent.toYuanText()}"
                    )
                    Text(
                        "很多“不知道钱花去哪了”通常藏在这里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Text(
                "钱主要花在哪",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        if (state.categories.isEmpty()) {
            item {
                Text(
                    "还没有当前筛选条件下的消费。",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.categories.take(7)) { item ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryBadge(item.category)
                        Text("${item.count} 笔", style = MaterialTheme.typography.bodySmall)
                    }
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
