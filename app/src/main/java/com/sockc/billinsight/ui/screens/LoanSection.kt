package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.LoanRepaymentDetail
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.toYuanText
import java.math.BigDecimal
import java.math.RoundingMode

private fun parseMoneyCent(text: String): Long? = runCatching {
    val decimal=BigDecimal(text.trim())
    require(decimal >= BigDecimal.ZERO)
    decimal.movePointRight(2).setScale(0,RoundingMode.UNNECESSARY).longValueExact()
}.getOrNull()

@Composable
fun LoanSplitDialog(
    transaction: Transaction,
    existing: LoanRepaymentDetail?,
    onDismiss: () -> Unit,
    onSave: (Long, Long, Long, Long) -> Unit,
    onClear: (Long) -> Unit,
) {
    var principal by remember(transaction.id,existing) {
        mutableStateOf(existing?.principalCent?.let { cents -> "%.2f".format(java.util.Locale.US,cents/100.0) } ?: "")
    }
    var interest by remember(transaction.id,existing) {
        mutableStateOf(existing?.interestCent?.let { cents -> "%.2f".format(java.util.Locale.US,cents/100.0) } ?: "")
    }
    var fee by remember(transaction.id,existing) {
        mutableStateOf(existing?.feeCent?.let { cents -> "%.2f".format(java.util.Locale.US,cents/100.0) } ?: "")
    }
    val parts=listOf(principal,interest,fee).map(::parseMoneyCent)
    val valid=parts.all { it!=null } && parts.filterNotNull().sum()==transaction.amountCent
    val total=parts.filterNotNull().sum()
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("拆分本次贷款还款")},
        text={
            Column(Modifier.heightIn(max=430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text(transaction.counterparty.ifBlank { "贷款机构" },
                    fontWeight=FontWeight.SemiBold)
                Text("本次还款：${transaction.amountCent.toYuanText()}",
                    style=MaterialTheme.typography.titleLarge)
                Text("原始账单没有拆分时请勿估算。未填写的金额不会被当成利息或个人消费。",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.bodySmall)
                listOf(
                    Triple("偿还本金（元）",principal,{ value:String -> principal=value }),
                    Triple("利息（元）",interest,{ value:String -> interest=value }),
                    Triple("手续费（元）",fee,{ value:String -> fee=value }),
                ).forEach { (label,value,update) ->
                    OutlinedTextField(
                        value=value,
                        onValueChange={ update(it.filter { ch -> ch.isDigit()||ch=='.' }.take(14)) },
                        label={Text(label)},
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                        singleLine=true,modifier=Modifier.fillMaxWidth()
                    )
                }
                Text("已填写：${total.toYuanText()} / ${transaction.amountCent.toYuanText()}",
                    color=if(valid) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant)
                if(existing!=null) {
                    TextButton(onClick={onClear(transaction.id);onDismiss()}) {
                        Text("撤销拆分（恢复未知）")
                    }
                }
            }
        },
        confirmButton={
            Button(enabled=valid,onClick={
                onSave(transaction.id,parts[0]!!,parts[1]!!,parts[2]!!)
                onDismiss()
            }) { Text("保存拆分") }
        },
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}}
    )
}

@Composable
fun LoanSection(
    state: BillUiState,
    onSave: (Long,Long,Long,Long) -> Unit,
    onClear: (Long) -> Unit,
) {
    val loans=state.monthlyTransactions.filter { it.flowType==FlowType.LOAN_REPAYMENT }
    val disbursements=state.monthlyTransactions.filter { it.flowType==FlowType.LOAN_DISBURSEMENT }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    editing?.let { tx ->
        LoanSplitDialog(
            transaction=tx,
            existing=state.loanDetails[tx.id],
            onDismiss={editing=null},
            onSave=onSave,
            onClear=onClear
        )
    }
    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("贷款与还款",style=MaterialTheme.typography.titleMedium,
                fontWeight=FontWeight.Bold)
            Text("本月偿还 ${state.summary.loanRepaymentCent.toYuanText()} · " +
                    "${state.summary.loanRepaymentCount} 笔",
                style=MaterialTheme.typography.titleLarge,
                fontWeight=FontWeight.Bold)
            if(state.summary.loanRepaymentCent>0) {
                Text("其中本金 ${state.summary.loanPrincipalCent.toYuanText()} · " +
                    "利息 ${state.summary.loanInterestCent.toYuanText()} · " +
                    "手续费 ${state.summary.loanFeeCent.toYuanText()}",
                    style=MaterialTheme.typography.bodyMedium)
                if(state.summary.loanUnallocatedCent>0) {
                    Text("待拆分：${state.summary.loanUnallocatedCent.toYuanText()}。该部分已计资金支出，但不猜算消费。",
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=MaterialTheme.typography.bodySmall)
                }
            }
            Text("贷款到账：${state.summary.loanDisbursementCent.toYuanText()}（借款不算收入）",
                style=MaterialTheme.typography.bodySmall)
            if(loans.isEmpty() && disbursements.isEmpty()) {
                Text("本月暂无贷款记录",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            loans.forEach { tx ->
                TransactionCard(tx,trailing={
                    TextButton(onClick={editing=tx}) {
                        Text(if(state.loanDetails.containsKey(tx.id)) "修改本金/利息" else "拆分本金/利息")
                    }
                })
            }
            disbursements.forEach { TransactionCard(it) }
        }
    }
}
