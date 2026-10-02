package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.importer.TransactionClassifier
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.model.displayName

@Composable
fun BulkReviewDialog(
    selected: List<Transaction>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>, FlowType, String) -> Unit,
) {
    val directions = selected.map {
        when {
            it.directionText.contains("收入") -> "IN"
            it.directionText.contains("支出") -> "OUT"
            else -> "UNKNOWN"
        }
    }.toSet()
    val valid = selected.size in 1..100 && directions.size == 1 &&
        directions.single() != "UNKNOWN"
    val incoming = valid && directions.single() == "IN"
    val options = if (incoming) listOf(
        FlowType.INCOME, FlowType.GIFT_INCOME, FlowType.LOAN_RECOVERY, FlowType.LOAN_DISBURSEMENT,
        FlowType.BUSINESS_INCOME, FlowType.REFUND, FlowType.TRANSFER, FlowType.IGNORE
    ) else listOf(
        FlowType.EXPENSE, FlowType.GIFT_EXPENSE, FlowType.LOAN_OUT,
        FlowType.BUSINESS_EXPENSE, FlowType.CREDIT_REPAYMENT, FlowType.LOAN_REPAYMENT,
        FlowType.TRANSFER, FlowType.IGNORE
    )
    var target by remember(selected.map { it.id }) { mutableStateOf<FlowType?>(null) }
    var category by remember(selected.map { it.id }) { mutableStateOf("其他") }
    var showCategories by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量确认 ${selected.size} 笔交易") },
        text = {
            Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (!valid) "仅允许同时确认同一收支方向、最多 100 笔记录；未知方向请逐笔处理。"
                    else "只修改选中的待确认流水，不按收付款人自动套用，也不修改原始导入内容。"
                )
                if (valid) {
                    options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable { target=option },
                            verticalAlignment=Alignment.CenterVertically,
                        ) {
                            RadioButton(selected=target==option,onClick={target=option})
                            Text(option.displayName())
                        }
                    }
                    if (target==FlowType.EXPENSE) {
                        OutlinedButton(onClick={showCategories=true}) {
                            Text("消费分类：$category")
                        }
                        DropdownMenu(
                            expanded=showCategories,
                            onDismissRequest={showCategories=false}
                        ) {
                            TransactionClassifier.categories.forEach { name ->
                                DropdownMenuItem(
                                    text={CategoryBadge(name)},
                                    onClick={category=name;showCategories=false}
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && target!=null,
                onClick = {
                    val nature=target ?: return@TextButton
                    val resolved = when(nature) {
                        FlowType.EXPENSE -> category
                        FlowType.GIFT_EXPENSE -> "人情"
                        FlowType.GIFT_INCOME -> "红包收入"
                        FlowType.INCOME -> "收入"
                        FlowType.BUSINESS_INCOME,FlowType.BUSINESS_EXPENSE -> "经营相关"
                        FlowType.LOAN_OUT -> "借出款"
                        FlowType.LOAN_RECOVERY -> "借款收回"
                        FlowType.CREDIT_REPAYMENT -> "信用卡还款"
                        FlowType.LOAN_REPAYMENT -> "贷款还款"
                        FlowType.LOAN_DISBURSEMENT -> "贷款到账"
                        FlowType.REFUND -> "退款"
                        FlowType.TRANSFER -> "资金流转"
                        FlowType.IGNORE -> "忽略"
                        FlowType.PENDING -> "待确认"
                    }
                    onConfirm(selected.map { it.id },nature,resolved)
                }
            ) { Text("确认修改") }
        },
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}}
    )
}
