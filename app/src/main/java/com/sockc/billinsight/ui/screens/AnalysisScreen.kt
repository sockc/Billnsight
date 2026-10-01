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
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.util.toYuanText

@Composable
fun AnalysisScreen(
    state: BillUiState,
    onPlatformChange: (Platform?) -> Unit,
) {
    val max = state.categories.maxOfOrNull { it.amountCent }?.coerceAtLeast(1) ?: 1
    var expandedCategory by remember(state.month, state.platformFilter) { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("消费分析", "点开分类即可继续看钱具体花给了哪些商户。") }
        item { SourceFilterRow(state.platformFilter, onPlatformChange) }

        items(state.categories) { item ->
            val expanded = expandedCategory == item.category
            val color = categoryColor(item.category)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        expandedCategory = if (expanded) null else item.category
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth()) {
                    CategoryBadge(item.category)
                    Text(
                        " · ${item.count} 笔",
                        modifier = Modifier.weight(1f).padding(top = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(item.amountCent.toYuanText(), fontWeight = FontWeight.Bold)
                }
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .padding(top = 7.dp)
                        .width((280f * item.amountCent.toFloat() / max.toFloat()).coerceAtLeast(4f).dp)
                        .height(8.dp)
                        .background(color, MaterialTheme.shapes.small)
                )
                Text(
                    if (expanded) "收起商户明细 ↑" else "查看商户明细 ↓",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )

                if (expanded) {
                    val merchants = state.categoryMerchants[item.category].orEmpty()
                    if (merchants.isEmpty()) {
                        Text(
                            "暂无商户明细",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        merchants.forEachIndexed { index, merchant ->
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp, start = 12.dp)
                            ) {
                                Text(
                                    "${index + 1}. ${merchant.merchant} · ${merchant.count} 笔",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    merchant.amountCent.toYuanText(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "高消费商户",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(20.dp)
            )
        }
        items(state.merchants.take(10)) { item ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp)) {
                Text("${item.merchant} · ${item.count} 笔", modifier = Modifier.weight(1f))
                Text(item.amountCent.toYuanText())
            }
        }
    }
}
