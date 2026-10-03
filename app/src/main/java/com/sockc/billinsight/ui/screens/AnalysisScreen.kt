package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.BorderStroke
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
import com.sockc.billinsight.analysis.TransferPerson
import com.sockc.billinsight.analysis.MerchantAnalysis
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toYuanText
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

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
    onOpenTransfer:(String,String,Boolean)->Unit,
    onConfirmMerge:(String)->Unit,
) {
    var section by remember { mutableStateOf("SPENDING") }
    var spendingRank by remember { mutableStateOf("CATEGORY") }
    var transferRank by remember {mutableStateOf("OUT")}
    var mergingName by remember {mutableStateOf<String?>(null)}
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
    val transfers=state.personTransfers
    val transferTotals=state.personTransferTotals
    val accountTotals=state.accountTransferTotals
    val sortedTransfers=when(transferRank){
        "IN"->transfers.sortedByDescending{it.receivedCent}
        "ALL"->transfers.sortedByDescending{it.totalCent}
        else->transfers.sortedByDescending{it.sentCent}
    }
    if(mergingName!=null) AlertDialog(
        onDismissRequest={mergingName=null},
        title={Text("确认同一往来人？")},
        text={Text("仅把微信和支付宝中姓名完全相同的往来合并统计；不会修改原始账单。")},
        confirmButton={Button(onClick={
            mergingName?.let(onConfirmMerge);mergingName=null
        }){Text("确认合并")}},
        dismissButton={TextButton(onClick={mergingName=null}){Text("取消")}}
    )
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
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),
                horizontalArrangement=Arrangement.spacedBy(5.dp)){
                listOf(
                    Triple("SPENDING","支出",state.homeSummary.shoppingConsumptionCent),
                    Triple("INCOME","收入",state.homeSummary.incomeCent),
                    Triple("REPAYMENT","还款",
                        state.homeSummary.creditRepaymentCent+
                        state.homeSummary.loanRepaymentCent),
                    Triple("TRANSFER","转账往来",transferTotals.totalCent)
                ).forEach{(key,label,value)->
                    val selected=section==key
                    Card(
                        Modifier.weight(1f).clickable {section=key},
                        shape=RoundedCornerShape(12.dp),
                        border=if(selected)BorderStroke(1.5.dp,MaterialTheme.colorScheme.primary)
                            else null,
                        colors=CardDefaults.cardColors(
                            containerColor=if(selected)MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant)
                    ){
                        Column(Modifier.fillMaxWidth().padding(horizontal=5.dp,vertical=11.dp),
                            horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.spacedBy(5.dp)){
                            Text(label,maxLines=1,style=MaterialTheme.typography.labelSmall)
                            Text(compactYuan(value),fontWeight=FontWeight.Bold,maxLines=1,
                                style=MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Text("四项口径不同，转账往来为转入＋转出，不能将四项相加。",
                Modifier.padding(start=16.dp,end=16.dp,top=4.dp),
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
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
            item {
                OutlinedButton(onClick=onOpenTrends,
                    modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp)){
                    Text("查看消费趋势 ›")
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
        } else if(section=="REPAYMENT"){
            item {
                OverviewAmountCard("本期还款",
                    state.homeSummary.creditRepaymentCent+state.homeSummary.loanRepaymentCent,
                    "还款按偿还日期计入；在金融中心查看信用卡、贷款和分期")
            }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                    shape=RoundedCornerShape(16.dp)){
                    Column(Modifier.padding(16.dp),
                        verticalArrangement=Arrangement.spacedBy(12.dp)){
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.SpaceBetween){
                            Text("信用卡还款")
                            Text(state.homeSummary.creditRepaymentCent.toYuanText())
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.SpaceBetween){
                            Text("贷款还款")
                            Text(state.homeSummary.loanRepaymentCent.toYuanText())
                        }
                        Button(onClick=onOpenFinance,modifier=Modifier.fillMaxWidth()){
                            Text("查看金融还款明细 ›")
                        }
                    }
                }
            }
        } else if(section=="TRANSFER"){
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),
                    horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf(
                        Triple("转出",transferTotals.sentCent,transferTotals.sentCount),
                        Triple("转入",transferTotals.receivedCent,transferTotals.receivedCount),
                        Triple("往来差额",transferTotals.differenceCent,-1)
                    ).forEach{(title,value,count)->
                        Card(Modifier.weight(1f),shape=RoundedCornerShape(13.dp)){
                            Column(Modifier.padding(9.dp),
                                verticalArrangement=Arrangement.spacedBy(5.dp)){
                                Text(title,style=MaterialTheme.typography.labelSmall)
                                Text(compactYuan(value),fontWeight=FontWeight.Bold,
                                    style=MaterialTheme.typography.bodyMedium,
                                    maxLines=1)
                                Text(if(count>=0)"${count}笔" else "转入－转出",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text("差额只说明资金方向，不代表欠款。",
                    Modifier.padding(horizontal=16.dp),
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    listOf("OUT" to "转出排行","IN" to "转入排行",
                        "ALL" to "往来排行").forEach{(key,label)->
                        FilterChip(selected=transferRank==key,
                            onClick={transferRank=key},label={Text(label)})
                    }
                }
            }
            if(sortedTransfers.isEmpty())item{
                EmptyFinanceCard("所选期间没有已识别的个人转账往来")
            }
            items(sortedTransfers,key={"person_"+it.key}){person->
                val offerMerge=person.platforms.split(',').size==1 &&
                    transfers.any{other->
                        other.key!=person.key && other.name==person.name &&
                        other.platforms!=person.platforms &&
                        setOf(other.platforms,person.platforms)==
                            setOf("WECHAT","ALIPAY")
                    }
                TransferPersonCard(person,state,onOpenTransfer,
                    onMerge=if(offerMerge)({mergingName=person.name}) else null)
            }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp),
                    shape=RoundedCornerShape(16.dp)){
                    Column(Modifier.padding(14.dp),
                        verticalArrangement=Arrangement.spacedBy(7.dp)){
                        Text("账户流转",fontWeight=FontWeight.SemiBold)
                        Text("余额宝、零钱通及本人账户互转与个人往来分开统计",
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.SpaceBetween){
                            Text("转出 ${accountTotals.sentCount} 笔")
                            Text(accountTotals.sentCent.toYuanText())
                        }
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.SpaceBetween){
                            Text("转入 ${accountTotals.receivedCount} 笔")
                            Text(accountTotals.receivedCent.toYuanText())
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
        if(state.periodTransactions.size>=10000) item {
            Text("部分消费与收入明细仅显示最近一万笔；转账往来汇总与排名使用完整账本。",
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

/** One row per person; large histories are fetched only when expanded. */
@Composable
private fun TransferPersonCard(
    person:TransferPerson,
    state:BillUiState,
    onOpen:(String,String,Boolean)->Unit,
    onMerge:(()->Unit)?
){
    var expanded by remember(person.key){mutableStateOf(false)}
    val selected=expanded && state.selectedPersonKey==person.key
    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
        shape=RoundedCornerShape(15.dp)){
        Column{
            Row(Modifier.fillMaxWidth().clickable{
                expanded=!expanded
                if(expanded)onOpen(person.key,"ALL",false)
            }.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                    Text(person.name,fontWeight=FontWeight.SemiBold)
                    Text(person.platforms.split(',').distinct().joinToString("＋"){
                        when(it){"WECHAT"->"微信";"ALIPAY"->"支付宝";else->it}
                    },style=MaterialTheme.typography.labelSmall)
                    Text("转出 ${person.sentCent.toYuanText()} · 转入 "+
                        person.receivedCent.toYuanText(),
                        style=MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment=Alignment.End){
                    Text("${person.count}笔",style=MaterialTheme.typography.labelSmall)
                    Text(if(selected)"收起 ▲" else "明细 ▼",
                        style=MaterialTheme.typography.labelSmall)
                }
            }
            if(onMerge!=null)TextButton(onClick=onMerge,
                modifier=Modifier.padding(start=7.dp)){
                Text("确认同名的微信和支付宝往来为同一人")
            }
            if(selected){
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    listOf("ALL" to "全部","OUT" to "转出","IN" to "转入")
                        .forEach{(key,label)->
                            FilterChip(
                                selected=state.selectedPersonDirection==key,
                                onClick={onOpen(person.key,key,false)},
                                label={Text(label)}
                            )
                        }
                }
                state.personTransferDetails.forEach {tx->TransactionCard(tx)}
                if(state.personTransferLoading)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                if(state.personTransferHasMore && !state.personTransferLoading)
                    TextButton(onClick={
                        onOpen(person.key,state.selectedPersonDirection,true)
                    },modifier=Modifier.fillMaxWidth()){Text("加载更多明细")}
            }
        }
    }
}

private fun compactYuan(amount:Long):String {
    val absAmount=if(amount==Long.MIN_VALUE)Long.MAX_VALUE else kotlin.math.abs(amount)
    return if(absAmount>=1_000_000L)
        String.format(Locale.CHINA,"%.1f万",amount/1_000_000.0)
    else amount.toYuanText()
}
