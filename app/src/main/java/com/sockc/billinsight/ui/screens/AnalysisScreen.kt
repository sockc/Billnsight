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
    onReclassify: () -> Unit,
) {
    var section by remember { mutableStateOf("SPENDING") }
    var spendingRank by remember { mutableStateOf("CATEGORY") }
    var showTransfers by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Transaction?>(null) }
    var confirmReclassify by remember { mutableStateOf(false) }
    if (confirmReclassify) {
        AlertDialog(
            onDismissRequest = { confirmReclassify = false },
            title = { Text("自动识别其他消费？") },
            text = { Text("将检查全账本尚未人工分类的“其他”消费，优先使用你保存的商户规则。不会更改手动分类、还款或资金流转。") },
            confirmButton = {
                Button(onClick = {
                    confirmReclassify = false
                    onReclassify()
                }) { Text("开始识别") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReclassify = false }) { Text("取消") }
            }
        )
    }
    editingCategory?.let { tx ->
        ExpenseCategoryDialog(
            transaction=tx,
            preview=if(state.categoryPreviewId==tx.id) state.categoryPreview else null,
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
        state.periodTransactions,state.merchantAliases,state.scanMerchantLabels
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
                    "REPAYMENT" to "还款分析"
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
                                TextButton(onClick={confirmReclassify=true},
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
                OverviewAmountCard("本期还款",
                    state.homeSummary.creditRepaymentCent+
                        state.homeSummary.loanRepaymentCent,
                    "信用卡 ${state.homeSummary.creditRepaymentCent.toYuanText()} · "+
                    "贷款 ${state.homeSummary.loanRepaymentCent.toYuanText()}")
            }
            item {SectionHeader("信用卡还款","按银行与卡片统计，还款不重复算消费")}
            if(cards.isEmpty()) item {EmptyFinanceCard("所选期间暂无信用卡还款")}
            items(cards,key={"credit_"+it}) { name ->
                val originals=credit[name].orEmpty()
                val supplements=manual[name].orEmpty()
                var expanded by remember(
                    state.homeStart,state.homeEnd,state.platformFilter,name
                ){mutableStateOf(false)}
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                    shape=RoundedCornerShape(17.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable {expanded=!expanded}
                            .padding(14.dp),
                            horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(name,fontWeight=FontWeight.SemiBold)
                                Text("${originals.size+supplements.size} 笔 · "+
                                    if(expanded)"收起 ▲" else "查看还款 ▼",
                                    style=MaterialTheme.typography.labelSmall)
                            }
                            Text((originals.sumOf {it.amountCent}+
                                supplements.sumOf {it.amountCent}).toYuanText(),
                                fontWeight=FontWeight.Bold)
                        }
                        if(expanded) {
                            originals.forEach {TransactionCard(it)}
                            supplements.forEach {
                                Text("手动补录 · "+it.amountCent.toYuanText(),
                                    modifier=Modifier.padding(14.dp),
                                    style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {SectionHeader("贷款还款",
                "本金和利息拆分以已确认的还款明细为准")}
            if(loans.isEmpty()) item {EmptyFinanceCard("所选期间暂无贷款还款")}
            items(loans.toList(),key={"loan_"+it.first}) { (name,records) ->
                var expanded by remember(
                    state.homeStart,state.homeEnd,state.platformFilter,name
                ){mutableStateOf(false)}
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                    shape=RoundedCornerShape(17.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable {expanded=!expanded}
                            .padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(name,fontWeight=FontWeight.SemiBold)
                                Text("${records.size} 笔 · "+
                                    if(expanded)"收起 ▲" else "查看还款 ▼",
                                    style=MaterialTheme.typography.labelSmall)
                            }
                            Text(records.sumOf {it.amountCent}.toYuanText(),
                                fontWeight=FontWeight.Bold)
                        }
                        if(expanded) records.forEach {TransactionCard(it)}
                    }
                }
            }
            if(state.homeSummary.loanFinanceCostCent>0) item {
                Text("其中已确认利息及手续费 "+
                    state.homeSummary.loanFinanceCostCent.toYuanText(),
                    modifier=Modifier.padding(horizontal=20.dp,vertical=8.dp),
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
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
        onCategoryClick={onEdit(tx)},
        trailing={
            TextButton(onClick={onEdit(tx)}) {
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
