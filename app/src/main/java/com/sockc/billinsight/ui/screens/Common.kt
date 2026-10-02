package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.model.displayName
import com.sockc.billinsight.util.toDisplayDateTime
import com.sockc.billinsight.util.toYuanText
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun categoryColor(category: String): Color = when (category) {
    "餐饮" -> Color(0xFFE68B43)
    "水果" -> Color(0xFF45A36E)
    "买菜" -> Color(0xFF67B76D)
    "饮品" -> Color(0xFF9C76CF)
    "加油" -> Color(0xFFCF9134)
    "商超日用" -> Color(0xFFB48D2C)
    "购物" -> Color(0xFF9472CC)
    "交通", "车辆" -> Color(0xFF3988D3)
    "住房" -> Color(0xFF977E64)
    "生活缴费" -> Color(0xFF279DA6)
    "娱乐" -> Color(0xFFCB608D)
    "医疗" -> Color(0xFFCA5260)
    "数码" -> Color(0xFF7965CC)
    "教育" -> Color(0xFF419B75)
    "旅行" -> Color(0xFF389BC1)
    "经营相关" -> Color(0xFF28886B)
    "红包收入", "人情" -> Color(0xFFC8618A)
    "待确认" -> Color(0xFFD89E3C)
    "转账支出" -> Color(0xFF657FC5)
    "转账收入","扫码收入" -> Color(0xFF2D9B7E)
    "资金提现" -> Color(0xFF8795A7)
    "金融费用", "贷款还款", "信用卡还款" -> Color(0xFFC28B36)
    "贷款到账", "收入" -> Color(0xFF2D9B7E)
    else -> Color(0xFF8795A7)
}

private fun categoryIcon(category: String): ImageVector = when (category) {
    "餐饮" -> Icons.Outlined.LocalDining
    "水果","买菜" -> Icons.Outlined.Storefront
    "饮品" -> Icons.Outlined.LocalDining
    "加油" -> Icons.Outlined.DirectionsCar
    "商超日用" -> Icons.Outlined.Storefront
    "购物" -> Icons.Outlined.ShoppingBag
    "交通", "车辆" -> Icons.Outlined.DirectionsCar
    "住房" -> Icons.Outlined.Home
    "医疗" -> Icons.Outlined.LocalHospital
    "娱乐" -> Icons.Outlined.Theaters
    "教育" -> Icons.Outlined.School
    "旅行" -> Icons.Outlined.FlightTakeoff
    "人情", "红包收入" -> Icons.Outlined.CardGiftcard
    "转账支出","转账收入","资金提现" -> Icons.Outlined.SwapHoriz
    "扫码收入" -> Icons.Outlined.AccountBalanceWallet
    "金融费用", "贷款还款", "信用卡还款" -> Icons.Outlined.AccountBalance
    "收入", "贷款到账" -> Icons.Outlined.AccountBalanceWallet
    "资金流转" -> Icons.Outlined.SwapHoriz
    "其他" -> Icons.Outlined.ReceiptLong
    else -> Icons.Outlined.Payments
}

fun platformLabel(platform: Platform?): String = when (platform) {
    Platform.WECHAT -> "微信"
    Platform.ALIPAY -> "支付宝"
    Platform.JD -> "京东"
    Platform.DOUYIN -> "抖音"
    Platform.MEITUAN -> "美团"
    Platform.BANK -> "银行卡"
    Platform.UNKNOWN -> "未知来源"
    null -> "全部"
}

@Composable
fun PageTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=22.dp,bottom=10.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(title,style=MaterialTheme.typography.headlineSmall,
            color=MaterialTheme.colorScheme.onBackground,fontWeight=FontWeight.Bold)
        if(!subtitle.isNullOrBlank()) {
            Text(subtitle,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
        }
    }
}

@Composable
fun SectionHeader(title: String, detail: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title,style=MaterialTheme.typography.titleMedium,
                color=MaterialTheme.colorScheme.onBackground,fontWeight=FontWeight.Bold)
            if(!detail.isNullOrBlank()) Text(detail,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Surface(
        modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),
        color=MaterialTheme.colorScheme.surface,
        shape=RoundedCornerShape(17.dp),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal=7.dp,vertical=1.dp),
            horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.CenterVertically
        ) {
            TextButton(onClick=onPrevious) { Text("‹ 上月") }
            Text("${month.year}年 ${month.monthValue}月",
                style=MaterialTheme.typography.titleMedium)
            TextButton(onClick=onNext) { Text("下月 ›") }
        }
    }
}

@Composable
fun SourceFilterRow(selected: Platform?, onSelect: (Platform?) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical=4.dp),
        contentPadding=androidx.compose.foundation.layout.PaddingValues(horizontal=16.dp),
        horizontalArrangement=Arrangement.spacedBy(8.dp)
    ) {
        listOf(null to "全部",Platform.WECHAT to "微信",Platform.ALIPAY to "支付宝",Platform.JD to "京东",Platform.DOUYIN to "抖音",Platform.MEITUAN to "美团",Platform.BANK to "银行卡")
            .forEach { (platform,label) ->
                FilterChip(
                    selected=selected==platform,
                    onClick={onSelect(platform)},
                    label={Text(label)},
                    shape=RoundedCornerShape(13.dp),
                    colors=FilterChipDefaults.filterChipColors(
                        selectedContainerColor=MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor=MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                )
            }
    }
}

@Composable
fun CategoryBadge(category: String) {
    val c=categoryColor(category)
    Surface(color=c.copy(alpha=0.11f),contentColor=c,
        shape=RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal=8.dp,vertical=4.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Icon(categoryIcon(category),contentDescription=null,modifier=Modifier.size(12.dp))
            Text(category,style=MaterialTheme.typography.labelSmall,maxLines=1)
        }
    }
}

@Composable
fun PlatformBadge(platform: Platform) {
    val color=when(platform) {
        Platform.WECHAT -> Color(0xFF19A868)
        Platform.ALIPAY -> Color(0xFF367CE4)
        Platform.JD -> Color(0xFFE84E49)
        Platform.DOUYIN -> Color(0xFF202A3B)
        Platform.MEITUAN -> Color(0xFFFFBA20)
        Platform.BANK -> Color(0xFF735DD7)
        Platform.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color=color.copy(alpha=0.10f),contentColor=color,
        shape=RoundedCornerShape(10.dp)) {
        Text(platformLabel(platform),modifier=Modifier.padding(horizontal=7.dp,vertical=4.dp),
            style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun TransactionCard(
    item:Transaction,
    trailing:@Composable (() -> Unit)?=null,
    onMerchantClick:(()->Unit)?=null,
) {
    var expanded by remember(item.id) { mutableStateOf(false) }
    val incoming=item.directionText.contains("收入")
    val outgoing=item.directionText.contains("支出")
    val color=categoryColor(item.category)
    Card(
        modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
        shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.7f)),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable{expanded=!expanded}.padding(13.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp),
                verticalAlignment=Alignment.CenterVertically,
            ) {
                Box(Modifier.size(43.dp).background(color.copy(alpha=0.12f),
                    RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {
                    Icon(categoryIcon(item.category),contentDescription=null,
                        tint=color,modifier=Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(item.counterparty.ifBlank { item.description.ifBlank { "未知交易" } },
                        modifier=if(onMerchantClick!=null) Modifier.clickable(onClick=onMerchantClick)
                            else Modifier,
                        fontWeight=FontWeight.SemiBold,
                        style=MaterialTheme.typography.bodyMedium,maxLines=1,
                        overflow=TextOverflow.Ellipsis)
                    if(item.description.isNotBlank() && item.description!=item.counterparty) {
                        Text(item.description,style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,
                            overflow=TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        PlatformBadge(item.platform)
                        CategoryBadge(item.category)
                    }
                }
                Column(horizontalAlignment=Alignment.End,
                    verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(
                        (if(incoming) "+" else if(outgoing) "−" else "") + item.amountCent.toYuanText(),
                        fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,
                        color=if(incoming) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        maxLines=1
                    )
                    Text(item.occurredAt.toDisplayDateTime(),
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(if(expanded) "收起" else "详情",
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.primary)
                        Icon(Icons.Outlined.ChevronRight,null,modifier=Modifier.size(16.dp),
                            tint=MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if(expanded) {
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(horizontal=16.dp,vertical=13.dp),
                    verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    val date=Instant.ofEpochMilli(item.occurredAt)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    DetailRow("交易时间",date)
                    DetailRow("来源",platformLabel(item.platform))
                    DetailRow("对方/商户",item.counterparty.ifBlank{"未提供"})
                    DetailRow("商品/备注",item.description.ifBlank{"未提供"})
                    DetailRow("原始类型",item.tradeType.ifBlank{item.flowType.displayName()})
                    DetailRow("交易性质",item.flowType.displayName())
                    DetailRow("分类",item.category)
                    DetailRow("收支方向",item.directionText.ifBlank{"未提供"})
                    DetailRow("金额",item.amountCent.toYuanText())
                    DetailRow("支付方式",item.paymentMethod.ifBlank{"未提供"})
                    if(item.transactionId.isNotBlank()) DetailRow("交易单号",item.transactionId)
                    if(item.merchantOrderId.isNotBlank()) DetailRow("商户单号",item.merchantOrderId)
                    DetailRow("导入文件",item.sourceFile.ifBlank{"未提供"})
                    trailing?.invoke()
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label:String,value:String) {
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),
        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,modifier=Modifier.weight(0.8f),style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,modifier=Modifier.weight(1.7f),style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun EmptyFinanceCard(message:String) {
    Card(
        modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp),
        shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)
    ) {
        Text(
            message,modifier=Modifier.padding(20.dp),
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            style=MaterialTheme.typography.bodyMedium
        )
    }
}
