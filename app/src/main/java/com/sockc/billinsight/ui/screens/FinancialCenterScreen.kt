package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.importer.FinancialTransactionDetector
import com.sockc.billinsight.model.*
import com.sockc.billinsight.util.toYuanText
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Calendar year is based on actual transaction time, not bill labels.
 * Internal transfers are displayed separately and never included in repayment.
 */
@Composable
fun FinancialCenterScreen(
    state:BillUiState,
    onYearChange:(Int)->Unit,
    onRecheck:()->Unit,
    onOpenCredit:()->Unit,
    onOpenLoan:()->Unit,
    onSearchOrigins:(String)->Unit,
    onCreate:(Long,FinancePlanKind,String,String,Long?,Int?,Int?)->Unit,
    onUpdate:(Long,String,String,Long?,Int?,Int?)->Unit,
    onDelete:(Long)->Unit,
    onSearchLinks:(Long,FinanceLinkRole,String)->Unit,
    onLink:(Long,Long,FinanceLinkRole)->Unit,
    onUnlink:(Long,Long,FinanceLinkRole)->Unit,
) {
    val year=state.financeYear
    var month by remember(year){mutableIntStateOf(0)}
    var section by remember{mutableStateOf("账单还款")}
    var yearsExpanded by remember{mutableStateOf(false)}
    var confirmRecheck by remember{mutableStateOf(false)}
    val zone=ZoneId.systemDefault()
    fun yearMonth(time:Long)=YearMonth.from(Instant.ofEpochMilli(time).atZone(zone))
    val transactions=state.financeHistory.filter {
        month==0 || yearMonth(it.occurredAt).monthValue==month
    }
    val manual=state.financeManualRepayments.filter {
        it.countsAsRepayment && (month==0 || yearMonth(it.occurredAt).monthValue==month)
    }
    val installments=state.financePlans.filter { plan ->
        plan.links.any { link ->
            val date=yearMonth(link.transaction.occurredAt)
            date.year==year && (month==0 || date.monthValue==month)
        }
    }

    if(confirmRecheck) AlertDialog(
        onDismissRequest={confirmRecheck=false},
        title={Text("重新识别 "+year+" 年金融流水？")},
        text={Text("仅重查有明确还款或账户转移证据、尚未人工修改的历史记录；"+
            "退款、失败交易、人工分类和已有分期关联均受保护。")},
        confirmButton={Button(onClick={confirmRecheck=false;onRecheck()}){Text("开始检查")}},
        dismissButton={TextButton(onClick={confirmRecheck=false}){Text("取消")}}
    )

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),
            verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceBetween) {
                Text("金融中心",style=MaterialTheme.typography.headlineSmall,
                    fontWeight=FontWeight.Bold)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(enabled=year>2000,onClick={onYearChange(year-1)}){Text("‹")}
                    Box {
                        TextButton(onClick={yearsExpanded=true}){Text(year.toString()+"年 ▾")}
                        DropdownMenu(expanded=yearsExpanded,
                            onDismissRequest={yearsExpanded=false}) {
                            ((LocalDate.now().year+1) downTo 2015).forEach { y ->
                                DropdownMenuItem(text={Text(y.toString()+"年")},onClick={
                                    yearsExpanded=false;onYearChange(y)
                                })
                            }
                        }
                    }
                    TextButton(enabled=year<LocalDate.now().year+1,
                        onClick={onYearChange(year+1)}){Text("›")}
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                (0..12).forEach { m ->
                    FilterChip(selected=month==m,onClick={month=m},
                        label={Text(if(m==0)"全年" else m.toString()+"月")})
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("账单还款","我的分期","贷款","代付","资金流转").forEach { label ->
                    FilterChip(selected=section==label,onClick={section=label},
                        label={Text(label)})
                }
            }
        }
        if(section=="我的分期" || section=="代付") {
            Text("按所选年月筛选有流水的分期卡片；余额显示当前状态，而非历史年末余额。",
                modifier=Modifier.padding(horizontal=18.dp,vertical=5.dp),
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            FinanceInstallmentScreen(
                state=state.copy(financePlans=installments),
                onSearchOrigins=onSearchOrigins,onCreate=onCreate,onUpdate=onUpdate,
                onDelete=onDelete,onSearchLinks=onSearchLinks,onLink=onLink,
                onUnlink=onUnlink,
                initialKind=if(section=="代付")FinancePlanKind.ADVANCE else FinancePlanKind.OWN,
                showHeader=false,showKindFilter=false
            )
        } else {
            val rows=transactions.filter {
                when(section) {
                    "贷款" -> it.flowType==FlowType.LOAN_REPAYMENT
                    "资金流转" -> it.flowType==FlowType.TRANSFER
                    else -> it.flowType==FlowType.CREDIT_REPAYMENT ||
                        it.flowType==FlowType.LOAN_REPAYMENT
                }
            }
            val manualVisible=if(section=="账单还款")manual else emptyList()
            val sum=rows.sumOf{it.amountCent}+manualVisible.sumOf{it.amountCent}
            val grouped=rows.groupBy{yearMonth(it.occurredAt).monthValue}
                .toSortedMap(compareByDescending{it})
            LazyColumn(Modifier.fillMaxSize(),
                verticalArrangement=Arrangement.spacedBy(9.dp)) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                        shape=RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp),
                            verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(when(section) {
                                "资金流转"->"本期已记录资金流转"
                                "贷款"->"本期贷款还款"
                                else->"本期金融还款"
                            },style=MaterialTheme.typography.titleSmall)
                            Text(sum.toYuanText(),
                                style=MaterialTheme.typography.headlineMedium,
                                fontWeight=FontWeight.Bold)
                            Text(if(section=="资金流转")
                                "账户内转入转出不计入消费或收入；跨平台可能有对应流水，不代表净收益。"
                                else "已关联的手动信用卡还款不重复统计；账单归属月份单独标注。",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                if(section=="账单还款")
                                    OutlinedButton(onClick=onOpenCredit){Text("信用卡管理")}
                                if(section=="贷款")
                                    OutlinedButton(onClick=onOpenLoan){Text("贷款管理")}
                                if(section!="贷款")
                                    OutlinedButton(onClick={confirmRecheck=true}) {
                                        Text("重识别本年")
                                    }
                            }
                        }
                    }
                }
                if(rows.isEmpty() && manualVisible.isEmpty()) item {
                    Text(year.toString()+"年"+
                        (if(month>0)month.toString()+"月" else "")+
                        "暂无此类记录。若旧账单仍显示“忽略”，可点击“重识别本年”。",
                        modifier=Modifier.padding(20.dp))
                }
                grouped.forEach { (m,monthRows) ->
                    item {
                        Text(year.toString()+"年"+m.toString().padStart(2,'0')+
                            "月 · "+monthRows.size.toString()+" 笔",
                            modifier=Modifier.padding(horizontal=20.dp,vertical=5.dp),
                            style=MaterialTheme.typography.titleMedium,
                            fontWeight=FontWeight.Bold)
                    }
                    items(monthRows,key={"finance_"+it.id}) { tx ->
                        Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                            shape=RoundedCornerShape(15.dp)) {
                            Column(Modifier.padding(13.dp),
                                verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement=Arrangement.SpaceBetween) {
                                    Text(tx.counterparty.ifBlank{tx.category},
                                        modifier=Modifier.weight(1f),
                                        fontWeight=FontWeight.SemiBold)
                                    Text(tx.amountCent.toYuanText(),
                                        fontWeight=FontWeight.SemiBold)
                                }
                                Text(tx.category+" · "+
                                    Instant.ofEpochMilli(tx.occurredAt).atZone(zone)
                                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                                FinancialTransactionDetector.billingMonth(tx.description)?.let {
                                    Text("账单归属："+it,
                                        style=MaterialTheme.typography.bodySmall)
                                }
                                if(section=="资金流转") Text("资金内部移动，不计日常消费或收入",
                                    style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if(manualVisible.isNotEmpty()) {
                    item {Text("手动补录 · "+manualVisible.size+" 笔",
                        modifier=Modifier.padding(horizontal=20.dp),
                        style=MaterialTheme.typography.titleMedium)}
                    items(manualVisible,key={"manual_finance_"+it.id}) { tx ->
                        Card(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
                            Row(Modifier.fillMaxWidth().padding(13.dp),
                                horizontalArrangement=Arrangement.SpaceBetween) {
                                Text(tx.cardName+" · "+
                                    Instant.ofEpochMilli(tx.occurredAt).atZone(zone)
                                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                                    modifier=Modifier.weight(1f))
                                Text(tx.amountCent.toYuanText())
                            }
                        }
                    }
                }
                item {Spacer(Modifier.height(16.dp))}
            }
        }
    }
}
