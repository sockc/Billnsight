package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.analysis.CounterpartyAnalysis
import com.sockc.billinsight.analysis.CounterpartySummary
import com.sockc.billinsight.analysis.MerchantAnalysis
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toYuanText
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun AnalysisScreen(
    state: BillUiState,
    onPlatformChange: (com.sockc.billinsight.model.Platform?) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onSelectPeriod: (String,LocalDate?,LocalDate?) -> Unit,
    onChangeExpenseCategory: (Transaction,String,String) -> Unit,
    onPreviewExpenseCategory: (Transaction) -> Unit,
    onOpenOrganize: () -> Unit,
    onOpenFinance: () -> Unit,
    onOpenTrends: () -> Unit,
) {
    var section by remember { mutableStateOf("SPENDING") }
    var spendingRank by remember { mutableStateOf("CATEGORY") }
    var showTransfers by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Transaction?>(null) }
    editingCategory?.let { tx ->
        ExpenseCategoryDialog(
            transaction=tx,
            preview=if(state.categoryPreviewId==tx.id) state.categoryPreview else null,
            evidence=state.categoryEvidence[tx.id].orEmpty(),
            onDismiss={editingCategory=null},
            onConfirm={item,category,scope->
                onChangeExpenseCategory(item,category,scope)
                editingCategory=null
            }
        )
    }
    val editExpense: (Transaction)->Unit={ tx ->
        editingCategory=tx
        onPreviewExpenseCategory(tx)
    }
    val spendingCategories=state.periodCategories.filter {it.category!="金融费用"}
    val merchantGroups=MerchantAnalysis.groups(
        state.periodTransactions.filter {it.id !in state.entrustedOriginIds},
        state.merchantAliases,state.scanMerchantLabels
    )
    val income=CounterpartyAnalysis.incomeSources(
        state.periodTransactions,state.linkedReceiptIds
    )
    val transfers=CounterpartyAnalysis.people(state.periodTransactions)
    val credit=state.periodTransactions
        .filter {it.flowType==FlowType.CREDIT_REPAYMENT}
        .groupBy {
            val raw=it.counterparty.trim().ifBlank {"未识别信用卡"}
            state.creditCenter.aliases[raw.lowercase()] ?: raw
        }
    val manual=if(state.platformFilter==null)
        state.periodManualRepayments.filter {it.countsAsRepayment}.groupBy {it.cardName}
    else emptyMap()
    val cards=(credit.keys+manual.keys).distinct().sortedByDescending { name ->
        credit[name].orEmpty().sumOf {it.amountCent}+
            manual[name].orEmpty().sumOf {it.amountCent}
    }
    val loans=state.periodTransactions.filter {it.flowType==FlowType.LOAN_REPAYMENT}
        .groupBy {it.counterparty.trim().ifBlank {"未知贷款机构"}}

    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        item {DateScopeTitle("收支分析","所选期间 · 资金去向",state,onSelectMonth,onSelectPeriod)}
        item {SourceFilterRow(state.platformFilter,onPlatformChange)}
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(
                    "SPENDING" to "支出去向",
                    "INCOME" to "收入来源",
                    "TREND" to "趋势"
                ).forEach { (key,label) ->
                    FilterChip(
                        selected=section==key,onClick={section=key},
                        modifier=Modifier.weight(1f),
                        label={Text(label,style=MaterialTheme.typography.labelMedium,
                            maxLines=1)},
                        shape=RoundedCornerShape(12.dp)
                    )
                }
            }
        }
        if(section=="SPENDING") {
            item {
                OverviewAmountCard("本期消费",
                    state.homeSummary.shoppingConsumptionCent,
                    "分类、商家和对应账单统一按真实交易分类统计")
            }
            if(spendingCategories.isNotEmpty()) item {
                CategoryDonutCard(spendingCategories,state.homeSummary.shoppingConsumptionCent)
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=spendingRank=="CATEGORY",
                        onClick={spendingRank="CATEGORY"},
                        label={Text("按分类排行")})
                    FilterChip(selected=spendingRank=="MERCHANT",
                        onClick={spendingRank="MERCHANT"},
                        label={Text("按商户排行")})
                }
            }
            if(spendingRank=="CATEGORY") {
                item {SectionHeader("消费分类排行","点开分类，再点单笔分类即可修改")}
                if(spendingCategories.isEmpty()) item {
                    EmptyFinanceCard("所选期间暂无消费记录")
                }
                items(spendingCategories,key={"category_"+it.category}) { group ->
                    val records=state.periodTransactions.filter {
                        it.id !in state.entrustedOriginIds &&
                        it.category==group.category &&
                            it.flowType in setOf(FlowType.EXPENSE,FlowType.GIFT_EXPENSE)
                    }
                    var expanded by remember(
                        state.homeStart,state.homeEnd,state.platformFilter,group.category
                    ){mutableStateOf(false)}
                    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                        shape=RoundedCornerShape(17.dp)) {
                        Column {
                            Row(Modifier.fillMaxWidth().clickable {expanded=!expanded}
                                .padding(14.dp),
                                horizontalArrangement=Arrangement.SpaceBetween,
                                verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f),
                                    verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                    CategoryBadge(group.category)
                                    Text("${group.count} 笔 · "+
                                        if(expanded)"收起 ▲" else "展开并修改分类 ▼",
                                        style=MaterialTheme.typography.labelSmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(group.amountCent.toYuanText(),fontWeight=FontWeight.Bold,
                                    style=MaterialTheme.typography.titleMedium)
                            }
                            if(group.category=="其他") {
                                TextButton(onClick=onOpenOrganize,
                                    modifier=Modifier.padding(horizontal=9.dp)) {
                                    Text("一键识别未分类账单")
                                }
                            }
                            if(expanded) {
                                HorizontalDivider()
                                records.forEach { tx ->
                                    AnalysisEditableTransaction(tx,editExpense)
                                }
                            }
                        }
                    }
                }
            } else {
                item {SectionHeader("商户消费排行","已合并的商户别名归在同一组")}
                if(merchantGroups.isEmpty()) item {
                    EmptyFinanceCard("所选期间暂无可识别商户")
                }
                items(merchantGroups,key={"merchant_"+it.key}) { merchant ->
                    var expanded by remember(
                        state.homeStart,state.homeEnd,state.platformFilter,merchant.key
                    ){mutableStateOf(false)}
                    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                        shape=RoundedCornerShape(17.dp)) {
                        Column {
                            Row(Modifier.fillMaxWidth().clickable {expanded=!expanded}
                                .padding(14.dp),
                                horizontalArrangement=Arrangement.SpaceBetween,
                                verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(merchant.name,fontWeight=FontWeight.SemiBold)
                                    Text("${merchant.count} 笔 · "+
                                        if(expanded)"收起 ▲" else "展开并修改分类 ▼",
                                        style=MaterialTheme.typography.labelSmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(merchant.amountCent.toYuanText(),fontWeight=FontWeight.Bold)
                            }
                            if(expanded) {
                                HorizontalDivider()
                                merchant.transactions.forEach { tx ->
                                    AnalysisEditableTransaction(tx,editExpense)
                                }
                            }
                        }
                    }
                }
            }
        } else if(section=="INCOME") {
            item {OverviewAmountCard("本期收入",state.homeSummary.incomeCent,
                "按付款人汇总；普通转账收入只在这里计算一次")}
            item {SectionHeader("收入来源","点击付款人查看原始账单")}
            if(income.isEmpty()) item {EmptyFinanceCard("所选期间暂无收入记录")}
            items(income,key={"income_"+it.platform.name+"_"+it.name}) {
                CounterpartyCard(it,showTransfer=false)
            }
            if(transfers.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),
                        shape=RoundedCornerShape(15.dp)) {
                        Column {
                            Row(Modifier.fillMaxWidth().clickable {
                                showTransfers=!showTransfers
                            }.padding(14.dp),verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("查看转账往来",fontWeight=FontWeight.SemiBold)
                                    Text("转入已包含在收入中；转出单独列明，不重复计算",
                                        style=MaterialTheme.typography.labelSmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if(showTransfers)"收起 ▲" else "展开 ▼",
                                    style=MaterialTheme.typography.labelSmall)
                            }
                            if(showTransfers) transfers.forEach {
                                CounterpartyCard(it,showTransfer=true)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                OverviewAmountCard("消费趋势",state.homeSummary.shoppingConsumptionCent,
                    "最近 30 天及历史月份的消费变化")
            }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                    shape=RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(17.dp),
                        verticalArrangement=Arrangement.spacedBy(10.dp)){
                        Text("历史消费趋势",fontWeight=FontWeight.SemiBold)
                        Text("查看近 30 天与最近 12 个月的趋势，"+
                            "点选时段可查看原始账单。",
                            style=MaterialTheme.typography.bodySmall)
                        Button(onClick=onOpenTrends,
                            modifier=Modifier.fillMaxWidth()) {
                            Text("打开消费趋势 ›")
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(13.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("本期还款 "+
                            (state.homeSummary.creditRepaymentCent+
                                state.homeSummary.loanRepaymentCent).toYuanText(),
                            style=MaterialTheme.typography.bodyMedium)
                        Text("信用卡、贷款及分期明细统一在金融中心管理",
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick=onOpenFinance){Text("查看金融 ›")}
                }
            }
        }
        if(state.periodTransactions.size>=10000) item {
            Text("明细最多显示最近一万笔；统计金额按完整日期范围计算。",
                modifier=Modifier.padding(16.dp),
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {Spacer(Modifier.height(20.dp))}
    }
}

@Composable
private fun OverviewAmountCard(title:String,amount:Long,subtitle:String) {
    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp),
        shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.cardColors(
            containerColor=MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(title,style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onPrimaryContainer)
            Text(amount.toYuanText(),style=MaterialTheme.typography.headlineMedium,
                fontWeight=FontWeight.Bold,
                color=MaterialTheme.colorScheme.onPrimaryContainer)
            Text(subtitle,style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun AnalysisEditableTransaction(tx:Transaction,onEdit:(Transaction)->Unit) {
    TransactionCard(
        tx,
        onCategoryClick=if(tx.flowType==FlowType.EXPENSE) ({onEdit(tx)}) else null,
        trailing={
            if(tx.flowType==FlowType.EXPENSE) TextButton(onClick={onEdit(tx)}) {
                Text("修改分类",style=MaterialTheme.typography.labelMedium)
            }
        }
    )
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
