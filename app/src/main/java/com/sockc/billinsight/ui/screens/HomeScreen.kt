package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.util.toYuanText
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun HomeScreen(
    state: BillUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onImport: () -> Unit,
    onPlatformChange: (Platform?) -> Unit,
    onReviewPending: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onRecheckCredit: () -> Unit,
) {
    val current=state.summary.netExpenseCent
    val previous=state.previousSummary.netExpenseCent
    val delta=current-previous
    val percentage=if(previous>0L) delta*100.0/previous else null
    var expandedSummary by remember(state.month,state.platformFilter) {
        mutableStateOf<String?>(null)
    }
    var creditDetailsOpen by remember(state.month,state.platformFilter) {
        mutableStateOf(false)
    }
    val creditTransactions = state.monthlyTransactions.filter {
        it.flowType == FlowType.CREDIT_REPAYMENT
    }
    val income=state.monthlyTransactions.filter { tx ->
        tx.flowType in setOf(
            FlowType.INCOME,FlowType.GIFT_INCOME,FlowType.BUSINESS_INCOME,
            FlowType.LOAN_RECOVERY,FlowType.LOAN_DISBURSEMENT,FlowType.REFUND
        ) || (tx.directionText.contains("收入") && tx.flowType==FlowType.TRANSFER)
    }
    val expense=state.monthlyTransactions.filter { tx ->
        tx.flowType in setOf(
            FlowType.EXPENSE,FlowType.GIFT_EXPENSE,FlowType.BUSINESS_EXPENSE,
            FlowType.LOAN_OUT,FlowType.CREDIT_REPAYMENT,FlowType.LOAN_REPAYMENT
        ) || (tx.directionText.contains("支出") && tx.flowType==FlowType.TRANSFER)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement=Arrangement.spacedBy(5.dp)
    ) {
        item { PageTitle("账单洞察","每一笔收支，都有迹可循") }
        item { MonthHeader(state.month,onPrevious,onNext) }
        item { SourceFilterRow(state.platformFilter,onPlatformChange) }

        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=7.dp),
                shape=RoundedCornerShape(24.dp),
                colors=CardDefaults.cardColors(
                    containerColor=MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(21.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text("${platformLabel(state.platformFilter)} · 本月实际净消费",
                        color=MaterialTheme.colorScheme.onPrimaryContainer,
                        style=MaterialTheme.typography.labelLarge)
                    Text(current.toYuanText(),
                        color=MaterialTheme.colorScheme.onPrimaryContainer,
                        style=MaterialTheme.typography.headlineLarge,
                        fontWeight=FontWeight.Bold)
                    val comparison=when {
                        previous==0L && current==0L -> "本月和上月暂无消费"
                        previous==0L -> "上月暂无可对比数据"
                        delta>0 -> "比上月增加 ${abs(delta).toYuanText()}（+${"%.1f".format(Locale.US,percentage)}%）"
                        delta<0 -> "比上月减少 ${abs(delta).toYuanText()}（${"%.1f".format(Locale.US,percentage)}%）"
                        else -> "与上月持平"
                    }
                    Text(comparison,
                        style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.83f))
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("原始消费  ${state.summary.expenseCent.toYuanText()}",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("退款/分摊  ${(state.summary.linkedRefundCent+
                            state.summary.linkedShareCent).toYuanText()}",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    if(state.summary.loanFinanceCostCent>0L) {
                        Text("净消费已包含明确拆分的贷款利息和手续费",
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.78f))
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    HomeMetricCard(
                        label="个人收入",amount=state.summary.incomeCent,
                        hint="不含贷款到账",tint=Color(0xFF27987A),modifier=Modifier.weight(1f))
                    HomeMetricCard(
                        label="支出记录合计",amount=state.summary.cashOutflowCent,
                        hint="含还款，不等于净消费",
                        tint=Color(0xFF5383BD),modifier=Modifier.weight(1f))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    HomeMetricCard(
                        label="信用卡还款",amount=state.summary.creditRepaymentCent,
                        hint="${state.summary.creditRepaymentCount} 笔 · 点击${if (creditDetailsOpen) "收起" else "核对"}",
                        tint=Color(0xFF9873C9),modifier=Modifier.weight(1f),
                        onClick={creditDetailsOpen=!creditDetailsOpen})
                    HomeMetricCard(
                        label="贷款还款",amount=state.summary.loanRepaymentCent,
                        hint="${state.summary.loanRepaymentCount} 笔 · 待拆分 ${state.summary.loanUnallocatedCent.toYuanText()}",
                        tint=Color(0xFFCB9444),modifier=Modifier.weight(1f))
                }
            }
        }

        if (creditDetailsOpen) {
            item {
                SectionHeader(
                    "信用卡还款明细",
                    "${state.month.year}年${state.month.monthValue}月 · ${platformLabel(state.platformFilter)} · " +
                        "${creditTransactions.size} 笔"
                )
            }
            if (creditTransactions.isEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                        shape=RoundedCornerShape(18.dp),
                        colors=CardDefaults.cardColors(
                            containerColor=MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(Modifier.padding(16.dp),
                            verticalArrangement=Arrangement.spacedBy(9.dp)) {
                            Text("当前月份及账单来源暂无已识别的信用卡还款。",
                                style=MaterialTheme.typography.bodyMedium)
                            Text(
                                "可重新检查旧微信、支付宝账单。如果信用卡由银行卡直接自动扣款，" +
                                    "而未经过微信或支付宝，则导出的账单不包含这笔还款。",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.platformFilter != null) {
                                TextButton(onClick={onPlatformChange(null)}) {
                                    Text("切换为全部账单来源")
                                }
                            }
                            OutlinedButton(
                                onClick=onRecheckCredit,
                                enabled=!state.isLoading,
                                modifier=Modifier.fillMaxWidth()
                            ) { Text("重新识别旧账单") }
                        }
                    }
                }
            } else {
                items(
                    DailyLedger.group(creditTransactions),
                    key={ "credit_${it.first}" }
                ) { (day,transactions) ->
                    DailyLedgerHeader(day,transactions)
                    transactions.forEach { TransactionCard(it) }
                }
                item {
                    TextButton(
                        onClick=onRecheckCredit,enabled=!state.isLoading,
                        modifier=Modifier.fillMaxWidth()
                    ) { Text("检查是否还有漏识别的旧记录") }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick=onImport,modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(15.dp)) {
                    Icon(Icons.Outlined.AddCircleOutline,contentDescription=null)
                    Spacer(Modifier.width(6.dp))
                    Text("导入账单")
                }
                OutlinedButton(onClick=onOpenAnalysis,modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(15.dp)) {
                    Text("分析明细")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.ArrowForward,contentDescription=null)
                }
            }
        }
        if(state.pendingCount>0) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal=16.dp),
                    shape=RoundedCornerShape(17.dp),
                    colors=CardDefaults.cardColors(
                        containerColor=MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=9.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${state.pendingCount} 笔待确认交易",
                                fontWeight=FontWeight.SemiBold,
                                color=MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("核对后让收支统计更准确",
                                color=MaterialTheme.colorScheme.onSecondaryContainer,
                                style=MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick=onReviewPending){Text("去处理")}
                    }
                }
            }
        }

        item { SectionHeader("近 7 天消费","按所选月份统计，每天金额直接显示") }
        item { SevenDayTrend(state) }
        item {
            SectionHeader("钱主要花在哪里","分类消费金额与占比",
                trailing={TextButton(onClick=onOpenAnalysis){Text("全部")}})
        }
        if(state.categories.isEmpty()) {
            item { EmptyFinanceCard("还没有当前筛选条件下的消费记录。") }
        } else {
            items(state.categories.take(5),key={it.category}) { item ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=3.dp),
                    colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                    shape=RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(horizontal=15.dp,vertical=12.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            CategoryBadge(item.category)
                            Text("  ${item.count} 笔",modifier=Modifier.weight(1f),
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.amountCent.toYuanText(),fontWeight=FontWeight.Bold,
                                style=MaterialTheme.typography.titleSmall)
                        }
                        Box(Modifier.fillMaxWidth().padding(top=10.dp).height(5.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(5.dp))) {
                            Box(Modifier.fillMaxWidth(
                                (item.amountCent.toFloat()/max(1L,state.summary.expenseCent).toFloat())
                                    .coerceIn(0.02f,1f)
                            ).height(5.dp).background(categoryColor(item.category),
                                RoundedCornerShape(5.dp)))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={
                    expandedSummary=if(expandedSummary=="expense") null else "expense"
                }) { Text(if(expandedSummary=="expense") "收起支出 ▲" else "全部支出 ▼") }
                TextButton(onClick={
                    expandedSummary=if(expandedSummary=="income") null else "income"
                }) { Text(if(expandedSummary=="income") "收起收入 ▲" else "全部收入 ▼") }
            }
        }
        if(expandedSummary!=null) {
            val entries=if(expandedSummary=="expense") expense else income
            item {
                SectionHeader(
                    if(expandedSummary=="expense") "本月全部付款流水" else "本月全部收款流水",
                    "包括还款和账户划转，按原始性质分别统计"
                )
            }
            if(entries.isEmpty()) item { EmptyFinanceCard("本月暂无此类记录") }
            else items(DailyLedger.group(entries),key={it.first.toString()}) { (day,records) ->
                DailyLedgerHeader(day,records)
                records.forEach { TransactionCard(it) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HomeMetricCard(
    label:String,amount:Long,hint:String,tint:Color,modifier:Modifier=Modifier,
    onClick:(()->Unit)?=null,
) {
    Card(
        modifier=if (onClick==null) modifier else modifier.clickable(onClick=onClick),
        shape=RoundedCornerShape(19.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Box(Modifier.width(30.dp).height(4.dp)
                .background(tint,RoundedCornerShape(4.dp)))
            Text(label,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount.toYuanText(),style=MaterialTheme.typography.titleLarge,
                color=MaterialTheme.colorScheme.onSurface,
                fontWeight=FontWeight.Bold,maxLines=1)
            Text(hint,style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
        }
    }
}

@Composable
private fun SevenDayTrend(state:BillUiState) {
    val today=YearMonth.now()
    val end=if(state.month==today) java.time.LocalDate.now().dayOfMonth
            else state.month.lengthOfMonth()
    val days=(max(1,end-6)..end).toList()
    val data=state.dailyTotals.associateBy { it.dayOfMonth }
    val maxSpend=days.maxOfOrNull { data[it]?.amountCent?:0L }?.coerceAtLeast(1L)?:1L
    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
        shape=RoundedCornerShape(19.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=17.dp),
            horizontalArrangement=Arrangement.spacedBy(2.dp),
            verticalAlignment=Alignment.Bottom) {
            days.forEach { day ->
                val cents=data[day]?.amountCent?:0L
                val fraction=cents.toFloat()/maxSpend.toFloat()
                Column(Modifier.weight(1f),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(
                        if(cents==0L) "0" else if(cents>=100000L)
                            "${"%.1f".format(Locale.US,cents/100000.0)}k"
                        else "${cents/100L}",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines=1
                    )
                    Box(Modifier.height(70.dp),contentAlignment=Alignment.BottomCenter) {
                        Box(Modifier.width(20.dp)
                            .height((6+fraction*64).dp)
                            .background(
                                if(day==end) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha=0.37f),
                                RoundedCornerShape(topStart=6.dp,topEnd=6.dp)
                            ))
                    }
                    Text("${day}日",style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun EmptyFinanceCard(message:String) {
    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp),
        shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Text(message,modifier=Modifier.padding(20.dp),
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            style=MaterialTheme.typography.bodyMedium)
    }
}
