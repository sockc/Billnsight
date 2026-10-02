package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction

@Composable
fun TransactionsScreen(
    transactions: List<Transaction>,
    pendingTransactions: List<Transaction>,
    pendingCount: Int,
    reviewMode: Boolean,
    onReviewModeChange: (Boolean) -> Unit,
    platformFilter: Platform?,
    onPlatformChange: (Platform?) -> Unit,
    onNatureChange: (Transaction, FlowType, String) -> Unit,
) {
    var editing by remember { mutableStateOf<Transaction?>(null) }
    editing?.let { tx ->
        TransactionNatureDialog(
            transaction = tx,
            onDismiss = { editing = null },
            onConfirm = { item, nature, category ->
                onNatureChange(item, nature, category)
                editing = null
            },
        )
    }

    val shown = if (reviewMode) pendingTransactions else transactions
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("流水", "个人转账和用途不明的扫码交易，确认后才参与相应统计。") }
        item { SourceFilterRow(platformFilter, onPlatformChange) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !reviewMode,
                    onClick = { onReviewModeChange(false) },
                    label = { Text("全部流水") },
                )
                FilterChip(
                    selected = reviewMode,
                    onClick = { onReviewModeChange(true) },
                    label = { Text("待确认 $pendingCount") },
                )
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (reviewMode) "暂无待确认交易" else "当前筛选条件下暂无流水",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(shown, key = { it.id }) { tx ->
            TransactionCard(tx) {
                TextButton(onClick = { editing = tx }) {
                    Text(if (tx.flowType == FlowType.PENDING) "确认用途" else "修改性质")
                }
            }
        }
        if (reviewMode && pendingCount > pendingTransactions.size) {
            item {
                Text(
                    "仅显示最近 ${pendingTransactions.size} 笔；确认后自动加载后续记录。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
