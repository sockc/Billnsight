package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.parseAmountToCent
import com.sockc.billinsight.util.toYuanText

/** In-app corrections preserve the raw export amount in the transaction description. */
@Composable
fun ImportAmountCorrectionDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var input by remember(transaction.id) { mutableStateOf(
        if(transaction.amountCent<0L && transaction.amountCent!=Long.MIN_VALUE)
            "%.2f".format(java.util.Locale.ROOT,-transaction.amountCent/100.0)
        else ""
    ) }
    val validSyntax=Regex("""[0-9]{1,12}(\.[0-9]{1,2})?""").matches(input.trim())
    val cents=if(validSyntax) parseAmountToCent(input.trim()) else 0L
    val valid=validSyntax && cents in 1L..100_000_000_000L
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("核对导入金额")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(transaction.counterparty.ifBlank{"未知商户"})
                Text("当前导入金额："+transaction.amountCent.toYuanText(),
                    color=MaterialTheme.colorScheme.error)
                Text(transaction.description,style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value=input,onValueChange={input=it.take(20)},
                    label={Text("核对后的金额（元）")},
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                    singleLine=true,isError=input.isNotEmpty()&&!valid,
                    modifier=Modifier.fillMaxWidth()
                )
                Text("不会修改源文件，原始金额保留在备注中。保存后还需确认交易用途，" +
                    "确认前不计入消费或收入。",style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={
            TextButton(enabled=valid,onClick={onSave(cents)}) {Text("保存核对金额")}
        },
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}}
    )
}
