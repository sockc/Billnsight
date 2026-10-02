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
import com.sockc.billinsight.util.toYuanText

@Composable
fun TransactionNatureDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onConfirm: (Transaction, FlowType, String) -> Unit,
) {
    val incoming = transaction.directionText.contains("收入") ||
        transaction.flowType in setOf(
            FlowType.INCOME, FlowType.GIFT_INCOME, FlowType.LOAN_RECOVERY, FlowType.LOAN_DISBURSEMENT,
            FlowType.BUSINESS_INCOME, FlowType.REFUND
        )
    val options = if (incoming) listOf(
        FlowType.INCOME, FlowType.GIFT_INCOME, FlowType.LOAN_RECOVERY, FlowType.LOAN_DISBURSEMENT,
        FlowType.BUSINESS_INCOME, FlowType.REFUND, FlowType.TRANSFER, FlowType.IGNORE
    ) else listOf(
        FlowType.EXPENSE, FlowType.GIFT_EXPENSE, FlowType.LOAN_OUT,
        FlowType.BUSINESS_EXPENSE, FlowType.CREDIT_REPAYMENT, FlowType.LOAN_REPAYMENT, FlowType.TRANSFER, FlowType.IGNORE
    )
    var nature by remember(transaction.id) {
        mutableStateOf<FlowType?>(transaction.flowType.takeUnless { it == FlowType.PENDING })
    }
    var category by remember(transaction.id) {
        mutableStateOf(transaction.category.takeIf { it in TransactionClassifier.categories } ?: "其他")
    }
    var categoryMenu by remember(transaction.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (transaction.flowType == FlowType.PENDING) "确认交易用途" else "修改交易性质") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(transaction.counterparty.ifBlank { transaction.description })
                Text(transaction.amountCent.toYuanText())
                Text("只调整这一笔交易，不自动决定同一收付款人的其他交易。")
                options.forEach { choice ->
                    Row(
                        Modifier.fillMaxWidth().clickable { nature = choice },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = nature == choice, onClick = { nature = choice })
                        Text(choice.displayName())
                    }
                }
                if (nature == FlowType.EXPENSE) {
                    TextButton(onClick = { categoryMenu = true }) { Text("分类：$category") }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        TransactionClassifier.categories.forEach { value ->
                            DropdownMenuItem(
                                text = { CategoryBadge(value) },
                                onClick = { category = value; categoryMenu = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nature != null && nature in options,
                onClick = {
                    val selected = nature ?: return@TextButton
                    val resolvedCategory = when (selected) {
                        FlowType.EXPENSE -> category
                        FlowType.GIFT_EXPENSE -> "人情"
                        FlowType.GIFT_INCOME -> "红包收入"
                        FlowType.BUSINESS_EXPENSE, FlowType.BUSINESS_INCOME -> "经营相关"
                        FlowType.LOAN_OUT -> "借出款"
                        FlowType.LOAN_RECOVERY -> "借款收回"
                        FlowType.TRANSFER -> "资金流转"
                        FlowType.CREDIT_REPAYMENT -> "信用卡还款"
                        FlowType.LOAN_REPAYMENT -> "贷款还款"
                        FlowType.LOAN_DISBURSEMENT -> "贷款到账"
                        FlowType.INCOME -> if (transaction.category == "转账收入" || transaction.category == "扫码收入") transaction.category else "收入"
                        FlowType.REFUND -> "退款"
                        FlowType.IGNORE -> "忽略"
                        FlowType.PENDING -> "待确认"
                    }
                    onConfirm(transaction, selected, resolvedCategory)
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
