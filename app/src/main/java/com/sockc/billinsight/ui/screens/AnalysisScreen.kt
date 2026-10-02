package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.sockc.billinsight.analysis.CounterpartyAnalysis
import com.sockc.billinsight.analysis.CounterpartySummary
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toYuanText

@Composable
fun AnalysisScreen(
    state:BillUiState,
    onPlatformChange:(com.sockc.billinsight.model.Platform?)->Unit,
    onPrevious:()->Unit,
    onNext:()->Unit,
) {
    var selected by remember { mutableStateOf("EXPENSE") }
    val income=CounterpartyAnalysis.incomeSources(
        state.monthlyTransactions,state.linkedReceiptIds)
    val people=CounterpartyAnalysis.people(state.monthlyTransactions)
    val received=people.sumOf { it.receivedCent }
    val sent=people.sumOf { it.sentCent }
    val transferred=received+sent
    val credit=state.monthlyTransactions
        .filter { it.flowType==FlowType.CREDIT_REPAYMENT }
        .groupBy {
            val raw=it.counterparty.trim().ifBlank { "未识别信用卡" }
            state.creditCenter.aliases[raw.lowercase()] ?: raw
        }
    val manual=if(state.platformFilter==null)
        state.creditCenter.manual.filter { it.countsAsRepayment }
            .groupBy { it.cardName } else emptyMap()
    val creditNames=(credit.keys+manual.keys).distinct().sortedByDescending { key ->
        credit[key].orEmpty().sumOf { it.amountCent }+
            manual[key].orEmpty().sumOf { it.amountCent }
    }
    val loans=state.monthlyTransactions.filter {
        it.flowType==FlowType.LOAN_REPAYMENT
    }.groupBy { it.counterparty.trim().ifBlank { "未知贷款机构" } }

    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        item { PageTitle("收支分析","选择分类，一眼看清资金去向和来源") }
        item { MonthHeader(state.month,onPrevious,onNext) }
        item { SourceFilterRow(state.platformFilter,onPlatformChange) }
        item {
            Column(Modifier.padding(horizontal=16.dp,vertical=6.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    AnalysisModeCard("支出",state.summary.netExpenseCent,
                        selected=="EXPENSE",Modifier.weight(1f)){selected="EXPENSE"}
                    AnalysisModeCard("收入",state.summary.incomeCent,
                        selected=="INCOME",Modifier.weight(1f)){selected="INCOME"}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    AnalysisModeCard("资金往来",transferred,
                        selected=="TRANSFER",Modifier.weight(1f)){selected="TRANSFER"}
                    AnalysisModeCard("还款",
                        state.summary.creditRepaymentCent+state.summary.loanRepaymentCent,
                        selected=="REPAYMENT",Modifier.weight(1f)){selected="REPAYMENT"}
                }
            }
        }

        if(selected=="EXPENSE") {
            item { CategoryDonutCard(state.categories,state.summary.netExpenseCent) }
            item { SectionHeader("分类排行","点击展开对应账单") }
            if(state.categories.isEmpty())
                item { EmptyFinanceCard("本月暂无支出记录") }
            items(state.categories,key={it.category}) { category ->
                val detail=state.monthlyTransactions.filter {
                    if(category.category=="金融费用")
                        it.flowType==FlowType.LOAN_REPAYMENT &&
                            (state.loanDetails[it.id]?.financeCostCent?:0)>0
                    else it.category==category.category &&
                        it.flowType in setOf(FlowType.EXPENSE,FlowType.GIFT_EXPENSE)
                }
                var open by remember(state.month,state.platformFilter,category.category) {
                    mutableStateOf(false)
                }
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                    shape=RoundedCornerShape(17.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable {open=!open}.padding(15.dp),
                            horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f),
                                verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                CategoryBadge(category.category)
                                Text(category.count.toString()+" 笔 · "+
                                    if(open)"收起 ▲" else "查看流水 ▼",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(category.amountCent.toYuanText(),
                                fontWeight=FontWeight.Bold,
                                style=MaterialTheme.typography.titleMedium)
                        }
                        if(open) {
                            HorizontalDivider()
                            detail.forEach { TransactionCard(it) }
                            if(category.category=="金融费用") Text(
                                "金融费用只计明确拆分的贷款利息及手续费",
                                modifier=Modifier.padding(13.dp),
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else if(selected=="INCOME") {
            item { SectionHeader("收入","按付款人统计总额、次数和明细") }
            if(income.isEmpty()) item {EmptyFinanceCard("本月暂无收入记录")}
            items(income,key={"income_"+it.platform.name+"_"+it.name}) {
                CounterpartyCard(it,showTransfer=false)
            }
        } else if(selected=="TRANSFER") {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                    shape=RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(15.dp),
                        verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Text("资金往来",fontWeight=FontWeight.Bold,
                            style=MaterialTheme.typography.titleMedium)
                        Text("转入 "+received.toYuanText()+
                            " · 转出 "+sent.toYuanText(),
                            style=MaterialTheme.typography.bodyMedium)
                        Text("仅普通转账；不包含扫码购物、提现和信用卡还款",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if(people.isEmpty()) item {EmptyFinanceCard("本月暂无转账往来")}
            items(people,key={"transfer_"+it.platform.name+"_"+it.name}) {
                CounterpartyCard(it,showTransfer=true)
            }
            item {
                Text("同名的微信和支付宝账户暂时分开统计，避免误把不同的人合并。",
                    modifier=Modifier.padding(horizontal=20.dp,vertical=10.dp),
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if(selected=="REPAYMENT") {
            item {SectionHeader("还款","信用卡与贷款独立统计，不重复计入消费")}
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                    shape=RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp),
                        verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Text("本月还款合计",style=MaterialTheme.typography.titleSmall)
                        Text((state.summary.creditRepaymentCent+
                            state.summary.loanRepaymentCent).toYuanText(),
                            style=MaterialTheme.typography.headlineMedium,
                            fontWeight=FontWeight.Bold)
                        Text("信用卡 "+state.summary.creditRepaymentCent.toYuanText()+
                            "（"+state.summary.creditRepaymentCount+" 笔）",
                            style=MaterialTheme.typography.bodySmall)
                        Text("贷款 "+state.summary.loanRepaymentCent.toYuanText()+
                            "（"+state.summary.loanRepaymentCount+" 笔）",
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SectionHeader("信用卡","按收款机构查看还款笔数和金额") }
            if(creditNames.isEmpty())
                item {EmptyFinanceCard("本月暂无已识别的信用卡还款")}
            items(creditNames,key={"credit_"+it}) { name ->
                val originals=credit[name].orEmpty()
                val supplements=manual[name].orEmpty()
                var open by remember(state.month,state.platformFilter,name) {
                    mutableStateOf(false)
                }
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                    shape=RoundedCornerShape(17.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable {open=!open}.padding(14.dp),
                            horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(name,fontWeight=FontWeight.SemiBold)
                                Text((originals.size+supplements.size).toString()+" 笔 · "+
                                    if(open)"收起 ▲" else "查看流水 ▼",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            Text((originals.sumOf {it.amountCent}+
                                supplements.sumOf {it.amountCent}).toYuanText(),
                                fontWeight=FontWeight.Bold)
                        }
                        if(open) {
                            originals.forEach {TransactionCard(it)}
                            supplements.forEach {
                                Text("原有手动补录 · "+it.amountCent.toYuanText(),
                                    modifier=Modifier.padding(14.dp),
                                    style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {SectionHeader("贷款","按机构汇总还款，已拆分的本金利息保留")}
            if(loans.isEmpty()) item {EmptyFinanceCard("本月暂无贷款还款")}
            items(loans.toList(),key={"loan_"+it.first}) { (name,transactions) ->
                var open by remember(state.month,state.platformFilter,name) {
                    mutableStateOf(false)
                }
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                    shape=RoundedCornerShape(17.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable {open=!open}.padding(14.dp),
                            horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(name,fontWeight=FontWeight.SemiBold)
                                Text(transactions.size.toString()+" 笔 · "+
                                    if(open)"收起 ▲" else "查看流水 ▼",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            Text(transactions.sumOf {it.amountCent}.toYuanText(),
                                fontWeight=FontWeight.Bold)
                        }
                        if(open) transactions.forEach {TransactionCard(it)}
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisModeCard(
    title:String,amount:Long,selected:Boolean,modifier:Modifier,
    onClick:()->Unit,
) {
    Card(
        modifier.clickable(onClick=onClick),
        colors=CardDefaults.cardColors(
            containerColor=if(selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
        ),
        shape=RoundedCornerShape(19.dp)
    ) {
        Column(Modifier.padding(15.dp),
            verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall,
                fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium,
                color=if(selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface)
            Text(amount.toYuanText(),
                style=MaterialTheme.typography.titleLarge,
                fontWeight=FontWeight.Bold,maxLines=1)
        }
    }
}

@Composable
private fun CounterpartyCard(group:CounterpartySummary,showTransfer:Boolean) {
    var open by remember(group.platform,group.name,showTransfer) {
        mutableStateOf(false)
    }
    val total=if(showTransfer) group.receivedCent+group.sentCent
        else group.receivedCent
    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
        shape=RoundedCornerShape(17.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable {open=!open}.padding(15.dp),
                horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f),
                    verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(group.name,fontWeight=FontWeight.SemiBold,
                        style=MaterialTheme.typography.titleSmall)
                    PlatformBadge(group.platform)
                    if(showTransfer) {
                        Text("转入 "+group.receivedCount+" 次 / "+
                            group.receivedCent.toYuanText(),
                            style=MaterialTheme.typography.bodySmall)
                        Text("转出 "+group.sentCount+" 次 / "+
                            group.sentCent.toYuanText(),
                            style=MaterialTheme.typography.bodySmall)
                    } else {
                        Text(group.receivedCount.toString()+" 笔收入",
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
                Column {
                    Text(total.toYuanText(),fontWeight=FontWeight.Bold,
                        style=MaterialTheme.typography.titleMedium)
                    Text(if(open)"收起 ▲" else "明细 ▼",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if(open) {
                HorizontalDivider()
                if(showTransfer) Text(
                    "往来差额（转入－转出）："+group.differenceCent.toYuanText(),
                    modifier=Modifier.padding(horizontal=15.dp,vertical=9.dp),
                    style=MaterialTheme.typography.bodySmall)
                group.records.forEach {TransactionCard(it)}
            }
        }
    }
}
