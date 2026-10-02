package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.analysis.MerchantGroup
import com.sockc.billinsight.analysis.MerchantAnalysis
import com.sockc.billinsight.analysis.ProductAnalysis
import com.sockc.billinsight.util.toYuanText

/** Stable 2-column layout: long merchant names can never push the amount off screen. */
@Composable
fun MerchantRankCard(
    rank: Int,
    group: MerchantGroup,
    maxAmount: Long,
    aliases: Map<String,String> = emptyMap(),
    onSaveAlias: ((merchant: String, original: String, canonical: String) -> Unit)? = null,
    onSaveMerchantAlias: ((original: String, canonical: String) -> Unit)? = null,
) {
    var expanded by remember(group.key) { mutableStateOf(false) }
    var openProduct by remember(group.key) { mutableStateOf<String?>(null) }
    var renameProduct by remember(group.key) { mutableStateOf<String?>(null) }
    var canonical by remember(group.key) { mutableStateOf("") }
    val originalNames = remember(group.key, group.transactions.size) {
        group.transactions.mapNotNull(MerchantAnalysis::merchantName).distinct()
    }
    var merchantDialog by remember(group.key) { mutableStateOf(false) }
    var selectedMerchantName by remember(group.key) { mutableStateOf(originalNames.firstOrNull().orEmpty()) }
    var mergedMerchantName by remember(group.key) { mutableStateOf(group.name) }
    val fraction = (group.amountCent.toFloat() / maxAmount.coerceAtLeast(1).toFloat()).coerceIn(0.025f,1f)

    renameProduct?.let { original ->
        AlertDialog(
            onDismissRequest = { renameProduct = null },
            title = { Text("合并同一商户商品名称") },
            text = {
                Column {
                    Text("原名称：$original", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = canonical, onValueChange = { canonical = it.take(80) },
                        label = { Text("统一商品名称") }, singleLine = true,
                    )
                    Text("仅影响分析分组，不会更改原始账单。",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canonical.isNotBlank(),
                    onClick = {
                        onSaveAlias?.invoke(group.transactions.first().counterparty, original, canonical)
                        renameProduct = null
                    }
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameProduct = null }) { Text("取消") } }
        )
    }

    if (merchantDialog) {
        AlertDialog(
            onDismissRequest = { merchantDialog = false },
            title = { Text("合并商户名称") },
            text = {
                Column {
                    Text("选择原商户名称，别名只影响排行及分析，不修改原始账单。",
                        style = MaterialTheme.typography.bodySmall)
                    originalNames.forEach { raw ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selectedMerchantName = raw },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedMerchantName == raw,
                                onClick = { selectedMerchantName = raw }
                            )
                            Text(raw, maxLines = 2)
                        }
                    }
                    OutlinedTextField(
                        value = mergedMerchantName,
                        onValueChange = { mergedMerchantName = it.take(80) },
                        label = { Text("统一商户名称") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedMerchantName.isNotBlank() && mergedMerchantName.isNotBlank() &&
                        selectedMerchantName.trim().lowercase() != mergedMerchantName.trim().lowercase(),
                    onClick = {
                        onSaveMerchantAlias?.invoke(selectedMerchantName, mergedMerchantName)
                        merchantDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { merchantDialog = false }) { Text("取消") } },
        )
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(30.dp).background(MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$rank", color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        group.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${group.count} 笔消费",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                Column(Modifier.widthIn(min = 98.dp), horizontalAlignment = Alignment.End) {
                    Text(group.amountCent.toYuanText(), fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        if (expanded) "收起 ▲" else "展开 ▼",
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 10.dp).height(5.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            ) {
                Box(
                    Modifier.fillMaxWidth(fraction).height(5.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                )
            }
            if (expanded) {
                if (onSaveMerchantAlias != null && originalNames.isNotEmpty()) {
                    TextButton(onClick = {
                        selectedMerchantName = originalNames.first()
                        mergedMerchantName = group.name
                        merchantDialog = true
                    }) { Text("合并商户名称") }
                }
                val products = ProductAnalysis.groups(
                    group.transactions, aliases,
                    merchantLabels=group.transactions.associate { it.id to group.name }
                )
                products.forEach { product ->
                    val key = product.product
                    val opened = openProduct == key
                    Column(Modifier.padding(top = 8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { openProduct = if (opened) null else key }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(product.product, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("${product.count} 笔 · ${if (opened) "收起 ▲" else "查看全部 ▼"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(product.amountCent.toYuanText(), fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 10.dp))
                        }
                        if (opened) {
                            if (!product.unspecified && onSaveAlias != null) {
                                TextButton(onClick = {
                                    renameProduct = product.transactions.first().description
                                    canonical = product.product
                                }) { Text("合并商品名称") }
                            }
                            DailyLedger.group(product.transactions).forEach { (day, records) ->
                                DailyLedgerHeader(day, records)
                                records.forEach { TransactionCard(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}
