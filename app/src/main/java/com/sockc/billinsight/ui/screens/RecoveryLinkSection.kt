package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.LinkKind
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toDisplayDateTime
import com.sockc.billinsight.util.toYuanText
import java.math.BigDecimal
import java.math.RoundingMode

/** All associations are explicit and reversible; no imported row is rewritten. */
@Composable
fun RecoveryLinkSection(
    state: BillUiState,
    onCreate: (expenseId: Long, receiptId: Long, kind: LinkKind, amountCent: Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val expenses = state.monthlyTransactions.filter {
        it.flowType in setOf(FlowType.EXPENSE, FlowType.GIFT_EXPENSE)
    }
    val receipts = state.linkableReceipts.filter {
        it.id !in state.linkedReceiptIds && it.flowType in setOf(FlowType.INCOME, FlowType.REFUND)
    }
    var expense by remember(state.month, state.platformFilter) { mutableStateOf<Transaction?>(null) }
    var receipt by remember(state.month, state.platformFilter) { mutableStateOf<Transaction?>(null) }
    var amount by remember(state.month, state.platformFilter) { mutableStateOf("") }
    var selecting by remember(state.month, state.platformFilter) { mutableStateOf<String?>(null) }

    if (selecting != null) {
        val candidates = if (selecting == "expense") expenses else receipts
        TransactionSelector(
            title = if (selecting == "expense") "选择本月原消费" else "选择退款或 AA 收款（支持跨月）",
            candidates = candidates,
            onDismiss = { selecting = null },
            onChoose = { selected ->
                if (selecting == "expense") expense = selected
                else {
                    receipt = selected
                    if (amount.isBlank()) amount = "%.2f".format(selected.amountCent / 100.0)
                }
                selecting = null
            }
        )
    }
    val parsed = runCatching {
        BigDecimal(amount.trim()).multiply(BigDecimal(100))
            .setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }.getOrNull() ?: 0L

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("退款 / AA 分摊关联", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Text("选择原消费与实际收款后计算净消费；允许部分退款、跨月收款和撤销关联。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { selecting = "expense" },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(expense?.let { "原消费：${it.counterparty} ${it.amountCent.toYuanText()}" }
                    ?: "① 选择本月原消费")
            }
            OutlinedButton(onClick = { selecting = "receipt" },
                modifier = Modifier.fillMaxWidth()) {
                Text(receipt?.let {
                    "${if (it.flowType == FlowType.REFUND) "退款" else "AA 收款"}：${it.counterparty} ${it.amountCent.toYuanText()}"
                } ?: "② 选择退款或 AA 收款")
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' }.take(14) },
                label = { Text("本次关联金额（元）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                enabled = expense != null && receipt != null && parsed > 0,
                onClick = {
                    val e = expense ?: return@Button
                    val r = receipt ?: return@Button
                    onCreate(e.id, r.id,
                        if (r.flowType == FlowType.REFUND) LinkKind.REFUND else LinkKind.SHARE,
                        parsed)
                    receipt = null
                    amount = ""
                }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("关联并重新计算净消费") }

            if (state.links.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text("本月原消费已关联", style = MaterialTheme.typography.titleSmall)
                state.links.forEach { link ->
                    val original = expenses.firstOrNull { it.id == link.expenseId }
                    val returned = state.linkableReceipts.firstOrNull { it.id == link.receiptId }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${original?.counterparty ?: "原消费"} · ${link.amountCent.toYuanText()}",
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${if (link.kind == LinkKind.REFUND) "退款" else "AA 分摊"} · " +
                                    (returned?.counterparty ?: "已关联收款"),
                                style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onDelete(link.id) }) { Text("撤销") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionSelector(
    title: String,
    candidates: List<Transaction>,
    onDismiss: () -> Unit,
    onChoose: (Transaction) -> Unit,
) {
    var query by remember(title) { mutableStateOf("") }
    val filtered = candidates.filter {
        query.isBlank() || listOf(it.counterparty, it.description, it.amountCent.toYuanText())
            .any { field -> field.contains(query, ignoreCase = true) }
    }.take(80)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(70) },
                    label = { Text("搜索对方 / 商品 / 金额") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(filtered, key = { it.id }) { tx ->
                        Column(Modifier.fillMaxWidth().clickable { onChoose(tx) }
                            .padding(vertical = 10.dp)) {
                            Text(tx.counterparty.ifBlank { tx.description },
                                maxLines = 2, fontWeight = FontWeight.SemiBold)
                            Text("${tx.occurredAt.toDisplayDateTime()} · ${tx.amountCent.toYuanText()}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                    if (filtered.isEmpty()) {
                        item { Text("没有符合条件的交易", modifier = Modifier.padding(12.dp)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
