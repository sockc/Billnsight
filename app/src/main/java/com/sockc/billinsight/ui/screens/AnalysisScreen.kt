package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.analysis.MerchantAnalysis
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.LinkKind
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.displayName
import com.sockc.billinsight.util.toYuanText

@Composable
fun AnalysisScreen(
    state: BillUiState,
    onPlatformChange: (Platform?) -> Unit,
    onProductAlias: (String, String, String) -> Unit,
    onLinkRecovery: (Long, Long, LinkKind, Long) -> Unit,
    onDeleteLink: (Long) -> Unit,
) {
    var expandedIncome by remember(state.month, state.platformFilter) { mutableStateOf<FlowType?>(null) }
    var expandedPayer by remember(state.month, state.platformFilter) { mutableStateOf<String?>(null) }
    var expandedCategory by remember(state.month, state.platformFilter) { mutableStateOf<String?>(null) }
    val incomeTypes = setOf(
        FlowType.INCOME, FlowType.GIFT_INCOME, FlowType.BUSINESS_INCOME,
        FlowType.LOAN_RECOVERY, FlowType.REFUND
    )
    val incomeGroups = state.monthlyTransactions
        .filter { it.flowType in incomeTypes &&
            (it.flowType != FlowType.INCOME || it.id !in state.linkedReceiptIds) }
        .groupBy { it.flowType }.toList()
        .sortedByDescending { (_, tx) -> tx.sumOf { it.amountCent } }
    val merchantMax = state.merchantGroups.maxOfOrNull { it.amountCent } ?: 1L

    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("收支分析", "清晰查看每个商户的真实消费、收入及关联明细。") }
        item { SourceFilterRow(state.platformFilter, onPlatformChange) }

        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("本月净消费", style = MaterialTheme.typography.titleMedium)
                    Text(state.summary.netExpenseCent.toYuanText(),
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "原始消费 ${state.summary.expenseCent.toYuanText()} − 已关联退款 " +
                            "${state.summary.linkedRefundCent.toYuanText()} − AA 收回 " +
                            state.summary.linkedShareCent.toYuanText(),
                        style = MaterialTheme.typography.bodySmall)
                    Text(
                        "信用卡还款 ${state.summary.creditRepaymentCent.toYuanText()} 属于资金支出，不重复算作消费。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text("同一商户消费排行", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
            Text(
                "真实商户单独排行；转账备注、收款备注及未识别商户不混入。",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.merchantGroups.isEmpty()) {
            item { Text("当前月份没有可识别的商户消费",
                modifier = Modifier.padding(20.dp)) }
        } else {
            items(state.merchantGroups.take(30), key = { "merchant_${it.key}" }) { group ->
                val rank = state.merchantGroups.indexOfFirst { it.key == group.key } + 1
                MerchantRankCard(rank, group, merchantMax, state.productAliases, onProductAlias)
            }
        }

        item {
            Text("收入来源", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        }
        if (incomeGroups.isEmpty()) {
            item { Text("本月暂无收入流水", modifier = Modifier.padding(horizontal = 20.dp)) }
        } else {
            items(incomeGroups, key = { "income_${it.first}" }) { (type, records) ->
                val isOpen = expandedIncome == type
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth().clickable {
                            expandedIncome = if (isOpen) null else type
                            expandedPayer = null
                        }.padding(vertical = 6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(type.displayName(), fontWeight = FontWeight.SemiBold)
                                Text("${records.size} 笔 · ${if (isOpen) "收起 ▲" else "展开付款人 ▼"}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text(records.sumOf { it.amountCent }.toYuanText(),
                                fontWeight = FontWeight.Bold)
                        }
                        if (isOpen) {
                            val payers = records.groupBy {
                                it.counterparty.ifBlank { "未知付款人" }
                            }.toList().sortedByDescending { (_, tx) -> tx.sumOf { it.amountCent } }
                            payers.forEach { (payer, list) ->
                                val key = type.name + "/" + payer
                                val payerOpen = expandedPayer == key
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth().clickable {
                                    expandedPayer = if (payerOpen) null else key
                                }.padding(vertical = 9.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(payer, maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium)
                                        Text("${list.size} 笔 · ${if (payerOpen) "收起 ▲" else "全部明细 ▼"}",
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(list.sumOf { it.amountCent }.toYuanText(),
                                        modifier = Modifier.padding(start = 10.dp),
                                        fontWeight = FontWeight.SemiBold)
                                }
                                if (payerOpen) list.forEach { TransactionCard(it) }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("支出分类", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        }
        items(state.categories, key = { "category_${it.category}" }) { category ->
            val open = expandedCategory == category.category
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth().clickable {
                        expandedCategory = if (open) null else category.category
                    }.padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            CategoryBadge(category.category)
                            Text("${category.count} 笔 · ${if (open) "收起 ▲" else "展开商户 ▼"}",
                                modifier = Modifier.padding(top = 5.dp),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text(category.amountCent.toYuanText(), fontWeight = FontWeight.Bold)
                    }
                    if (open) {
                        val categoryTransactions = state.monthlyTransactions.filter {
                            it.category == category.category
                        }
                        val merchants = MerchantAnalysis.groups(categoryTransactions)
                        val max = merchants.maxOfOrNull { it.amountCent } ?: 1L
                        merchants.forEachIndexed { index, merchant ->
                            MerchantRankCard(index + 1, merchant, max, state.productAliases,
                                onProductAlias)
                        }
                        categoryTransactions.filter { it.flowType == FlowType.GIFT_EXPENSE }
                            .forEach { TransactionCard(it) }
                        if (merchants.isEmpty() &&
                            categoryTransactions.none { it.flowType == FlowType.GIFT_EXPENSE }) {
                            Text("本分类暂无可辨识商户，原始记录可在流水页查看。",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            RecoveryLinkSection(state, onLinkRecovery, onDeleteLink)
        }
    }
}
