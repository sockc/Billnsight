package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.util.toYuanText
import java.time.LocalDate
import java.time.YearMonth

private val Blue=Color(0xFF3976F5)
private val Green=Color(0xFF16A579)
private val Violet=Color(0xFF8756E8)

@Composable
fun HomeScreen(
    state: BillUiState,
    onImport: () -> Unit,
    onPlatformChange: (Platform?) -> Unit,
    onOpenAnalysis: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onSelectPeriod: (String,LocalDate?,LocalDate?) -> Unit,
    onOpenLedgerFilter: (String) -> Unit,
) {
    val summary=state.homeSummary
    val repayment=summary.creditRepaymentCent+summary.loanRepaymentCent
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            DateScopeTitle("BillInsight","看清每一笔钱",state,onSelectMonth,onSelectPeriod)
        }
        item { SourceFilterRow(state.platformFilter,onPlatformChange) }
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal=16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF458CFE),Color(0xFF3F51DF))))
                .clickable {onOpenLedgerFilter("OUTFLOW")}
            ) {
                Column(Modifier.padding(21.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("所选期间总支出",style=MaterialTheme.typography.titleSmall,
                            color=Color.White.copy(alpha=.87f),modifier=Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronRight,contentDescription="查看总支出账单",
                            tint=Color.White)
                    }
                    Text(summary.cashOutflowCent.toYuanText(),
                        style=MaterialTheme.typography.headlineLarge,
                        fontWeight=FontWeight.ExtraBold,color=Color.White,
                        maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("消费付款、信用卡及贷款还款等已记录对外付款",
                        style=MaterialTheme.typography.bodySmall,
                        color=Color.White.copy(alpha=.90f))
                    HorizontalDivider(color=Color.White.copy(alpha=.25f))
                    Text("点击查看支出账单 · ${state.homeStart} — ${state.homeEnd}",
                        color=Color.White.copy(alpha=.88f),
                        style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    SummaryTile("收入",summary.incomeCent,Green,
                        Icons.Outlined.AccountBalanceWallet,Modifier.weight(1f)) {
                        onOpenLedgerFilter("RECEIPTS")
                    }
                    SummaryTile("消费",summary.shoppingConsumptionCent,Blue,
                        Icons.Outlined.ShoppingBag,Modifier.weight(1f)) {
                        onOpenLedgerFilter("CONSUMPTION")
                    }
                }
                Card(
                    Modifier.fillMaxWidth().clickable {onOpenLedgerFilter("REPAYMENT")},
                    shape=RoundedCornerShape(19.dp),
                    colors=CardDefaults.cardColors(
                        containerColor=MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(40.dp).background(Violet.copy(alpha=.13f),
                            RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
                            Icon(Icons.Outlined.CreditCard,null,tint=Violet,
                                modifier=Modifier.size(23.dp))
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("还款",style=MaterialTheme.typography.bodyMedium,
                                fontWeight=FontWeight.SemiBold)
                            Text("信用卡 ${summary.creditRepaymentCent.toYuanText()} · " +
                                "贷款 ${summary.loanRepaymentCent.toYuanText()}",
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines=2)
                        }
                        Text(repayment.toYuanText(),style=MaterialTheme.typography.titleLarge,
                            fontWeight=FontWeight.Bold,maxLines=1)
                        Icon(Icons.Outlined.ChevronRight,null,tint=Violet,
                            modifier=Modifier.size(18.dp))
                    }
                }
            }
        }
        item {
            Text(
                "消费按购物发生时统计，还款按实际偿还时统计；已明确识别的信用卡消费不重复计入总支出。未提供付款账户的账单仍需核对。",
                modifier=Modifier.padding(horizontal=20.dp),
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick=onImport,modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.FileUpload,null,modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导入账单")
                }
                OutlinedButton(onClick=onOpenAnalysis,modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(14.dp)) {Text("收支分析 ›")}
            }
        }
        item {
            SectionHeader("近期流水","所选期间，点击展开详情",
                trailing={TextButton(onClick={onOpenLedgerFilter("ALL")}){Text("查看全部 ›")}})
        }
        if(state.homeRecent.isEmpty()) item {
            EmptyFinanceCard("所选日期暂无账单，可导入或切换日期")
        } else items(state.homeRecent.take(5),key={it.id}) { TransactionCard(it) }
        item {Spacer(Modifier.height(20.dp))}
    }
}

@Composable
private fun SummaryTile(
    title:String,amount:Long,tint:Color,icon:ImageVector,modifier:Modifier,
    onClick:()->Unit
) {
    Card(modifier.clickable(onClick=onClick),shape=RoundedCornerShape(19.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(36.dp).background(tint.copy(alpha=.13f),
                RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center) {
                Icon(icon,null,tint=tint,modifier=Modifier.size(21.dp))
            }
            Text(title,style=MaterialTheme.typography.bodyMedium,
                fontWeight=FontWeight.SemiBold)
            Text(amount.toYuanText(),style=MaterialTheme.typography.titleLarge,
                fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("查看账单",style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight,null,tint=tint,modifier=Modifier.size(16.dp))
            }
        }
    }
}
