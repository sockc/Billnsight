package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.LoanProfile
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toYuanText
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth

private fun optionalAmount(text:String):Long?=runCatching {
    if(text.isBlank()) null else BigDecimal(text.trim())
        .movePointRight(2).setScale(0,RoundingMode.UNNECESSARY).longValueExact()
}.getOrNull()

@Composable
fun LoanCenterScreen(
    state:BillUiState,
    onBack:()->Unit,
    onPrevious:()->Unit,
    onNext:()->Unit,
    onSaveProfile:(String,Long?,Long?)->Unit,
    onDeleteProfile:(String)->Unit,
    onSaveSplit:(Long,Long,Long,Long)->Unit,
    onClearSplit:(Long)->Unit,
) {
    var editingProfile by remember { mutableStateOf<LoanProfile?>(null) }
    var addingProfile by remember { mutableStateOf(false) }
    var institution by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("") }
    var remaining by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var splitting by remember { mutableStateOf<Transaction?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    splitting?.let { tx ->
        LoanSplitDialog(
            tx,state.loanDetails[tx.id],onDismiss={splitting=null},
            onSave=onSaveSplit,onClear=onClearSplit
        )
    }
    if(addingProfile) {
        val principal=optionalAmount(original)
        val balance=optionalAmount(remaining)
        val valid=institution.trim().isNotBlank() &&
            (original.isBlank() || (principal!=null&&principal>0)) &&
            (remaining.isBlank() || (balance!=null&&balance>=0))
        AlertDialog(
            onDismissRequest={addingProfile=false},
            title={Text(if(editingProfile==null) "登记贷款机构" else "修改贷款档案")},
            text={
                Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value=institution,onValueChange={institution=it.take(100)},
                        label={Text("贷款机构/平台")},modifier=Modifier.fillMaxWidth(),
                        singleLine=true,enabled=editingProfile==null
                    )
                    OutlinedTextField(
                        value=original,onValueChange={original=it.take(16)},
                        label={Text("原始贷款金额（元，可留空）")},
                        modifier=Modifier.fillMaxWidth(),singleLine=true
                    )
                    OutlinedTextField(
                        value=remaining,onValueChange={remaining=it.take(16)},
                        label={Text("当前剩余本金（元，可留空）")},
                        modifier=Modifier.fillMaxWidth(),singleLine=true
                    )
                    Text("这些金额只由你填写和修改，不从账单自动推算。历史还款流水单独保留。",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton={
                Button(enabled=valid,onClick={
                    onSaveProfile(institution,principal,balance)
                    addingProfile=false
                }){Text("保存档案")}
            },
            dismissButton={TextButton(onClick={addingProfile=false}){Text("取消")}}
        )
    }
    deleting?.let { key ->
        AlertDialog(
            onDismissRequest={deleting=null},
            title={Text("删除贷款机构档案？")},
            text={Text("仅删除手工登记的原贷款金额和余额，所有原始还款及拆分记录都会保留。")},
            confirmButton={
                Button(onClick={onDeleteProfile(key);deleting=null}){Text("确认删除")}
            },
            dismissButton={TextButton(onClick={deleting=null}){Text("取消")}}
        )
    }
    val month=state.month
    val visible=state.loanHistory.filter { tx ->
        showAll || YearMonth.from(
            Instant.ofEpochMilli(tx.occurredAt).atZone(ZoneId.systemDefault())
        )==month
    }
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),
                horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick=onBack){Text("‹ 返回")}
                TextButton(onClick={
                    editingProfile=null;institution="";original="";remaining="";
                    addingProfile=true
                }){Text("＋ 贷款档案")}
            }
            PageTitleWithMonth("贷款管理","按机构查看还款",month,onPrevious,onNext)
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                shape=RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("本月贷款还款",style=MaterialTheme.typography.titleMedium)
                    Text(state.summary.loanRepaymentCent.toYuanText(),
                        style=MaterialTheme.typography.headlineLarge,
                        fontWeight=FontWeight.Bold)
                    Text("已确认本金 "+state.summary.loanPrincipalCent.toYuanText()+
                        " · 利息及手续费 "+state.summary.loanFinanceCostCent.toYuanText(),
                        style=MaterialTheme.typography.bodyMedium)
                    if(state.summary.loanUnallocatedCent>0L)
                        Text("未拆分 "+state.summary.loanUnallocatedCent.toYuanText()+
                            "，尚不计入个人净消费",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("本月贷款到账 "+state.summary.loanDisbursementCent.toYuanText()+
                        "（不算个人收入）",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { SectionHeader("按贷款机构查看","累计金额来自流水，贷款余额仅来自手动档案") }
        if(state.loanProfiles.isEmpty()) item {
            EmptyFinanceCard("尚无贷款还款流水或手动登记的贷款档案")
        }
        items(state.loanProfiles,key={it.institution}) { profile ->
            val open=expanded==profile.institution
            val linked=visible.filter {
                it.counterparty.trim().ifBlank { "未知贷款机构" }==profile.institution
            }
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                shape=RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable {
                        expanded=if(open) null else profile.institution
                    },horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.institution,style=MaterialTheme.typography.titleMedium,
                                fontWeight=FontWeight.Bold)
                            Text(profile.transactionCount.toString()+" 笔累计还款 · "+
                                if(open) "收起 ▲" else "查看流水 ▼",
                                style=MaterialTheme.typography.bodySmall)
                        }
                        Text(profile.repaidCent.toYuanText(),fontWeight=FontWeight.Bold)
                    }
                    Text("累计本金 "+profile.principalCent.toYuanText()+
                        " · 利息 "+profile.interestCent.toYuanText()+
                        " · 费用 "+profile.feeCent.toYuanText(),
                        style=MaterialTheme.typography.bodySmall)
                    Text("原始金额 "+(profile.originalAmountCent?.toYuanText()?:"未填写")+
                        " · 当前余额 "+(profile.remainingPrincipalCent?.toYuanText()?:"未填写"),
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(profile.missingSplitCount>0) Text(
                        profile.missingSplitCount.toString()+" 笔尚未拆分",
                        color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodySmall
                    )
                    if(open) {
                        Row {
                            TextButton(onClick={
                                editingProfile=profile;institution=profile.institution
                                original=profile.originalAmountCent?.let {
                                    BigDecimal(it).movePointLeft(2).toPlainString()
                                }?:""
                                remaining=profile.remainingPrincipalCent?.let {
                                    BigDecimal(it).movePointLeft(2).toPlainString()
                                }?:""
                                addingProfile=true
                            }){Text("修改档案")}
                            TextButton(onClick={deleting=profile.institution}){Text("清除档案")}
                        }
                        if(linked.isEmpty()) Text("当前范围暂无还款流水",
                            style=MaterialTheme.typography.bodySmall)
                        linked.forEach { tx ->
                            TransactionCard(tx,trailing={
                                if(tx.flowType==FlowType.LOAN_REPAYMENT) {
                                    TextButton(onClick={splitting=tx}) {
                                        Text(if(state.loanDetails.containsKey(tx.id))
                                            "修改本金/利息" else "拆分本金/利息")
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                FilterChip(selected=!showAll,onClick={showAll=false},
                    label={Text("本月流水")})
                FilterChip(selected=showAll,onClick={showAll=true},
                    label={Text("全部已加载历史")})
            }
            Text("已加载最近 3,000 笔贷款相关流水；贷款余额不自动估算。",
                modifier=Modifier.padding(horizontal=20.dp),
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.labelSmall)
        }
    }
}
