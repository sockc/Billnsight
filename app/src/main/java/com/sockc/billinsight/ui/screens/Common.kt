package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.displayName
import com.sockc.billinsight.model.displayName
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toDisplayDateTime
import com.sockc.billinsight.util.toYuanText
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun categoryColor(category: String): Color = when (category) {
    "餐饮" -> Color(0xFFFF8A00)
    "商超日用" -> Color(0xFFE0A800)
    "购物" -> Color(0xFF8E5CC7)
    "交通" -> Color(0xFF2979FF)
    "住房" -> Color(0xFF8D6E63)
    "生活缴费" -> Color(0xFF0097A7)
    "娱乐" -> Color(0xFFEC407A)
    "医疗" -> Color(0xFFE53935)
    "车辆" -> Color(0xFF1565C0)
    "数码" -> Color(0xFF5E35B1)
    "教育" -> Color(0xFF43A047)
    "旅行" -> Color(0xFF039BE5)
    "经营相关" -> Color(0xFF00796B)
    "红包收入", "人情" -> Color(0xFFD81B60)
    "待确认" -> Color(0xFFE69E18)
    else -> Color(0xFF757575)
}

fun platformLabel(platform: Platform?): String = when (platform) {
    Platform.WECHAT -> "微信"
    Platform.ALIPAY -> "支付宝"
    Platform.UNKNOWN -> "未知来源"
    null -> "全部"
}

private fun platformColor(platform: Platform): Color = when (platform) {
    Platform.WECHAT -> Color(0xFF07C160)
    Platform.ALIPAY -> Color(0xFF1677FF)
    Platform.UNKNOWN -> Color(0xFF757575)
}

@Composable
fun PageTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        androidx.compose.material3.TextButton(onClick = onPrevious) { Text("‹ 上月") }
        Text(
            "${month.year} 年 ${month.monthValue} 月",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        androidx.compose.material3.TextButton(onClick = onNext) { Text("下月 ›") }
    }
}

@Composable
fun SourceFilterRow(selected: Platform?, onSelect: (Platform?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("全部") },
        )
        FilterChip(
            selected = selected == Platform.WECHAT,
            onClick = { onSelect(Platform.WECHAT) },
            label = { Text("微信") },
        )
        FilterChip(
            selected = selected == Platform.ALIPAY,
            onClick = { onSelect(Platform.ALIPAY) },
            label = { Text("支付宝") },
        )
    }
}

@Composable
fun CategoryBadge(category: String) {
    val color = categoryColor(category)
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(7.dp).background(color, CircleShape)
            )
            Text(category, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PlatformBadge(platform: Platform) {
    val color = platformColor(platform)
    Surface(
        color = color.copy(alpha = 0.13f),
        contentColor = color,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            platformLabel(platform),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The same inline expansion is available wherever a transaction appears:
 * dashboard, merchant/product drilldowns, discover and the entire ledger.
 */
@Composable
fun TransactionCard(item: Transaction, trailing: @Composable (() -> Unit)? = null) {
    var expanded by remember(item.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.counterparty.ifBlank { item.description.ifBlank { "未知交易" } },
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.padding(top = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlatformBadge(item.platform)
                        CategoryBadge(item.category)
                    }
                    Text(
                        item.occurredAt.toDisplayDateTime(),
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.description.isNotBlank() && item.description != item.counterparty) {
                        Text(item.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(item.amountCent.toYuanText(), fontWeight = FontWeight.Bold)
                    Text(item.flowType.displayName(), style = MaterialTheme.typography.labelSmall)
                    Text(
                        if (expanded) "收起 ▲" else "展开 ▼",
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (expanded) {
                androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val fullDate = Instant.ofEpochMilli(item.occurredAt)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    DetailRow("交易时间", fullDate)
                    DetailRow("来源", platformLabel(item.platform))
                    DetailRow("收付款人/商户", item.counterparty.ifBlank { "未提供" })
                    DetailRow("商品/说明", item.description.ifBlank { "未提供" })
                    DetailRow("交易类型", item.tradeType.ifBlank { item.flowType.displayName() })
                    DetailRow("性质", item.flowType.displayName())
                    DetailRow("分类", item.category)
                    DetailRow("方向", item.directionText.ifBlank { "未提供" })
                    DetailRow("金额", item.amountCent.toYuanText())
                    DetailRow("支付方式", item.paymentMethod.ifBlank { "未提供" })
                    if (item.transactionId.isNotBlank()) DetailRow("交易单号", item.transactionId)
                    if (item.merchantOrderId.isNotBlank()) DetailRow("商户单号", item.merchantOrderId)
                    DetailRow("导入文件", item.sourceFile.ifBlank { "未提供" })
                    trailing?.invoke()
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1.7f), style = MaterialTheme.typography.bodySmall)
    }
}
