package com.sockc.billinsight.ui.screens

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.sockc.billinsight.importer.TransactionClassifier
import com.sockc.billinsight.model.Platform

@Composable
fun RuleCenterScreen(
    state:BillUiState,
    onBack:()->Unit,
    onPreview:(String)->Unit,
    onSaveCategory:(String,String,Boolean)->Unit,
    onDeleteCategory:(String)->Unit,
    onDeletePlatformCategory:(Platform,String)->Unit,
    onSaveMerchantAlias:(String,String)->Unit,
    onDeleteMerchantAlias:(String)->Unit,
    onSaveProductAlias:(String,String,String)->Unit,
    onDeleteProductAlias:(String)->Unit,
) {
    var tab by remember { mutableStateOf("category") }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf("") }
    var productMerchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("其他") }
    var target by remember { mutableStateOf("") }
    var applyExisting by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Pair<String,String>?>(null) }
    var confirmApply by remember { mutableStateOf(false) }

    fun clearForm() {
        editing=null;source="";target="";productMerchant=""
        category="其他";applyExisting=false
    }
    deleting?.let { (kind,key) ->
        AlertDialog(
            onDismissRequest={deleting=null},
            title={Text("删除这条规则？")},
            text={Text("删除只影响以后识别和显示，不会撤销已导入账单的原始流水。")},
            confirmButton={
                Button(onClick={
                    when(kind) {
                        "category" -> onDeleteCategory(key)
                        "scoped" -> onDeletePlatformCategory(
                            Platform.valueOf(key.substringBefore('|')),key.substringAfter('|'))
                        "merchant" -> onDeleteMerchantAlias(key)
                        else -> onDeleteProductAlias(key)
                    }
                    deleting=null
                }){Text("确认删除")}
            },
            dismissButton={TextButton(onClick={deleting=null}){Text("取消")}}
        )
    }
    if(confirmApply) {
        AlertDialog(
            onDismissRequest={confirmApply=false},
            title={Text("应用到已有自动分类？")},
            text={
                Text("将更新 "+state.rulePreviewCount+
                    " 笔尚未人工确认的同商户个人消费；人工确认记录和原始商品说明不会修改。")
            },
            confirmButton={
                Button(onClick={
                    onSaveCategory(source,category,true)
                    confirmApply=false;clearForm()
                }){Text("确认应用")}
            },
            dismissButton={TextButton(onClick={confirmApply=false}){Text("取消")}}
        )
    }
    if(!confirmApply) editing?.let { kind ->
        val canApply=state.rulePreviewMerchant==source.trim()
        AlertDialog(
            onDismissRequest={clearForm()},
            title={Text(when(kind) {
                "category" -> "管理商户分类"
                "merchant" -> "合并商户别名"
                else -> "合并商品别名"
            })},
            text={
                Column(
                    Modifier.heightIn(max=430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(9.dp)
                ) {
                    if(kind=="product") {
                        OutlinedTextField(
                            value=productMerchant,
                            onValueChange={productMerchant=it.take(120)},
                            label={Text("商户名称")},
                            modifier=Modifier.fillMaxWidth(),singleLine=true
                        )
                    }
                    OutlinedTextField(
                        value=source,onValueChange={
                            source=it.take(120)
                            if(kind=="category") applyExisting=false
                        },
                        label={Text(if(kind=="product") "商品原名称" else "商户原名称")},
                        modifier=Modifier.fillMaxWidth(),singleLine=true
                    )
                    if(kind=="category") {
                        OutlinedButton(onClick={categoryMenu=true},
                            modifier=Modifier.fillMaxWidth()) {
                            Text("分类："+category)
                        }
                        DropdownMenu(
                            expanded=categoryMenu,
                            onDismissRequest={categoryMenu=false}
                        ) {
                            TransactionClassifier.categories.forEach { option ->
                                DropdownMenuItem(
                                    text={Text(option)},
                                    onClick={category=option;categoryMenu=false}
                                )
                            }
                        }
                        OutlinedButton(
                            onClick={onPreview(source)},
                            enabled=source.trim().isNotBlank(),
                            modifier=Modifier.fillMaxWidth()
                        ){Text("预览可能受影响的历史记录")}
                        Text(
                            if(canApply) "尚未人工确认的同商户消费："+state.rulePreviewCount+" 笔"
                            else "先预览，再决定是否应用到旧交易",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("同步更新已有自动分类")
                                Text("默认关闭；已手动确认的记录永不批量覆盖",
                                    style=MaterialTheme.typography.labelSmall)
                            }
                            Switch(
                                checked=applyExisting,
                                enabled=canApply,
                                onCheckedChange={applyExisting=it}
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value=target,onValueChange={target=it.take(80)},
                            label={Text("统一后的名称")},
                            modifier=Modifier.fillMaxWidth(),singleLine=true
                        )
                        Text("仅修改分析显示，历史流水的商户及商品原始文本不变。",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton={
                Button(
                    enabled=source.isNotBlank() &&
                        (kind=="category" || target.isNotBlank()) &&
                        (kind!="product" || productMerchant.isNotBlank()) &&
                        (!applyExisting || canApply),
                    onClick={
                        when(kind) {
                            "category" -> {
                                if(applyExisting && state.rulePreviewCount>0) {
                                    confirmApply=true
                                } else {
                                    onSaveCategory(source,category,applyExisting)
                                    clearForm()
                                }
                            }
                            "merchant" -> {
                                onSaveMerchantAlias(source,target);clearForm()
                            }
                            else -> {
                                onSaveProductAlias(productMerchant,source,target);clearForm()
                            }
                        }
                    }
                ){Text("保存")}
            },
            dismissButton={TextButton(onClick={clearForm()}){Text("取消")}}
        )
    }

    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),
                horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick=onBack){Text("‹ 返回")}
                TextButton(onClick={
                    source="";category="其他";target="";productMerchant=""
                    applyExisting=false;editing=tab
                }){Text("＋ 新建规则")}
            }
            PageTitle("分类与规则","统一管理分类及商户、商品别名")
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                listOf(
                    "category" to "自动分类",
                    "merchant" to "商户别名",
                    "product" to "商品别名"
                ).forEach { (key,label) ->
                    FilterChip(
                        selected=tab==key,onClick={tab=key},
                        label={Text(label)}
                    )
                }
            }
            OutlinedTextField(
                value=search,onValueChange={search=it.take(80)},
                label={Text("搜索规则或商户")},
                modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),
                singleLine=true
            )
        }
        if(tab=="category") {
            val scoped=state.platformCategoryRules.filter {
                it.merchant.contains(search,true) || it.category.contains(search,true)
            }
            if(scoped.isNotEmpty()) {
                item { SectionHeader("按账单来源记忆","仅匹配相同平台与明确商家名称") }
                items(scoped,key={"scope_"+it.platform.name+"_"+it.merchant}) { rule ->
                    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                        shape=RoundedCornerShape(17.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f),
                                verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(rule.merchant,fontWeight=FontWeight.SemiBold)
                                PlatformBadge(rule.platform)
                                CategoryBadge(rule.category)
                                Text("同平台尚未人工确认 ${rule.affectedCount} 笔（不会自动改旧账）",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick={
                                deleting="scoped" to (rule.platform.name+"|"+rule.merchant)
                            }) {Text("删除")}
                        }
                    }
                }
            }
            item { SectionHeader("通用商户规则","老规则；仅你确认后才修改历史交易") }
            val filtered=state.categoryRules.filter {
                it.merchant.contains(search,true)||it.category.contains(search,true)
            }
            if(filtered.isEmpty()) item {EmptyFinanceCard("还没有匹配的商户分类规则")}
            items(filtered,key={it.merchant}) { rule ->
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                    shape=RoundedCornerShape(17.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(rule.merchant,fontWeight=FontWeight.SemiBold)
                                CategoryBadge(rule.category)
                                Text(
                                    rule.affectedCount.toString()+" 笔未人工确认的原始消费可能受影响",
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick={
                                source=rule.merchant;category=rule.category
                                applyExisting=false
                                onPreview(rule.merchant);editing="category"
                            }){Text("编辑")}
                            TextButton(onClick={
                                deleting="category" to rule.merchant
                            }){Text("删除")}
                        }
                    }
                }
            }
        } else if(tab=="merchant") {
            item { SectionHeader("商户别名","多个支付平台不同显示可统一为同一个商户") }
            val aliases=state.merchantAliases.toList().filter {
                it.first.contains(search,true)||it.second.contains(search,true)
            }.sortedBy { it.first }
            if(aliases.isEmpty()) item {EmptyFinanceCard("暂无匹配的商户别名")}
            items(aliases,key={it.first}) { (key,value) ->
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                    shape=RoundedCornerShape(17.dp)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp),
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(key,fontWeight=FontWeight.SemiBold)
                            Text("→ "+value,style=MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick={
                            source=key;target=value;editing="merchant"
                        }){Text("修改")}
                        TextButton(onClick={deleting="merchant" to key}){Text("删除")}
                    }
                }
            }
        } else {
            item { SectionHeader("商品别名","同一商户内合并不同商品描述，不改原始流水") }
            val entries=state.productAliases.toList().filter {
                it.first.contains(search,true)||it.second.contains(search,true)
            }.sortedBy { it.first }
            if(entries.isEmpty()) item {EmptyFinanceCard("暂无匹配的商品别名")}
            items(entries,key={it.first}) { (key,value) ->
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                    shape=RoundedCornerShape(17.dp)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp),
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(key.substringBefore('|')+" · "+key.substringAfter('|'),
                                fontWeight=FontWeight.SemiBold)
                            Text("→ "+value,style=MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick={
                            productMerchant=key.substringBefore('|')
                            source=key.substringAfter('|')
                            target=value;editing="product"
                        }){Text("修改")}
                        TextButton(onClick={deleting="product" to key}){Text("删除")}
                    }
                }
            }
        }
        item {
            Text("删除规则不会改写原始导入账单；若要恢复已手动确认的分类，需在对应流水中逐笔修改。",
                modifier=Modifier.padding(20.dp),
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
