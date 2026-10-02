package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.sockc.billinsight.model.LoanRepaymentDetail

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
    loanDetails: Map<Long,LoanRepaymentDetail>,
    onSaveLoan: (Long,Long,Long,Long) -> Unit,
    onClearLoan: (Long) -> Unit,
    onBulkConfirm: (List<Long>, FlowType, String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchFlowFilter: String,
    onSearchFlowChange: (String) -> Unit,
    searchLimit: Int,
    onLoadMore: () -> Unit,
) {
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var editingLoan by remember { mutableStateOf<Transaction?>(null) }
    editingLoan?.let { tx ->
        LoanSplitDialog(transaction=tx,existing=loanDetails[tx.id],
            onDismiss={editingLoan=null},onSave=onSaveLoan,onClear=onClearLoan)
    }
    var selectedIds by remember(reviewMode, platformFilter) {
        mutableStateOf<Set<Long>>(emptySet())
    }
    var bulkDialog by remember { mutableStateOf(false) }
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
    val pendingIds = pendingTransactions.map { it.id }.toSet()
    val checked = selectedIds.intersect(pendingIds)
    if (bulkDialog && checked.isNotEmpty()) {
        BulkReviewDialog(
            selected = pendingTransactions.filter { it.id in checked },
            onDismiss = { bulkDialog = false },
            onConfirm = { ids, type, category ->
                onBulkConfirm(ids,type,category)
                selectedIds = emptySet()
                bulkDialog = false
            },
        )
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("流水", "按日期看每一笔，搜索人名、商户或金额") }
        item { SourceFilterRow(platformFilter, onPlatformChange) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !reviewMode, onClick = { onReviewModeChange(false) },
                    label = { Text("全部流水") },
                )
                if(pendingCount>0) FilterChip(
                    selected=reviewMode,onClick={onReviewModeChange(true)},
                    label={Text("需核对 "+pendingCount+" 笔")},
                )
            }
        }
        if (reviewMode) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        selectedIds = if (checked.isEmpty())
                            pendingTransactions.take(100).map { it.id }.toSet()
                            else emptySet()
                    }) { Text(if (checked.isEmpty()) "选择前 100 笔" else "取消全选") }
                    TextButton(
                        enabled = checked.isNotEmpty(),
                        onClick = { bulkDialog = true }
                    ) { Text("批量确认 ${checked.size} 笔") }
                }
            }
        }
        if (!reviewMode) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("搜索人名、商户、金额或备注") },
                    placeholder = { Text("搜索全部历史流水") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("ALL" to "全部", "EXPENSE" to "支出", "INCOME" to "收入", "OTHER" to "其他")
                        .forEach { (key, title) ->
                            FilterChip(
                                selected = searchFlowFilter == key,
                                onClick = { onSearchFlowChange(key) },
                                label = { Text(title) },
                            )
                        }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (reviewMode) "暂无待确认交易" else "没有匹配的流水",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(DailyLedger.group(shown), key = { "day_${it.first}" }) { (day, records) ->
            DailyLedgerHeader(day, records)
            records.forEach { tx ->
                if (reviewMode) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = tx.id in checked,
                            onCheckedChange = { isChecked ->
                                selectedIds = if (isChecked && checked.size < 100)
                                    checked + tx.id else checked - tx.id
                            }
                        )
                        Text(
                            if (tx.id in checked) "已选择" else "选择此笔",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                TransactionCard(tx) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick={editing=tx}) {
                            Text(if(tx.flowType==FlowType.PENDING) "确认用途" else "修改性质")
                        }
                        if(tx.flowType==FlowType.LOAN_REPAYMENT) {
                            TextButton(onClick={editingLoan=tx}) {
                                Text(if(loanDetails.containsKey(tx.id)) "修改还款拆分" else "拆分本金/利息")
                            }
                        }
                    }
                }
            }
        }
        if (!reviewMode && shown.size >= searchLimit && searchLimit < 10000) {
            item {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("查看更多历史流水（已显示 ${shown.size} 笔）")
                }
            }
        }
        if (reviewMode && pendingCount > pendingTransactions.size) {
            item {
                Text(
                    "仅显示最近 ${pendingTransactions.size} 笔，确认后自动加载后续记录。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
