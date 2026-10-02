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
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.importer.MerchantNaturePolicy
import com.sockc.billinsight.importer.TransactionClassifier
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toYuanText

/** Separates changing one expense from a persistent merchant-wide category rule. */
@Composable
fun ExpenseCategoryDialog(
    transaction: Transaction,
    affectedCount: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Transaction,String,String) -> Unit,
) {
    var category by remember(transaction.id) {
        mutableStateOf(transaction.category.takeIf { it in TransactionClassifier.categories } ?: "其他")
    }
    var scope by remember(transaction.id) { mutableStateOf("SINGLE") }
    val merchantAllowed=MerchantNaturePolicy.eligibleMerchant(transaction) &&
        transaction.flowType==FlowType.EXPENSE
    AlertDialog(
        onDismissRequest=onDismiss,
        shape=RoundedCornerShape(22.dp),
        title={Text("修改消费分类")},
        text={
            Column(Modifier.heightIn(max=475.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(11.dp)) {
                Text(transaction.counterparty.ifBlank { transaction.description },
                    fontWeight=FontWeight.Bold)
                Text(transaction.amountCent.toYuanText(),
                    style=MaterialTheme.typography.titleLarge)
                Text("选择正确的消费类别",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                TransactionClassifier.categories.chunked(3).forEach { items ->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        items.forEach { item ->
                            FilterChip(selected=category==item,onClick={category=item},
                                modifier=Modifier.weight(1f),label={
                                    Text(item,maxLines=1,
                                        style=MaterialTheme.typography.labelSmall)
                                })
                        }
                        repeat(3-items.size){Spacer(Modifier.weight(1f))}
                    }
                }
                HorizontalDivider()
                Text("应用范围",fontWeight=FontWeight.SemiBold)
                listOf(
                    Triple("SINGLE","仅修改这一笔","不影响其他历史账单"),
                    Triple("MERCHANT","同商户历史及未来","仅同平台、同名、未人工分类的普通消费"),
                    Triple("FUTURE","仅记住未来分类","当前和历史账单保持不变")
                ).forEach { (key,title,detail) ->
                    val enabled=key=="SINGLE" || merchantAllowed
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled=enabled){scope=key}
                            .padding(vertical=3.dp),
                        verticalAlignment=Alignment.CenterVertically
                    ) {
                        RadioButton(selected=scope==key,onClick={scope=key},enabled=enabled)
                        Column(Modifier.weight(1f)) {
                            Text(title,style=MaterialTheme.typography.bodyMedium,
                                color=if(enabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(detail,style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if(merchantAllowed && scope=="MERCHANT") {
                    Surface(shape=RoundedCornerShape(12.dp),
                        color=MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            "本笔 + ${affectedCount?.toString() ?: "核对中"} 笔自动分类历史消费；不会覆盖你已人工修改的记录。",
                            modifier=Modifier.padding(12.dp),
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                if(!merchantAllowed) Text(
                    "未识别到可靠的商户名称，不能为无关的个人收款码批量设置相同分类。",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton={
            Button(
                enabled=scope!="MERCHANT" || affectedCount!=null,
                onClick={onConfirm(transaction,category,scope)}
            ){Text(if(scope=="FUTURE")"记住未来分类" else "保存分类")}
        },
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}}
    )
}
