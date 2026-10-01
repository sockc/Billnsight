package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.importer.TransactionClassifier
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction

@Composable
fun TransactionsScreen(
    transactions: List<Transaction>,
    platformFilter: Platform?,
    onPlatformChange: (Platform?) -> Unit,
    onCategoryChange: (Transaction, String, Boolean) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("流水", "微信/支付宝来源清楚标记；点击分类可修正并记住商户。") }
        item { SourceFilterRow(platformFilter, onPlatformChange) }

        if (transactions.isEmpty()) {
            item { Text("当前筛选条件下暂无流水", modifier = Modifier.padding(20.dp)) }
        }

        items(transactions, key = { it.id }) { tx ->
            var expanded by remember(tx.id) { mutableStateOf(false) }
            TransactionCard(tx) {
                if (tx.flowType == FlowType.EXPENSE) {
                    Box {
                        Text(
                            "修改分类",
                            modifier = Modifier.clickable { expanded = true }.padding(top = 4.dp),
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            TransactionClassifier.categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { CategoryBadge(category) },
                                    onClick = {
                                        expanded = false
                                        onCategoryChange(tx, category, true)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
