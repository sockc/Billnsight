package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.util.toYuanText
import kotlin.math.max

@Composable
fun DiscoverScreen(
    state: BillUiState,
    onPlatformChange: (Platform?) -> Unit,
    onSmallThresholdChange: (Int) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("钱去哪了", "找小额高频、固定支出、大额消费和每天的花钱节奏。") }
        item { SourceFilterRow(state.platformFilter, onPlatformChange) }

        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("小额高频", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(20, 50, 100).forEach { value ->
                            FilterChip(
                                selected = state.smallThresholdYuan == value,
                                onClick = { onSmallThresholdChange(value) },
                                label = { Text("< ¥$value") },
                            )
                        }
                    }
                    Text(
                        "${state.summary.smallExpenseCount} 笔 · ${state.summary.smallExpenseCent.toYuanText()}",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "只统计真实消费，不含转账、充值和提现。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            InsightCard(
                title = "本月红包",
                main = "发出 ${state.summary.giftExpenseCent.toYuanText()} · 收到 ${state.summary.giftIncomeCent.toYuanText()}",
                detail = "发出 ${state.summary.giftExpenseCount} 笔，收到 ${state.summary.giftIncomeCount} 笔；收到的红包不冲减消费。",
            )
        }
        if (state.pendingCount > 0) {
            item {
                InsightCard(
                    title = "待确认交易",
                    main = "${state.pendingCount} 笔",
                    detail = "个人转账和不明确的二维码收付款暂不计消费，可在「流水 → 待确认」核对。",
                )
            }
        }
        item {
            InsightCard(
                title = "信用卡还款",
                main = state.summary.creditRepaymentCent.toYuanText(),
                detail = "${state.summary.creditRepaymentCount} 笔 · 计入资金支出，不重复计入实际消费",
            )
        }
        item {
            InsightCard(
                title = "转账与资金流转",
                main = state.summary.transferCent.toYuanText(),
                detail = "这部分不算消费，避免银行卡→微信→付款被重复计算。",
            )
        }

        item {
            Text(
                "消费日历",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
        item { ConsumptionCalendar(state) }

        item {
            Text(
                "疑似固定支出",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
        if (state.recurringExpenses.isEmpty()) {
            item {
                Text(
                    "至少需要 2 个月相近的同商户消费，才会在这里识别。",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.recurringExpenses) { item ->
                val current = if (item.latestMonthCent > 0) {
                    "本月 ${item.latestMonthCent.toYuanText()} · "
                } else {
                    "本月尚未出现 · "
                }
                InsightCard(
                    title = item.merchant,
                    main = "月均约 ${item.averageMonthlyCent.toYuanText()}",
                    detail = "${current}近 ${item.activeMonths} 个月出现 · 共 ${item.transactionCount} 笔",
                )
            }
        }

        item {
            Text(
                "本月最大支出",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(20.dp)
            )
        }
        items(state.largestExpenses) { TransactionCard(it) }

        item {
            Text(
                "高频 / 高额商户",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(20.dp)
            )
        }
        items(state.monthlyMerchantGroups.take(10)) { item ->
            val rank = state.monthlyMerchantGroups.indexOfFirst { it.key == item.key } + 1
            MerchantRankCard(
                rank = rank,
                group = item,
                maxAmount = state.monthlyMerchantGroups.firstOrNull()?.amountCent ?: 1L,
                aliases = state.productAliases,
            )
        }
    }
}

@Composable
private fun ConsumptionCalendar(state: BillUiState) {
    val totals = state.dailyTotals.associateBy { it.dayOfMonth }
    val days = state.month.lengthOfMonth()
    val leading = state.month.atDay(1).dayOfWeek.value - 1
    val cells = buildList<Int?> {
        repeat(leading) { add(null) }
        (1..days).forEach { add(it) }
        while (size % 7 != 0) add(null)
    }
    val maxAmount = max(1L, state.dailyTotals.maxOfOrNull { it.amountCent } ?: 0L)

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        if (day == null) {
                            Box(Modifier.weight(1f).height(56.dp).padding(2.dp))
                        } else {
                            val daily = totals[day]
                            val ratio = (daily?.amountCent ?: 0L).toFloat() / maxAmount.toFloat()
                            val alpha = if (daily == null) 0.03f else (0.10f + ratio * 0.28f).coerceAtMost(0.38f)
                            Surface(
                                modifier = Modifier.weight(1f).height(56.dp).padding(2.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("$day", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    if (daily != null && daily.amountCent > 0) {
                                        Text(
                                            compactMoney(daily.amountCent),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun compactMoney(cents: Long): String {
    val yuan = cents / 100.0
    return when {
        yuan >= 1000 -> "¥%.1fk".format(yuan / 1000.0)
        yuan >= 100 -> "¥%.0f".format(yuan)
        else -> "¥%.1f".format(yuan)
    }
}

@Composable
private fun InsightCard(title: String, main: String, detail: String) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(main, style = MaterialTheme.typography.headlineSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
