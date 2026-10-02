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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.util.toYuanText
import kotlin.math.abs

@Composable
fun HomeScreen(
    state: BillUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onImport: () -> Unit,
    onPlatformChange: (Platform?) -> Unit,
    onReviewPending: () -> Unit,
) {
    val current = state.summary.netExpenseCent
    val previous = state.previousSummary.netExpenseCent
    val delta = current - previous
    val percent = if (previous > 0) delta * 100.0 / previous else null
    var expandedSummary by remember(state.month, state.platformFilter) { mutableStateOf<String?>(null) }
    val allIncome = state.monthlyTransactions.filter { tx ->
        tx.flowType in setOf(
            FlowType.INCOME, FlowType.GIFT_INCOME, FlowType.BUSINESS_INCOME,
            FlowType.LOAN_RECOVERY, FlowType.REFUND
        ) || (tx.directionText.contains("收入") && tx.flowType == FlowType.TRANSFER)
    }
    val allExpense = state.monthlyTransactions.filter { tx ->
        tx.flowType in setOf(
            FlowType.EXPENSE, FlowType.GIFT_EXPENSE, FlowType.BUSINESS_EXPENSE,
            FlowType.LOAN_OUT, FlowType.CREDIT_REPAYMENT
        ) || (tx.directionText.contains("支出") && tx.flowType == FlowType.TRANSFER)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("账单洞察", "不要求天天记账，导入账单后直接看钱去了哪里。") }
        item { MonthHeader(state.month, onPrevious, onNext) }
        item { SourceFilterRow(state.platformFilter, onPlatformChange) }
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("${platformLabel(state.platformFilter)} · 本月实际净消费", style = MaterialTheme.typography.labelLarge)
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
                    Text(
                        "原始消费 ${state.summary.expenseCent.toYuanText()} · 已关联退款 " +
                            "${state.summary.linkedRefundCent.toYuanText()} · AA 分摊 " +
                            state.summary.linkedShareCent.toYuanText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Text(
                        "本月资金支出（含信用卡还款）：${state.summary.cashOutflowCent.toYuanText()}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "其中信用卡还款 ${state.summary.creditRepaymentCent.toYuanText()}（" +
                            "${state.summary.creditRepaymentCount} 笔），不重复计入消费。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryMini("收入", state.summary.incomeCent.toYuanText())
                        SummaryMini("退款", state.summary.refundCent.toYuanText())
                        SummaryMini("流水", "${state.summary.transactionCount} 笔")
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = {
                    expandedSummary = if (expandedSummary == "expense") null else "expense"
                }) { Text(if (expandedSummary == "expense") "收起全部支出 ▲" else "展开全部支出 ▼") }
                TextButton(onClick = {
                    expandedSummary = if (expandedSummary == "income") null else "income"
                }) { Text(if (expandedSummary == "income") "收起全部收入 ▲" else "展开全部收入 ▼") }
            }
        }
        if (expandedSummary != null) {
            item {
                Text(
                    if (expandedSummary == "expense")
                        "本月全部支出流水（包含转账和经营支出，各自统计口径不变）"
                    else
                        "本月全部收入流水（包含红包、退款和经营收款，各自统计口径不变）",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            val entries = if (expandedSummary == "expense") allExpense else allIncome
            if (entries.isEmpty()) {
                item { Text("本月暂无此类流水", modifier = Modifier.padding(20.dp)) }
            } else {
                items(DailyLedger.group(entries), key = { "summary_day_${it.first}" }) { (day, dayRecords) ->
                    DailyLedgerHeader(day, dayRecords)
                    dayRecords.forEach { TransactionCard(it) }
                }
            }
        }
        item {
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("导入微信 / 支付宝账单")
            }
        }
        if (state.pendingCount > 0) {
            item {
                Button(
                    onClick = onReviewPending,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) { Text("待确认交易 ${state.pendingCount} 笔 · 去核对") }
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
