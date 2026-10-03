package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.importer.ScanPaymentClassifier
import com.sockc.billinsight.importer.CategoryWorkbenchPolicy
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toYuanText
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private val scanCategories=listOf(
    "餐饮","买菜","水果","饮品","购物","商超日用","交通","加油","生活缴费","其他"
)
private fun cents(text:String):Long?=runCatching {
    val parsed=BigDecimal(text.trim()).movePointRight(2)
        .setScale(0,RoundingMode.UNNECESSARY).longValueExact()
    require(parsed>0L);parsed
}.getOrNull()

@Composable
fun ScanCenterScreen(
    state:BillUiState,
    onBack:()->Unit,
    onPlatformChange:(Platform?)->Unit,
    onUpdateCategory:(Transaction,String,Boolean)->Unit,
    onSaveLabel:(Long,String)->Unit,
    onRemoveLabel:(Long)->Unit,
    onAddManual:(String,Long,Long,String,String)->Unit,
    onLink:(Long,Long)->Unit,
    onUnlink:(Long)->Unit,
    onDeleteManual:(Long)->Unit,
    onOpenRules:()->Unit,
) {
    val all=state.monthlyTransactions.filter {
        ScanPaymentClassifier.isQrExpense(it) || it.sourceFile=="手动记账"
    }
    val paid=all.filter(ScanPaymentClassifier::isQrExpense)
    val missingName=paid.filter {
        ScanPaymentClassifier.needsMerchantReview(
            it,state.scanMerchantLabels[it.id]
        )
    }
    val unclassified=paid.filter { it.category=="其他" }
    var section by remember { mutableStateOf("ALL") }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var label by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var manualMerchant by remember { mutableStateOf("") }
    var manualAmount by remember { mutableStateOf("") }
    var manualDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var manualNote by remember { mutableStateOf("") }
    var manualCategory by remember { mutableStateOf("其他") }
    var linking by remember { mutableStateOf<Transaction?>(null) }
    var deleting by remember { mutableStateOf<Transaction?>(null) }
    var remembered by remember { mutableStateOf(true) }
    var opened by remember { mutableStateOf<Long?>(null) }

    editing?.let { tx ->
        AlertDialog(
            onDismissRequest={editing=null},
            title={Text("补充这笔扫码消费的商户")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("原始收款方："+tx.counterparty.ifBlank { "未提供" },
                        style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value=label,
                        onValueChange={label=it.take(80)},
                        label={Text("实际商户或收款方名称")},
                        singleLine=true,modifier=Modifier.fillMaxWidth()
                    )
                    Text("仅补充这一笔，不修改官方账单，也不会把其他“二维码付款”合并在一起。",
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=MaterialTheme.typography.bodySmall)
                    if(state.scanMerchantLabels.containsKey(tx.id)) {
                        TextButton(onClick={onRemoveLabel(tx.id);editing=null}) {
                            Text("撤销这笔补名")
                        }
                    }
                }
            },
            confirmButton={
                Button(
                    enabled=label.isNotBlank() &&
                        !ScanPaymentClassifier.isGenericCounterparty(label),
                    onClick={onSaveLabel(tx.id,label);editing=null}
                ){Text("保存")}
            },
            dismissButton={TextButton(onClick={editing=null}){Text("取消")}}
        )
    }
    if(adding) {
        val value=cents(manualAmount)
        val occurred=runCatching {
            LocalDate.parse(manualDate).atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        }.getOrNull()
        AlertDialog(
            onDismissRequest={adding=false},
            title={Text("快速记一笔扫码消费")},
            text={
                Column(
                    Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(9.dp)
                ) {
                    Text("适用于暂未导入的扫码付款。导入正式账单后，请关联去重。",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value=manualMerchant,onValueChange={manualMerchant=it.take(100)},
                        modifier=Modifier.fillMaxWidth(),singleLine=true,
                        label={Text("商户或个人收款方")}
                    )
                    OutlinedTextField(
                        value=manualAmount,onValueChange={manualAmount=it.take(16)},
                        modifier=Modifier.fillMaxWidth(),singleLine=true,
                        label={Text("消费金额（元）")}
                    )
                    OutlinedTextField(
                        value=manualDate,onValueChange={manualDate=it.take(10)},
                        modifier=Modifier.fillMaxWidth(),singleLine=true,
                        label={Text("日期 YYYY-MM-DD")}
                    )
                    Text("快捷分类",style=MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        scanCategories.forEach { name ->
                            FilterChip(selected=manualCategory==name,
                                onClick={manualCategory=name},label={Text(name)})
                        }
                    }
                    OutlinedTextField(
                        value=manualNote,onValueChange={manualNote=it.take(300)},
                        modifier=Modifier.fillMaxWidth(),
                        label={Text("备注（可选）")}
                    )
                    if(value==null || occurred==null) Text("请输入有效金额及日期",
                        color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton={
                Button(
                    enabled=manualMerchant.isNotBlank() && value!=null && occurred!=null,
                    onClick={
                        if(value!=null&&occurred!=null) onAddManual(
                            manualMerchant,value,occurred,manualCategory,manualNote)
                        adding=false;manualAmount="";manualNote=""
                    }
                ){Text("保存")}
            },
            dismissButton={TextButton(onClick={adding=false}){Text("取消")}}
        )
    }
    linking?.let { manual ->
        val linkedIds=state.manualScanLinks.values.toSet()
        val options=state.scanHistory.filter { source ->
            source.platform!=Platform.UNKNOWN && source.id !in linkedIds &&
                ScanPaymentClassifier.isQrExpense(source) &&
                manual.amountCent==source.amountCent &&
                abs(manual.occurredAt-source.occurredAt)<=7L*24L*3600L*1000L
        }.take(25)
        AlertDialog(
            onDismissRequest={linking=null},title={Text("关联正式账单去重")},
            text={
                Column(Modifier.heightIn(max=355.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text("只能关联金额一致、日期相差不超过七天的扫码消费；请核对收款方。",
                        style=MaterialTheme.typography.bodySmall)
                    if(options.isEmpty()) Text("未找到匹配账单，之后导入再关联即可。")
                    options.forEach { candidate ->
                        OutlinedButton(onClick={onLink(manual.id,candidate.id);linking=null},
                            modifier=Modifier.fillMaxWidth()) {
                            Column {
                                Text(candidate.counterparty,
                                    style=MaterialTheme.typography.bodyMedium)
                                Text(candidate.amountCent.toYuanText()+" · "+
                                    Instant.ofEpochMilli(candidate.occurredAt)
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
    deleting?.let { tx ->
        AlertDialog(
            onDismissRequest={deleting=null},
            title={Text("删除手动消费？")},
            text={Text("删除 "+tx.counterparty+" 的 "+
                tx.amountCent.toYuanText()+" 手动记录。官方导入账单不受影响。")},
            confirmButton={
                Button(onClick={onDeleteManual(tx.id);deleting=null}){Text("确认删除")}
            },
            dismissButton={TextButton(onClick={deleting=null}){Text("取消")}}
        )
    }

    val visible=when(section) {
        "MISSING" -> missingName
        "CATEGORY" -> unclassified
        "MANUAL" -> all.filter { it.sourceFile=="手动记账" }
        else -> all
    }
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),
                horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick=onBack){Text("‹ 返回")}
                TextButton(onClick={adding=true}){Text("＋ 快捷记账")}
            }
            PageTitle("扫码消费管理","商家与朋友的收款码付款，都按实际消费核算")
            SourceFilterRow(state.platformFilter,onPlatformChange)
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp),
                shape=RoundedCornerShape(22.dp),
                colors=CardDefaults.cardColors(
                    containerColor=MaterialTheme.colorScheme.primaryContainer
                )) {
                Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text("本月扫码消费",style=MaterialTheme.typography.titleSmall)
                    Text(paid.sumOf { it.amountCent }.toYuanText(),
                        style=MaterialTheme.typography.headlineLarge,
                        fontWeight=FontWeight.Bold)
                    Text(paid.size.toString()+" 笔 · 待补商户 "+
                        missingName.size+" 笔 · 待分类 "+unclassified.size+" 笔",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp)
                .horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(
                    "ALL" to "全部扫码",
                    "MISSING" to "待补商户",
                    "CATEGORY" to "待分类",
                    "MANUAL" to "手动记录"
                ).forEach { (key,title) ->
                    FilterChip(selected=section==key,onClick={section=key},
                        label={Text(title)})
                }
            }
            if(visible.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=remembered,onCheckedChange={remembered=it})
                    Text("明确商户可记住分类；通用二维码及综合平台仅修改本笔",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        if(visible.isEmpty()) item {
            EmptyFinanceCard("本月没有符合当前筛选条件的扫码消费")
        }
        items(visible.take(400),key={it.id}) { tx ->
            val custom=state.scanMerchantLabels[tx.id]
            val isManual=tx.sourceFile=="手动记账"
            val linked=state.manualScanLinks[tx.id]
            val open=opened==tx.id
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                shape=RoundedCornerShape(18.dp),
                colors=CardDefaults.cardColors(
                    containerColor=MaterialTheme.colorScheme.surface
                )) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable {
                        opened=if(open) null else tx.id
                    },horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(custom ?: tx.counterparty.ifBlank { "未知收款方" },
                                maxLines=2,overflow=TextOverflow.Ellipsis,
                                style=MaterialTheme.typography.titleSmall)
                            Text((if(isManual) "手动扫码" else platformLabel(tx.platform))+
                                " · "+tx.category+
                                (if(linked!=null) " · 已关联" else ""),
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(tx.amountCent.toYuanText(),fontWeight=FontWeight.Bold,
                            style=MaterialTheme.typography.titleMedium)
                    }
                    if(open) {
                        Text("快捷分类",style=MaterialTheme.typography.labelMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            scanCategories.forEach { name ->
                                FilterChip(
                                    selected=tx.category==name,
                                    enabled=linked==null,
                                    onClick={
                                        val remember=remembered && !isManual &&
                                            !ScanPaymentClassifier.isGenericCounterparty(
                                                tx.counterparty) &&
                                            !CategoryWorkbenchPolicy.isMixedMerchant(tx.counterparty)
                                        onUpdateCategory(tx,name,remember)
                                    },
                                    label={Text(name)}
                                )
                            }
                        }
                        if(!isManual) {
                            TextButton(onClick={
                                label=custom ?: "";editing=tx
                            }){Text(if(custom==null) "补充这笔商户名称" else "修改补充名称")}
                        } else {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                if(linked!=null) {
                                    TextButton(onClick={onUnlink(tx.id)}){Text("取消关联")}
                                } else {
                                    TextButton(onClick={linking=tx}){Text("关联正式账单")}
                                }
                                TextButton(onClick={deleting=tx},
                                    enabled=linked==null){Text("删除")}
                            }
                        }
                        TransactionCard(tx)
                    }
                }
            }
        }
        if(visible.size>400) item {
            Text("为保持流畅，当前只展示最近 400 笔；全部原始记录仍在流水页。",
                modifier=Modifier.padding(16.dp),
                style=MaterialTheme.typography.bodySmall)
        }
        item {
            TextButton(onClick=onOpenRules,
                modifier=Modifier.fillMaxWidth()){Text("打开商户分类规则中心 ›")}
            Text("普通个人转账不会仅凭对方是朋友就认定为扫码消费；借还款以后再单独设计。",
                modifier=Modifier.padding(horizontal=18.dp,vertical=12.dp),
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodySmall)
        }
    }
}
