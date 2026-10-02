package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.importer.MerchantCategoryPolicy
import com.sockc.billinsight.importer.TransactionClassifier
import com.sockc.billinsight.model.CategoryEditPreview
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toYuanText

/** Compact category editor shared by the ledger and both spending leaderboards. */
@Composable
fun ExpenseCategoryDialog(
    transaction: Transaction,
    preview: CategoryEditPreview?,
    onDismiss: () -> Unit,
    onConfirm: (Transaction,String,String) -> Unit,
) {
    val main=listOf("餐饮","购物","商超日用","交通","水果","买菜")
    val merchantAllowed=MerchantCategoryPolicy.key(transaction,emptyMap())!=null
    var category by remember(transaction.id) {
        mutableStateOf(transaction.category.takeIf {it in TransactionClassifier.categories} ?: "其他")
    }
    var scope by remember(transaction.id) {
        mutableStateOf(if(merchantAllowed) "MERCHANT" else "SINGLE")
    }
    var showMore by remember(transaction.id) {
        mutableStateOf(category !in main)
    }
    val choices=if(showMore) TransactionClassifier.categories else main
    AlertDialog(
        onDismissRequest=onDismiss,
        shape=RoundedCornerShape(20.dp),
        title={Text("修改消费分类",style=MaterialTheme.typography.titleMedium)},
        text={
            Column(Modifier.heightIn(max=385.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(transaction.counterparty.ifBlank { transaction.description },
                        modifier=Modifier.weight(1f),maxLines=1,
                        overflow=TextOverflow.Ellipsis,
                        style=MaterialTheme.typography.bodyMedium,
                        fontWeight=FontWeight.SemiBold)
                    Text(transaction.amountCent.toYuanText(),
                        style=MaterialTheme.typography.bodyMedium,
                        fontWeight=FontWeight.SemiBold)
                }
                choices.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        row.forEach { item ->
                            FilterChip(
                                selected=category==item,
                                onClick={category=item},
                                modifier=Modifier.weight(1f).heightIn(min=33.dp),
                                label={Text(item,maxLines=1,
                                    style=MaterialTheme.typography.labelSmall)},
                            )
                        }
                        repeat(3-row.size){Spacer(Modifier.weight(1f))}
                    }
                }
                TextButton(onClick={showMore=!showMore},
                    contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp),
                    modifier=Modifier.height(32.dp)) {
                    Text(if(showMore)"收起分类 ▲" else "更多分类 ▼",
                        style=MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider()
                Text("应用范围",fontWeight=FontWeight.SemiBold,
                    style=MaterialTheme.typography.bodyMedium)
                listOf(
                    "SINGLE" to "仅本笔",
                    "MERCHANT" to "同商户历史及未来",
                    "FUTURE" to "仅未来"
                ).forEach { (key,title) ->
                    val enabled=key=="SINGLE" || merchantAllowed
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled=enabled){scope=key}
                            .heightIn(min=34.dp),
                        verticalAlignment=Alignment.CenterVertically
                    ) {
                        RadioButton(selected=scope==key,onClick={scope=key},
                            enabled=enabled,modifier=Modifier.size(37.dp))
                        Text(title,style=MaterialTheme.typography.bodyMedium,
                            color=if(enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(scope=="MERCHANT" && merchantAllowed) {
                    Surface(color=MaterialTheme.colorScheme.primaryContainer,
                        shape=RoundedCornerShape(10.dp)) {
                        Column(Modifier.fillMaxWidth().padding(9.dp),
                            verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Text(if(preview==null)"正在核对同商户历史记录…" else
                                "本笔 + ${preview.eligibleCount} 笔可修改，"+
                                "${preview.protectedCount} 笔人工分类保留",
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onPrimaryContainer)
                            if((preview?.variantCount?:0)>0)
                                Text("包含 ${preview!!.variantCount} 种已合并的商户名称",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                } else if(scope=="FUTURE") {
                    Text("仅记住新账单分类，不修改当前和历史账单。",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton={
            Button(enabled=scope!="MERCHANT" || preview!=null,
                onClick={onConfirm(transaction,category,scope)}) {
                Text(if(scope=="FUTURE")"记住分类" else "保存")
            }
        },
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}},
    )
}
