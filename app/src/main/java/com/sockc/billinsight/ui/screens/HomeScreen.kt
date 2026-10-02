package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.util.toYuanText

@Composable
fun HomeScreen(
    state:BillUiState,
    onPrevious:()->Unit,onNext:()->Unit,
    onImport:()->Unit,onPlatformChange:(Platform?)->Unit,
    onOpenAnalysis:()->Unit,
) {
    val days=state.trendDays.takeLast(7)
    val peak=days.maxOfOrNull { it.expenseCent }?.coerceAtLeast(1L)?:1L
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        item { PageTitle("账单洞察","导入账单，自动看清钱花哪了、从哪里来") }
        item { MonthHeader(state.month,onPrevious,onNext) }
        item { SourceFilterRow(state.platformFilter,onPlatformChange) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                colors=CardDefaults.cardColors(
                    containerColor=MaterialTheme.colorScheme.primaryContainer),
                shape=RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("本月消费与转账支出",style=MaterialTheme.typography.titleMedium,
                        color=MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(state.summary.netExpenseCent.toYuanText(),
                        style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,
                        color=MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("已扣除匹配退款与分摊；提现及信用卡还款不重复算消费",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                AmountCard("本月收入",state.summary.incomeCent,
                    "扫码及转账收入",Modifier.weight(1f))
                AmountCard("信用卡还款",state.summary.creditRepaymentCent,
                    state.summary.creditRepaymentCount.toString()+" 笔",Modifier.weight(1f))
            }
        }
        if(state.summary.withdrawalCent>0L || state.summary.loanRepaymentCent>0L) item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(17.dp)) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(state.summary.withdrawalCent>0L)
                        Text("资金提现  "+state.summary.withdrawalCent.toYuanText()+
                            " · 不计消费",style=MaterialTheme.typography.bodySmall)
                    if(state.summary.loanRepaymentCent>0L)
                        Text("贷款还款  "+state.summary.loanRepaymentCent.toYuanText()+
                            " · 单独统计",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Button(onClick=onImport,modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(14.dp)){Text("导入账单")}
                OutlinedButton(onClick=onOpenAnalysis,modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(14.dp)){Text("收支分析")}
            }
        }
        item { SectionHeader("近 7 天支出","每天的消费与转账支出") }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(13.dp),
                    verticalAlignment=Alignment.Bottom,
                    horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                    days.forEach { day ->
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text((day.expenseCent/100L).toString(),
                                style=MaterialTheme.typography.labelSmall,maxLines=1)
                            Box(Modifier.height(68.dp),contentAlignment=Alignment.BottomCenter) {
                                Box(Modifier.width(22.dp)
                                    .height((5f+60f*day.expenseCent.toFloat()/peak.toFloat()).dp)
                                    .background(MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(topStart=6.dp,topEnd=6.dp)))
                            }
                            Text(day.label,style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item { SectionHeader("最近流水","点击查看完整原始账单",
            trailing={TextButton(onClick=onOpenAnalysis){Text("分析 ›")}}) }
        if(state.monthlyTransactions.isEmpty())
            item {EmptyFinanceCard("尚无本月账单，导入微信或支付宝账单即可查看分析")}
        else items(state.monthlyTransactions.take(6),key={it.id}) {
            TransactionCard(it)
        }
        item {Spacer(Modifier.height(24.dp))}
    }
}

@Composable
private fun AmountCard(label:String,amount:Long,hint:String,modifier:Modifier) {
    Card(modifier,shape=RoundedCornerShape(19.dp)) {
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(label,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount.toYuanText(),style=MaterialTheme.typography.titleLarge,
                fontWeight=FontWeight.Bold,maxLines=1)
            Text(hint,style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
