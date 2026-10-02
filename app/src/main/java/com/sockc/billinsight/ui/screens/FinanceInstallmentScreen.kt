package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.*
import com.sockc.billinsight.util.toYuanText
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun displayPlatform(p: Platform)=when(p) {
    Platform.WECHAT->"微信"; Platform.ALIPAY->"支付宝"
    Platform.JD->"京东"; Platform.DOUYIN->"抖音"
    Platform.MEITUAN->"美团"; Platform.BANK->"银行"
    Platform.UNKNOWN->"其他"
}
private fun dateTime(time:Long)=Instant.ofEpochMilli(time)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

private fun centsInput(value:Long?):String =
    value?.let {BigDecimal.valueOf(it,2).stripTrailingZeros().toPlainString()} ?: ""
private fun parseCents(input:String):Long?=runCatching {
    input.trim().takeIf {it.isNotBlank()}?.let {
        BigDecimal(it).movePointRight(2)
            .setScale(0,RoundingMode.UNNECESSARY).longValueExact()
    }
}.getOrNull()

/** From the ledger or the source-bill picker: only the person's name is mandatory for advance. */
@Composable
fun FinanceInstallmentEditor(
    source:Transaction,
    existing:FinancePlan?=null,
    initialKind:FinancePlanKind=FinancePlanKind.OWN,
    onDismiss:()->Unit,
    onCreate:(Long,FinancePlanKind,String,String,Long?,Int?,Int?)->Unit,
    onUpdate:((Long,String,String,Long?,Int?,Int?)->Unit)?=null,
) {
    var kind by remember(source.id,existing?.id){mutableStateOf(existing?.kind?:initialKind)}
    var beneficiary by remember(source.id,existing?.id){
        mutableStateOf(existing?.beneficiary.orEmpty())
    }
    var title by remember(source.id,existing?.id) {
        mutableStateOf(existing?.title ?: source.description.trim()
            .takeUnless {it.isBlank() || it.length>70}
            ?: source.tradeType.trim().takeIf {it.isNotBlank()}?.take(70)
            ?: "分期账单")
    }
    var total by remember(source.id,existing?.id) {
        mutableStateOf(centsInput(existing?.totalCent))
    }
    var terms by remember(source.id,existing?.id) {
        mutableStateOf((existing?.termCount?:FinanceReference.totalTerms(source))
            ?.toString().orEmpty())
    }
    var dueDay by remember(source.id,existing?.id) {
        mutableStateOf(existing?.dueDay?.toString().orEmpty())
    }
    var advanced by remember(source.id,existing?.id) {
        mutableStateOf(existing!=null)
    }
    val amount= parseCents(total)
    val valid=title.trim().isNotBlank() &&
        (kind!=FinancePlanKind.ADVANCE || beneficiary.trim().isNotBlank()) &&
        (total.isBlank() || (amount!=null && amount>0)) &&
        (terms.isBlank() || terms.toIntOrNull()?.let {it in 2..360} == true) &&
        (dueDay.isBlank() || dueDay.toIntOrNull()?.let {it in 1..31} == true)
    AlertDialog(
        onDismissRequest=onDismiss,shape=RoundedCornerShape(21.dp),
        title={Text(if(existing==null)"从原账单创建分期" else "编辑分期卡片")},
        text={
            Column(Modifier.heightIn(max=500.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text("${displayPlatform(source.platform)} · ${source.counterparty} · "+
                    source.amountCent.toYuanText(),
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                if(existing==null) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=kind==FinancePlanKind.OWN,
                        onClick={kind=FinancePlanKind.OWN},label={Text("我的分期")})
                    FilterChip(selected=kind==FinancePlanKind.ADVANCE,
                        onClick={kind=FinancePlanKind.ADVANCE},label={Text("代付分期")})
                }
                if(kind==FinancePlanKind.ADVANCE) {
                    OutlinedTextField(
                        value=beneficiary,onValueChange={beneficiary=it.take(60)},
                        label={Text("对方姓名或备注 *")},singleLine=true,
                        modifier=Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value=title,onValueChange={title=it.take(90)},
                    label={Text("分期名称")},singleLine=true,
                    modifier=Modifier.fillMaxWidth()
                )
                TextButton(onClick={advanced=!advanced},
                    contentPadding=PaddingValues(horizontal=0.dp,vertical=0.dp)) {
                    Text(if(advanced)"收起可选信息 ▲" else "补充总额、期数和还款日 ▼")
                }
                if(advanced) {
                    OutlinedTextField(
                        value=total,onValueChange={total=it.take(18)},
                        label={Text("分期总应还（元，可留空）")},
                        supportingText={Text("账单只有一期金额时不要填成总额")},
                        singleLine=true,modifier=Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value=terms,onValueChange={terms=it.filter(Char::isDigit).take(3)},
                            label={Text("总期数")},singleLine=true,modifier=Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value=dueDay,onValueChange={dueDay=it.filter(Char::isDigit).take(2)},
                            label={Text("每月还款日")},singleLine=true,
                            modifier=Modifier.weight(1f)
                        )
                    }
                }
                Text("原始流水不变。只有明确的分期计划编号才自动合并历史；"+
                    "其他还款可在卡片内人工核对关联。",
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton={
            Button(enabled=valid,onClick={
                if(existing==null)onCreate(source.id,kind,beneficiary,title,amount,
                    terms.toIntOrNull(),dueDay.toIntOrNull())
                else onUpdate?.invoke(existing.id,title,beneficiary,amount,
                    terms.toIntOrNull(),dueDay.toIntOrNull())
            }){Text("保存")}
        },
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}}
    )
}

@Composable
fun FinanceInstallmentScreen(
    state:BillUiState,
    onSearchOrigins:(String)->Unit,
    onCreate:(Long,FinancePlanKind,String,String,Long?,Int?,Int?)->Unit,
    onUpdate:(Long,String,String,Long?,Int?,Int?)->Unit,
    onDelete:(Long)->Unit,
    onSearchLinks:(Long,FinanceLinkRole,String)->Unit,
    onLink:(Long,Long,FinanceLinkRole)->Unit,
    onUnlink:(Long,Long,FinanceLinkRole)->Unit,
) {
    var kind by remember {mutableStateOf(FinancePlanKind.OWN)}
    var pickSource by remember {mutableStateOf(false)}
    var editingSource by remember {mutableStateOf<Transaction?>(null)}
    var editingPlan by remember {mutableStateOf<FinancePlan?>(null)}
    var sourceQuery by remember {mutableStateOf("")}
    var expandedPlan by remember {mutableStateOf<Long?>(null)}
    var linkingPlan by remember {mutableStateOf<FinancePlan?>(null)}
    var linkingRole by remember {mutableStateOf(FinanceLinkRole.REPAYMENT)}
    var linkQuery by remember {mutableStateOf("")}
    var deletePlan by remember {mutableStateOf<FinancePlan?>(null)}
    var removeLink by remember {mutableStateOf<Pair<Long,FinancePlanLink>?>(null)}
    val plans=state.financePlans
    val own=plans.filter {it.kind==FinancePlanKind.OWN}
    val advance=plans.filter {it.kind==FinancePlanKind.ADVANCE}
    val ownedAmount=plans.mapNotNull {it.remainingCent}.sum()
    val owedToMe=advance.mapNotNull {it.receivableCent}.sum()
    val unknownTotal=plans.count {it.totalCent==null}
    val pendingFinance=plans.filter {it.remainingCent==null}

    editingSource?.let { source ->
        FinanceInstallmentEditor(
            source=source,initialKind=kind,onDismiss={editingSource=null},
            onCreate={id,k,person,title,total,terms,due->
                onCreate(id,k,person,title,total,terms,due)
                editingSource=null
                pickSource=false
            }
        )
    }
    editingPlan?.let { plan ->
        val origin=plan.links.firstOrNull {it.role==FinanceLinkRole.ORIGIN}?.transaction
        if(origin!=null) FinanceInstallmentEditor(
            source=origin,existing=plan,
            onDismiss={editingPlan=null},
            onCreate=onCreate,
            onUpdate={id,title,person,total,terms,due->
                onUpdate(id,title,person,total,terms,due)
                editingPlan=null
            }
        )
    }
    deletePlan?.let { plan ->
        AlertDialog(
            onDismissRequest={deletePlan=null},
            title={Text("删除分期卡片？")},
            text={Text("仅删除 ${plan.title} 的分期整理及关联，"+
                "原始账单、消费和还款记录均会保留。")},
            confirmButton={Button(onClick={onDelete(plan.id);deletePlan=null}){Text("删除卡片")}},
            dismissButton={TextButton(onClick={deletePlan=null}){Text("取消")}}
        )
    }
    removeLink?.let { (planId,link) ->
        AlertDialog(
            onDismissRequest={removeLink=null},
            title={Text("取消这笔账单的关联？")},
            text={Text("账单会留在流水中，仅从当前分期卡片移除。")},
            confirmButton={Button(onClick={
                onUnlink(planId,link.transaction.id,link.role)
                removeLink=null
            }){Text("移除关联")}},
            dismissButton={TextButton(onClick={removeLink=null}){Text("取消")}}
        )
    }
    if(pickSource) {
        AlertDialog(
            onDismissRequest={pickSource=false},title={Text("选择已有分期账单")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value=sourceQuery,
                        onValueChange={sourceQuery=it.take(90);onSearchOrigins(sourceQuery)},
                        label={Text("搜索平台、商品、订单号、金额")},
                        modifier=Modifier.fillMaxWidth(),singleLine=true
                    )
                    Text("从导入的原始账单建立卡片，不需要重新录入流水。",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.heightIn(max=395.dp)) {
                        items(state.financeOriginResults,key={it.id}) { tx ->
                            val exists=plans.any {it.originTransactionId==tx.id ||
                                it.links.any { link -> link.transaction.id==tx.id }}
                            Row(Modifier.fillMaxWidth().clickable(enabled=!exists){
                                editingSource=tx
                            }.padding(vertical=9.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tx.counterparty.ifBlank {tx.description},
                                        maxLines=1,overflow=TextOverflow.Ellipsis)
                                    Text("${displayPlatform(tx.platform)} · ${dateTime(tx.occurredAt)} · "+
                                        tx.tradeType,
                                        style=MaterialTheme.typography.labelSmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines=1)
                                }
                                Text(if(exists)"已关联" else tx.amountCent.toYuanText(),
                                    style=MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider()
                        }
                        if(state.financeOriginResults.isEmpty())item {
                            Text("没有找到记录；可输入订单号搜索全部历史流水。")
                        }
                    }
                }
            },confirmButton={TextButton(onClick={pickSource=false}){Text("关闭")}}
        )
    }
    linkingPlan?.let { plan ->
        AlertDialog(
            onDismissRequest={linkingPlan=null},
            title={Text(if(linkingRole==FinanceLinkRole.REPAYMENT)"关联历史还款" else "关联对方收款")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("关联到 ${plan.title}；已有账单不重复创建。",
                        style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value=linkQuery,onValueChange={
                            linkQuery=it.take(90)
                            onSearchLinks(plan.id,linkingRole,linkQuery)
                        },modifier=Modifier.fillMaxWidth(),singleLine=true,
                        label={Text("搜索商户、金额、订单号")}
                    )
                    if(linkingRole==FinanceLinkRole.RECOVERY) Text(
                        "请确认这笔收入确实是对方偿还代付款；普通收入不会自动归入。",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.heightIn(max=400.dp)) {
                        val matches=if(state.financeLinkPlanId==plan.id &&
                            state.financeLinkRole==linkingRole)
                            state.financeLinkCandidates else emptyList()
                        items(matches,key={it.transaction.id}) { item ->
                            val tx=item.transaction
                            Column(Modifier.fillMaxWidth().clickable {
                                onLink(plan.id,tx.id,linkingRole)
                                linkingPlan=null
                            }.padding(vertical=9.dp)) {
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement=Arrangement.SpaceBetween) {
                                    Text(tx.counterparty.ifBlank {tx.description},
                                        maxLines=1,modifier=Modifier.weight(1f))
                                    Spacer(Modifier.width(8.dp))
                                    Text(tx.amountCent.toYuanText(),fontWeight=FontWeight.SemiBold)
                                }
                                Text("${displayPlatform(tx.platform)} · ${dateTime(tx.occurredAt)} · "+
                                    item.reason,
                                    style=MaterialTheme.typography.labelSmall,
                                    color=if(item.exactReference)MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                        }
                        if(matches.isEmpty()) item {Text(
                            "暂无候选账单；输入收款方或订单号搜索历史记录。",
                            style=MaterialTheme.typography.bodySmall)}
                    }
                }
            },confirmButton={TextButton(onClick={linkingPlan=null}){Text("关闭")}}
        )
    }

    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(11.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start=20.dp,end=16.dp,top=18.dp),
                horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically) {
                Column {
                    Text("金融分期",style=MaterialTheme.typography.headlineSmall,
                        fontWeight=FontWeight.Bold)
                    Text("从已有账单自动整理，原始流水不重复录入",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick={
                    sourceQuery="";onSearchOrigins("");pickSource=true
                }) {Icon(Icons.Outlined.Add,null,Modifier.size(16.dp));Text("账单添加")}
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(22.dp),
                colors=CardDefaults.cardColors(
                    containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("已登记分期 · 已知剩余应还",
                        style=MaterialTheme.typography.titleSmall,
                        color=MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(ownedAmount.toYuanText(),
                        style=MaterialTheme.typography.headlineLarge,
                        fontWeight=FontWeight.Bold,
                        color=MaterialTheme.colorScheme.onPrimaryContainer)
                    if(unknownTotal>0) Text(
                        "另有 ${unknownTotal} 笔账单未提供总金额，暂不推算剩余欠款",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onPrimaryContainer)
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("代付尚待收回",color=MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(owedToMe.toYuanText(),
                            fontWeight=FontWeight.SemiBold,
                            color=MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=kind==FinancePlanKind.OWN,
                    onClick={kind=FinancePlanKind.OWN},
                    label={Text("我的分期 ${own.size}")})
                FilterChip(selected=kind==FinancePlanKind.ADVANCE,
                    onClick={kind=FinancePlanKind.ADVANCE},
                    label={Text("代付分期 ${advance.size}")})
            }
        }
        val visible=if(kind==FinancePlanKind.OWN)own else advance
        if(visible.isEmpty())item {
            EmptyFinanceCard(if(kind==FinancePlanKind.ADVANCE)
                "暂无代付分期。点击「账单添加」，选择原始分期并备注对方姓名。"
                else "暂无分期卡片。导入账单后，选择一笔分期交易建立卡片。")
        }
        val groups=if(kind==FinancePlanKind.OWN) visible.groupBy {
            displayPlatform(it.platform)
        } else visible.groupBy {it.beneficiary.ifBlank{"未备注"}}
        groups.forEach { (group,records) ->
            item {SectionHeader(if(kind==FinancePlanKind.OWN)"$group · ${records.size} 笔"
                else "$group · ${records.size} 笔代付分期")}
            items(records,key={"plan_"+it.id}) { plan ->
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                    shape=RoundedCornerShape(19.dp)) {
                    Column(Modifier.padding(15.dp),
                        verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.fillMaxWidth().clickable {
                            expandedPlan=if(expandedPlan==plan.id)null else plan.id
                        },verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(plan.title,fontWeight=FontWeight.SemiBold,
                                    style=MaterialTheme.typography.titleMedium)
                                Text("${displayPlatform(plan.platform)} · ${plan.institution}",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                            }
                            Column(horizontalAlignment=Alignment.End) {
                                Text(plan.remainingCent?.toYuanText()?:"总额未确认",
                                    fontWeight=FontWeight.Bold)
                                Text("剩余应还 · "+
                                    if(expandedPlan==plan.id)"收起 ▲" else "详情 ▼",
                                    style=MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("已关联 ${plan.repayments.size} 笔还款",
                                style=MaterialTheme.typography.labelSmall)
                            plan.termCount?.let {
                                Text("共 ${it} 期",
                                    style=MaterialTheme.typography.labelSmall)
                            }
                            plan.dueDay?.let {
                                Text("每月 ${it} 日还款",
                                    style=MaterialTheme.typography.labelSmall)
                            }
                        }
                        if(plan.kind==FinancePlanKind.ADVANCE) Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("对方尚欠我",
                                style=MaterialTheme.typography.bodySmall)
                            Text(plan.receivableCent?.toYuanText()?:"需补充总额",
                                fontWeight=FontWeight.SemiBold)
                        }
                        if(expandedPlan==plan.id) {
                            HorizontalDivider()
                            Text("已还平台 ${plan.repaidCent.toYuanText()}"+
                                if(plan.kind==FinancePlanKind.ADVANCE)
                                    " · 对方已还 ${plan.recoveredCent.toYuanText()}"
                                else "",style=MaterialTheme.typography.bodyMedium)
                            plan.planReference?.let {
                                Text("自动匹配依据：分期计划编号 ${it.takeLast(6)}",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            } ?: Text(
                                "原账单无可信分期计划编号，其他期数请人工关联。",
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            plan.links.filter {it.role!=FinanceLinkRole.ORIGIN}
                                .forEach { link ->
                                    Row(Modifier.fillMaxWidth(),
                                        verticalAlignment=Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(if(link.role==FinanceLinkRole.REPAYMENT)
                                                "平台还款" else "对方还款",
                                                style=MaterialTheme.typography.labelSmall)
                                            Text("${dateTime(link.transaction.occurredAt)} · "+
                                                link.transaction.amountCent.toYuanText()+
                                                if(link.autoLinked)" · 自动关联" else "",
                                                style=MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick={
                                            removeLink=plan.id to link
                                        }){Text("移除")}
                                    }
                                }
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={
                                    linkQuery=""
                                    linkingRole=FinanceLinkRole.REPAYMENT
                                    linkingPlan=plan
                                    onSearchLinks(plan.id,FinanceLinkRole.REPAYMENT,"")
                                },modifier=Modifier.weight(1f)) {Text("关联还款")}
                                if(plan.kind==FinancePlanKind.ADVANCE)
                                    OutlinedButton(onClick={
                                        linkQuery=""
                                        linkingRole=FinanceLinkRole.RECOVERY
                                        linkingPlan=plan
                                        onSearchLinks(plan.id,FinanceLinkRole.RECOVERY,"")
                                    },modifier=Modifier.weight(1f)) {Text("关联收款")}
                            }
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement=Arrangement.End) {
                                TextButton(onClick={editingPlan=plan}){Text("编辑")}
                                TextButton(onClick={deletePlan=plan}){Text("删除卡片")}
                            }
                        }
                    }
                }
            }
        }
        item {Spacer(Modifier.height(20.dp))}
    }
}
