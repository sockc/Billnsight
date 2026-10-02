package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toYuanText

@Composable
fun AnalysisScreen(
    state:BillUiState,
    onPlatformChange:(Platform?)->Unit,
    onPrevious:()->Unit,
    onNext:()->Unit,
) {
    val income=CounterpartyAnalysis.incomeSources(
        state.monthlyTransactions,state.linkedReceiptIds)
    val people=CounterpartyAnalysis.people(state.monthlyTransactions)
    val moneyIn=people.filter { it.receivedCount>0 }
        .sortedByDescending { it.receivedCent }
    val moneyOut=people.filter { it.sentCount>0 }
        .sortedByDescending { it.sentCent }
    val credits=state.monthlyTransactions
        .filter { it.flowType==FlowType.CREDIT_REPAYMENT }
        .groupBy {
            val raw=it.counterparty.trim().ifBlank { "未识别信用卡" }
            state.creditCenter.aliases[raw.lowercase()] ?: raw
        }
    val manualCredits=if(state.platformFilter==null)
        state.creditCenter.manual.filter { it.countsAsRepayment }
            .groupBy { it.cardName } else emptyMap()
    val creditNames=(credits.keys+manualCredits.keys).distinct()
        .sortedByDescending { key ->
            credits[key].orEmpty().sumOf { it.amountCent }+
                manualCredits[key].orEmpty().sumOf { it.amountCent }
        }

    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        item {PageTitle("收支分析","看清钱花在哪、谁给你的钱、给谁转了多少")}
        item {MonthHeader(state.month,onPrevious,onNext)}
        item {SourceFilterRow(state.platformFilter,onPlatformChange)}
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp),
                shape=RoundedCornerShape(21.dp),
                colors=CardDefaults.cardColors(
                    containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("消费与转账支出",style=MaterialTheme.typography.titleSmall)
                    Text(state.summary.netExpenseCent.toYuanText(),
                        style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                    Text("收入 "+state.summary.incomeCent.toYuanText()+
                        "  ·  信用卡还款 "+state.summary.creditRepaymentCent.toYuanText(),
                        style=MaterialTheme.typography.bodySmall)
                    Text("提现、充值和还款单列；匹配退款按原消费抵扣",
                        style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {SectionHeader("钱花哪了","支出分类与原始账单")}
        if(state.categories.isEmpty())
            item {EmptyFinanceCard("本月还没有消费或转账支出")}
        items(state.categories,key={it.category}) { category ->
            val detail=state.monthlyTransactions.filter {
                if(category.category=="金融费用")
                    it.flowType==FlowType.LOAN_REPAYMENT &&
                        state.loanDetails[it.id]?.financeCostCent?.let { cost ->cost>0 }==true
                else it.category==category.category &&
                    it.flowType in setOf(FlowType.EXPENSE,FlowType.GIFT_EXPENSE)
            }
            var open by remember(state.month,state.platformFilter) {
                mutableStateOf(false)
            }
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                shape=RoundedCornerShape(17.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth().clickable {open=!open}.padding(14.dp),
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            CategoryBadge(category.category)
                            Text(category.count.toString()+" 笔 · "+
                                if(open)"收起明细 ▲" else "查看明细 ▼",
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(category.amountCent.toYuanText(),
                            style=MaterialTheme.typography.titleMedium,
                            fontWeight=FontWeight.Bold)
                    }
                    if(open) {
                        HorizontalDivider()
                        detail.forEach {tx ->TransactionCard(tx)}
                        if(category.category=="金融费用")
                            Text("仅明确拆分的贷款利息及手续费计为金融费用",
                                modifier=Modifier.padding(14.dp),
                                style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {SectionHeader("收入从哪里来","按付款方统计金额和次数")}
        if(income.isEmpty()) item {EmptyFinanceCard("本月暂无已识别收入")}
        items(income,key={"income_"+it.platform.name+"_"+it.name}) {
            PersonRow(it,"收入",it.receivedCent,it.receivedCount,false)
        }
        item {SectionHeader("谁给我转了钱","每个人的次数、总额与双向交易")}
        if(moneyIn.isEmpty()) item {EmptyFinanceCard("本月暂无转账收入")}
        items(moneyIn,key={"received_"+it.platform.name+"_"+it.name}) {
            PersonRow(it,"转给我",it.receivedCent,it.receivedCount,true)
        }
        item {SectionHeader("我给谁转了钱","每个人的次数、总额与双向交易")}
        if(moneyOut.isEmpty()) item {EmptyFinanceCard("本月暂无转账支出")}
        items(moneyOut,key={"sent_"+it.platform.name+"_"+it.name}) {
            PersonRow(it,"我转给他",it.sentCent,it.sentCount,true)
        }
        item {SectionHeader("信用卡还款","按原始收款方分组，不重复算作消费")}
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                shape=RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("本月还款",style=MaterialTheme.typography.titleMedium)
                    Text(state.summary.creditRepaymentCent.toYuanText(),
                        style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                }
            }
        }
        if(creditNames.isEmpty()) item {EmptyFinanceCard("本月未从已导入账单识别到信用卡还款")}
        items(creditNames,key={it}) { name ->
            val originals=credits[name].orEmpty()
            val entered=manualCredits[name].orEmpty()
            var open by remember(state.month,state.platformFilter) {mutableStateOf(false)}
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                shape=RoundedCornerShape(17.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth().clickable{open=!open}.padding(14.dp),
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(name,fontWeight=FontWeight.SemiBold)
                            Text((originals.size+entered.size).toString()+" 笔 · "+
                                if(open)"收起 ▲" else "查看明细 ▼",
                                style=MaterialTheme.typography.bodySmall)
                        }
                        Text((originals.sumOf{it.amountCent}+
                            entered.sumOf{it.amountCent}).toYuanText(),
                            fontWeight=FontWeight.Bold)
                    }
                    if(open) {
                        originals.forEach {TransactionCard(it)}
                        entered.forEach {
                            Text("原有手动补录 · "+it.amountCent.toYuanText(),
                                modifier=Modifier.padding(14.dp),
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if(state.summary.withdrawalCent>0L || state.summary.loanRepaymentCent>0L)
            item {
                Text("资金提现 "+state.summary.withdrawalCent.toYuanText()+
                    " · 贷款还款 "+state.summary.loanRepaymentCent.toYuanText()+
                    "（均另行统计）",
                    modifier=Modifier.padding(horizontal=20.dp,vertical=10.dp),
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        item {
            Text("同名收付款人如果分属微信和支付宝，会分别显示；未验证身份前不自动合并。",
                modifier=Modifier.padding(horizontal=20.dp,vertical=15.dp),
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PersonRow(
    group:CounterpartySummary,
    caption:String,total:Long,count:Int,showBoth:Boolean,
) {
    var open by remember(group.platform,group.name) {mutableStateOf(false)}
    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
        shape=RoundedCornerShape(17.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable{open=!open}.padding(14.dp),
                horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(group.name,style=MaterialTheme.typography.titleSmall,
                        fontWeight=FontWeight.SemiBold)
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        PlatformBadge(group.platform)
                        Text(caption+" "+count.toString()+" 次 · "+
                            if(open)"收起 ▲" else "查看流水 ▼",
                            style=MaterialTheme.typography.labelSmall)
                    }
                }
                Text(total.toYuanText(),style=MaterialTheme.typography.titleMedium,
                    fontWeight=FontWeight.Bold)
            }
            if(open) {
                HorizontalDivider()
                if(showBoth) {
                    Text("他给我："+group.receivedCount.toString()+" 次 / "+
                        group.receivedCent.toYuanText()+"；我给他："+group.sentCount+
                        " 次 / "+group.sentCent.toYuanText(),
                        modifier=Modifier.padding(12.dp),
                        style=MaterialTheme.typography.bodySmall)
                }
                group.records.forEach {TransactionCard(it)}
            }
        }
    }
}
