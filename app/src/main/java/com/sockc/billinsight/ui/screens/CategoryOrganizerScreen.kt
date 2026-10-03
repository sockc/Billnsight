package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.CategoryReviewGroup
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toYuanText

/** No automatic edits until the user previews and explicitly confirms. */
@Composable
fun CategoryOrganizerScreen(
    state:BillUiState,
    onBack:()->Unit,
    onRefresh:()->Unit,
    onLoadMore:()->Unit,
    onApplyPreview:()->Unit,
    onUndo:()->Unit,
    onPreviewTransaction:(Transaction)->Unit,
    onChangeCategory:(Transaction,String,String)->Unit,
) {
    var confirmAll by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val report=state.categoryReviewPreview

    editing?.let { tx ->
        ExpenseCategoryDialog(
            transaction=tx,
            preview=if(state.categoryPreviewId==tx.id)state.categoryPreview else null,
            evidence=state.categoryEvidence[tx.id].orEmpty(),
            onDismiss={editing=null},
            onConfirm={item,category,scope->
                onChangeCategory(item,category,scope)
                editing=null
            }
        )
    }
    if(confirmAll && report!=null) AlertDialog(
        onDismissRequest={confirmAll=false},
        title={Text("应用本次自动分类预览？")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("已扫描 ${report.scanned}/${report.totalImported} 笔；建议调整 ${report.proposed} 笔。")
                Text("保留 ${report.protected} 笔已有人工分类。"+
                    "所有更新都会记录修改前状态，支持撤销。")
                Text("尚无明确依据或存在冲突的 ${report.unresolved} 笔不自动修改。")
                if(report.scanned<report.totalImported)Text("还有未检查的历史交易。可先返回继续扫描，或者只应用本次预览范围。",
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.error)
            }
        },
        confirmButton={
            Button(enabled=!state.categoryReviewLoading,onClick={
                confirmAll=false
                onApplyPreview()
            }){Text("确认修改 ${report.proposed} 笔")}
        },
        dismissButton={TextButton(onClick={confirmAll=false}){Text("取消")}}
    )
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(9.dp)){
        item {
            Row(
                Modifier.fillMaxWidth().padding(start=10.dp,end=16.dp,top=10.dp),
                verticalAlignment=Alignment.CenterVertically
            ){
                IconButton(onClick=onBack){
                    Icon(Icons.Outlined.ArrowBack,contentDescription="返回首页")
                }
                Column(Modifier.weight(1f)){
                    Text("待整理",style=MaterialTheme.typography.headlineSmall,
                        fontWeight=FontWeight.Bold)
                    Text("按商户集中核对 · 修改后同步流水与分析",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick=onRefresh){Text("重新检查")}
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(18.dp)){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                    Text("分类检查预览",fontWeight=FontWeight.Bold)
                    if(state.categoryReviewLoading) LinearProgressIndicator(
                        Modifier.fillMaxWidth()
                    )
                    if(report==null) Text(
                        if(state.categoryReviewLoading)"正在读取历史消费并核对规则…"
                        else "点击重新检查，预览所有尚待整理的账单。",
                        style=MaterialTheme.typography.bodySmall)
                    else {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            Column {
                                Text("扫描 ${report.scanned}/${report.totalImported} 笔",
                                    style=MaterialTheme.typography.titleMedium)
                                Text("其中建议调整 ${report.proposed} 笔",
                                    style=MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment=Alignment.End) {
                                Text("仍待核对 ${report.unresolved} 笔",
                                    style=MaterialTheme.typography.titleMedium)
                                Text("保护人工分类 ${report.protected} 笔",
                                    style=MaterialTheme.typography.labelSmall)
                            }
                        }
                        if(report.scanned<report.totalImported) OutlinedButton(
                            enabled=!state.categoryReviewLoading && report.scanned<100000,
                            onClick=onLoadMore,modifier=Modifier.fillMaxWidth()) {
                            Text(if(report.scanned<100000)"继续检查更早的 8,000 笔" else "已达到单次扫描上限")
                        }
                        Button(enabled=report.proposed>0 && !state.categoryReviewLoading,
                            onClick={confirmAll=true},modifier=Modifier.fillMaxWidth()) {
                            Text("预览并应用 ${report.proposed} 笔自动分类")
                        }
                    }
                    state.latestCategoryBatch?.let {batch->
                        OutlinedButton(onClick=onUndo,modifier=Modifier.fillMaxWidth(),
                            enabled=!state.categoryReviewLoading) {
                            Text("撤销上次分类 · ${batch.label}")
                        }
                    }
                    Text("未上传你的账单到第三方。高风险和无商品信息的交易仍需手动确认。",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(report!=null && report.groups.isEmpty())item {
            EmptyFinanceCard("本次扫描没有待整理交易")
        }
        if(report!=null)items(report.groups,key={it.key}){group->
            CategoryReviewCard(
                group=group,
                expanded=expanded==group.key,
                onToggle={
                    expanded=if(expanded==group.key)null else group.key
                },
                onEdit={tx->
                    editing=tx
                    onPreviewTransaction(tx)
                }
            )
        }
        item{Spacer(Modifier.height(20.dp))}
    }
}

@Composable
private fun CategoryReviewCard(
    group:CategoryReviewGroup,
    expanded:Boolean,
    onToggle:()->Unit,
    onEdit:(Transaction)->Unit,
){
    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
        shape=RoundedCornerShape(17.dp)){
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
            Row(Modifier.fillMaxWidth().clickable(onClick=onToggle).padding(14.dp),
                verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(group.label,fontWeight=FontWeight.SemiBold,
                        maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("${group.count} 笔 · "+
                        (group.category?.let {"建议：$it"} ?: "类别待确认"),
                        style=MaterialTheme.typography.bodySmall)
                    Text(group.evidence,
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if(expanded)"收起 ▲" else "核对 ▼",
                    style=MaterialTheme.typography.labelSmall)
            }
            if(expanded){
                HorizontalDivider()
                if(!group.batchEligible) Text(
                    "综合平台及支付服务商按商品逐笔确认，不批量套用平台分类。",
                    modifier=Modifier.padding(horizontal=14.dp),
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                group.items.forEach {item->
                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=5.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                            Text(item.transaction.description.ifBlank {
                                item.transaction.counterparty
                            }.take(70),maxLines=2,
                                style=MaterialTheme.typography.bodySmall)
                            Text(item.transaction.category+
                                " → "+(item.proposedCategory?:"待确认"),
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(item.manuallyEdited)Text("已人工确认；自动修改会跳过",
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.primary)
                        }
                        Text(item.transaction.amountCent.toYuanText(),
                            style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={onEdit(item.transaction)}){Text("分类")}
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
