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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.ManualCreditRepayment
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toYuanText
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private fun repaymentMoney(value:String):Long?=runCatching {
    val decimal=BigDecimal(value.trim()).movePointRight(2)
    val cents=decimal.setScale(0,RoundingMode.UNNECESSARY).longValueExact()
    require(cents>0)
    cents
}.getOrNull()

private fun creditDate(text:String):Long?=runCatching {
    LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

@Composable
fun CreditCenterScreen(
    state:BillUiState,
    onBack:()->Unit,
    onPrevious:()->Unit,
    onNext:()->Unit,
    onAdd:(String,Long,Long,String)->Unit,
    onLink:(Long,Long)->Unit,
    onUnlink:(Long)->Unit,
    onDelete:(Long)->Unit,
    onRename:(String,String)->Unit,
    onConfirmSuspected:(Transaction)->Unit,
) {
    val center=state.creditCenter
    val aliases=center.aliases
    fun display(tx:Transaction):String {
        val original=tx.counterparty.trim().ifBlank { "未识别信用卡" }
        return aliases[original.lowercase()] ?: original
    }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var note by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf("") }
    var linking by remember { mutableStateOf<ManualCreditRepayment?>(null) }
    var deleting by remember { mutableStateOf<ManualCreditRepayment?>(null) }
    var expanded by remember(state.month) { mutableStateOf<String?>(null) }
    val recognized=center.imported.groupBy(::display)
    val manually=center.manual.groupBy { it.cardName.trim() }
    val names=(recognized.keys+manually.keys).distinct().sortedByDescending { key ->
        recognized[key].orEmpty().sumOf { it.amountCent }+
            manually[key].orEmpty().filter { it.countsAsRepayment }.sumOf { it.amountCent }
    }

    if(adding) {
        val cents=repaymentMoney(amount)
        val whenAt=creditDate(date)
        AlertDialog(
            onDismissRequest={adding=false},
            title={Text("补录银行卡直接还款")},
            text={
                Column(
                    Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(10.dp)
                ) {
                    Text("只补录微信和支付宝账单中不存在的还款。将来导入对应流水后，可手动关联去重。",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(name,{name=it.take(80)},label={Text("银行或信用卡名称")},
                        modifier=Modifier.fillMaxWidth(),singleLine=true)
                    OutlinedTextField(amount,{amount=it.take(15)},label={Text("还款金额（元）")},
                        modifier=Modifier.fillMaxWidth(),singleLine=true)
                    OutlinedTextField(date,{date=it.take(10)},label={Text("还款日期 YYYY-MM-DD")},
                        modifier=Modifier.fillMaxWidth(),singleLine=true)
                    OutlinedTextField(note,{note=it.take(300)},label={Text("备注（可选）")},
                        modifier=Modifier.fillMaxWidth())
                    if(cents==null || whenAt==null) Text("请输入有效金额与日期",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.error)
                }
            },
            confirmButton={
                Button(
                    enabled=name.isNotBlank()&&cents!=null&&whenAt!=null,
                    onClick={
                        if(cents!=null&&whenAt!=null) onAdd(name,cents,whenAt,note)
                        adding=false;amount="";note=""
                    }
                ) { Text("保存补录") }
            },
            dismissButton={TextButton(onClick={adding=false}){Text("取消")}}
        )
    }
    renaming?.let { source ->
        AlertDialog(
            onDismissRequest={renaming=null},
            title={Text("设置信用卡显示名称")},
            text={
                Column {
                    Text("原始收款方："+source,style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value=displayName,onValueChange={displayName=it.take(80)},
                        label={Text("合并后的卡片名称")},modifier=Modifier.fillMaxWidth()
                    )
                    Text("仅修改分组显示名称，账单收款方不变。",
                        style=MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton={
                TextButton(
                    enabled=displayName.isNotBlank(),
                    onClick={onRename(source,displayName);renaming=null}
                ){Text("保存")}
            },
            dismissButton={TextButton(onClick={renaming=null}){Text("取消")}}
        )
    }
    linking?.let { manual ->
        val linkedIds=center.manual.mapNotNull { it.linkedTransactionId }.toSet()
        val candidates=state.creditHistory.filter {
            it.id !in linkedIds && it.amountCent==manual.amountCent &&
                abs(it.occurredAt-manual.occurredAt)<=90L*24*3600*1000
        }.take(25)
        AlertDialog(
            onDismissRequest={linking=null},
            title={Text("匹配原始还款流水")},
            text={
                Column(
                    Modifier.heightIn(max=365.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(9.dp)
                ) {
                    Text("仅显示金额相等、日期相差不超过 90 天的已确认还款。请核对银行和日期，关联后补录金额将不再重复计入。",
                        style=MaterialTheme.typography.bodySmall)
                    if(candidates.isEmpty()) Text("暂无可关联记录。可在以后导入真实账单后再操作。")
                    candidates.forEach { tx ->
                        OutlinedButton(
                            onClick={onLink(manual.id,tx.id);linking=null},
                            modifier=Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(tx.counterparty,style=MaterialTheme.typography.bodyMedium)
                                Text(tx.amountCent.toYuanText()+" · "+
                                    Instant.ofEpochMilli(tx.occurredAt)
                                        .atZone(ZoneId.systemDefault()).toLocalDate(),
                                    style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={linking=null}){Text("关闭")}}
        )
    }
    deleting?.let { manual ->
        AlertDialog(
            onDismissRequest={deleting=null},
            title={Text("删除补录记录？")},
            text={Text("删除 "+manual.cardName+" 的 "+
                manual.amountCent.toYuanText()+" 补录。已导入的原始账单不受影响。")},
            confirmButton={
                Button(onClick={onDelete(manual.id);deleting=null}){Text("确认删除")}
            },
            dismissButton={TextButton(onClick={deleting=null}){Text("取消")}}
        )
    }

    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),
                horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick=onBack){Text("‹ 返回")}
                TextButton(onClick={adding=true}){Text("＋ 手动补录")}
            }
            PageTitle("信用卡管理","核对账单、管理卡片和手动补录还款")
            MonthHeader(state.month,onPrevious,onNext)
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(22.dp),
                colors=CardDefaults.cardColors(
                    containerColor=MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(19.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("本月确认还款",style=MaterialTheme.typography.titleSmall)
                    Text(center.totalCent.toYuanText(),
                        style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                    Text("已导入 "+center.importedCent.toYuanText()+
                        " · 未关联补录 "+center.manualCent.toYuanText(),
                        style=MaterialTheme.typography.bodySmall)
                    Text(center.transactionCount.toString()+" 笔有效还款 · "+
                        center.suspected.size+" 笔待核对",
                        style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            SectionHeader("按信用卡查看","点击卡片展开原始交易及补录记录")
        }
        if(names.isEmpty()) item {EmptyFinanceCard("本月没有信用卡还款。可补录银行卡直接扣款。")}
        items(names,key={it}) { card ->
            val sourceRows=recognized[card].orEmpty()
            val manualRows=manually[card].orEmpty()
            val sum=sourceRows.sumOf { it.amountCent }+
                manualRows.filter { it.countsAsRepayment }.sumOf { it.amountCent }
            val open=expanded==card
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                shape=RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp),
                    verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth().clickable {
                        expanded=if(open) null else card
                    },horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(card,fontWeight=FontWeight.SemiBold,
                                style=MaterialTheme.typography.titleMedium,
                                maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text((sourceRows.size+manualRows.count { it.countsAsRepayment })
                                .toString()+" 笔 · "+if(open) "收起 ▲" else "查看明细 ▼",
                                style=MaterialTheme.typography.bodySmall)
                        }
                        Text(sum.toYuanText(),fontWeight=FontWeight.Bold,
                            style=MaterialTheme.typography.titleLarge)
                    }
                    if(open) {
                        val raw=sourceRows.firstOrNull()?.counterparty?.trim()
                        if(!raw.isNullOrBlank()) {
                            TextButton(onClick={renaming=raw;displayName=card}) {
                                Text("合并卡片显示名称")
                            }
                        }
                        sourceRows.forEach { TransactionCard(it) }
                        manualRows.forEach { entry ->
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement=Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("补录 · "+entry.amountCent.toYuanText(),
                                        fontWeight=FontWeight.SemiBold)
                                    Text(
                                        entry.note.ifBlank { "银行卡直接还款" }+
                                            if(entry.countsAsRepayment) " · 未关联" else " · 已关联去重",
                                        style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick={
                                    if(entry.countsAsRepayment) linking=entry
                                    else onUnlink(entry.id)
                                }) { Text(if(entry.countsAsRepayment) "关联账单" else "取消关联") }
                                TextButton(onClick={deleting=entry}){Text("删除")}
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("疑似还款待核对",
                "证据不足的流水仅供参考，不会自动计入还款")
        }
        if(center.suspected.isEmpty()) item {EmptyFinanceCard("当前月份没有待核对的疑似还款")}
        items(center.suspected,key={it.id}) { tx ->
            TransactionCard(tx,trailing={
                TextButton(onClick={onConfirmSuspected(tx)}) { Text("确认为信用卡还款") }
            })
        }
        item {
            Text("手动补录只统计未关联的记录；卡片管理不会修改原始微信或支付宝账单。",
                modifier=Modifier.padding(20.dp),
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
