package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.ProductGroup
import com.sockc.billinsight.model.displayName
import com.sockc.billinsight.util.toYuanText

@Composable
fun AnalysisScreen(state: BillUiState, onPlatformChange: (Platform?) -> Unit) {
    val max = state.categories.maxOfOrNull { it.amountCent }?.coerceAtLeast(1L) ?: 1L
    var expandedCategory by remember(state.month, state.platformFilter) { mutableStateOf<String?>(null) }
    var expandedMerchant by remember(state.month, state.platformFilter) { mutableStateOf<String?>(null) }
    var expandedProduct by remember(state.month, state.platformFilter) { mutableStateOf<String?>(null) }
    var expandedIncome by remember(state.month, state.platformFilter) { mutableStateOf<FlowType?>(null) }
    val incomeTypes = setOf(
        FlowType.INCOME, FlowType.GIFT_INCOME, FlowType.BUSINESS_INCOME,
        FlowType.LOAN_RECOVERY, FlowType.REFUND
    )
    val incomeGroups = state.monthlyTransactions.filter { it.flowType in incomeTypes }
        .groupBy { it.flowType }.toList().sortedByDescending { (_, tx) -> tx.sumOf { it.amountCent } }

    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("收支分析", "分类 → 商户 → 商品 → 全部消费；每项收入也可逐级展开查看。") }
        item { SourceFilterRow(state.platformFilter, onPlatformChange) }

        item {
            Text("收入来源", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        }
        if (incomeGroups.isEmpty()) {
            item { Text("本月暂无收入流水", modifier = Modifier.padding(horizontal = 20.dp)) }
        } else {
            items(incomeGroups, key = { "income_${it.first}" }) { (type, records) ->
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            expandedIncome = if (expandedIncome == type) null else type
                        }.padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(type.displayName() + " · ${records.size} 笔",
                            modifier = Modifier.weight(1f))
                        Text(records.sumOf { it.amountCent }.toYuanText(), fontWeight = FontWeight.Bold)
                    }
                    if (expandedIncome == type) {
                        records.forEach { TransactionCard(it) }
                    }
                }
            }
        }

        item {
            Text("消费分类", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        }
        items(state.categories, key = { "category_${it.category}" }) { category ->
            val expanded = expandedCategory == category.category
            val color = categoryColor(category.category)
            Column {
                Column(
                    Modifier.fillMaxWidth().clickable {
                        expandedCategory = if (expanded) null else category.category
                        expandedMerchant = null
                        expandedProduct = null
                    }.padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        CategoryBadge(category.category)
                        Text(" · ${category.count} 笔", modifier = Modifier.weight(1f).padding(top = 3.dp),
                            style = MaterialTheme.typography.bodySmall)
                        Text(category.amountCent.toYuanText(), fontWeight = FontWeight.Bold)
                    }
                    androidx.compose.foundation.layout.Box(
                        Modifier.padding(top = 7.dp)
                            .width((280f * category.amountCent.toFloat() / max.toFloat()).coerceAtLeast(4f).dp)
                            .height(8.dp)
                            .background(color, MaterialTheme.shapes.small)
                    )
                    Text(if (expanded) "收起商户 ▲" else "展开商户 ▼",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelSmall, color = color)
                }
                if (expanded) {
                    state.categoryMerchants[category.category].orEmpty().forEach { merchant ->
                        val merchantKey = category.category + "/" + merchant.merchant
                        val merchantExpanded = expandedMerchant == merchantKey
                        Column(Modifier.padding(start = 16.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    expandedMerchant = if (merchantExpanded) null else merchantKey
                                    expandedProduct = null
                                }.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(merchant.merchant + " · ${merchant.count} 笔" +
                                    if (merchantExpanded) " ▲" else " ▼",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(merchant.amountCent.toYuanText(),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            if (merchantExpanded) {
                                val groups = state.productGroups.filter {
                                    it.merchant.equals(merchant.merchant, ignoreCase = true)
                                }.mapNotNull { group ->
                                    val transactions = group.transactions.filter {
                                        it.category == category.category
                                    }
                                    if (transactions.isEmpty()) null else group.copy(
                                        transactions = transactions,
                                        count = transactions.size,
                                        amountCent = transactions.sumOf { it.amountCent }
                                    )
                                }.sortedByDescending { it.amountCent }

                                groups.forEach { product ->
                                    val productKey = merchantKey + "/" + product.product
                                    val isOpen = expandedProduct == productKey
                                    Column(Modifier.padding(start = 10.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth().clickable {
                                                expandedProduct = if (isOpen) null else productKey
                                            }.padding(horizontal = 16.dp, vertical = 9.dp)
                                        ) {
                                            Text(
                                                product.product + " · ${product.count} 笔" +
                                                    if (isOpen) " ▲" else " ▼",
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(product.amountCent.toYuanText(),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold)
                                        }
                                        if (isOpen) {
                                            Text(
                                                if (product.unspecified) "未注明具体商品的全部原始流水"
                                                else "全部 ${product.count} 笔 · 平均每笔 " +
                                                    (product.amountCent / product.count).toYuanText(),
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            product.transactions.forEach { TransactionCard(it) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }

        item {
            Text("同一商品消费排行", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        }
        items(state.productGroups.filterNot { it.unspecified }.take(20),
            key = { "product_${it.merchant}_${it.product}" }) { product ->
            val key = "ranking/${product.merchant}/${product.product}"
            val isOpen = expandedProduct == key
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        expandedProduct = if (isOpen) null else key
                    }.padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("${product.product} · ${product.merchant} · ${product.count} 笔" +
                        if (isOpen) " ▲" else " ▼",
                        modifier = Modifier.weight(1f))
                    Text(product.amountCent.toYuanText(), fontWeight = FontWeight.Bold)
                }
                if (isOpen) product.transactions.forEach { TransactionCard(it) }
            }
        }
    }
}
