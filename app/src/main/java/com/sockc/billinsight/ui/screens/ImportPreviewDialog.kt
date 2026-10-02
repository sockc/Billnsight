package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.model.ImportPreview
import com.sockc.billinsight.model.displayName
import com.sockc.billinsight.util.toYuanText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ImportPreviewDialog(
    preview:ImportPreview,
    loading:Boolean,
    onConfirm:()->Unit,
    onDismiss:()->Unit,
) {
    val dateFormat=DateTimeFormatter.ofPattern("yyyy-MM-dd")
    fun date(time:Long?):String=time?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(dateFormat)
    }?:"未知"
    AlertDialog(
        onDismissRequest=if (loading) ({}) else onDismiss,
        title={Text("导入前核对")},
        text={
            Column(
                Modifier.heightIn(max=470.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(10.dp)
            ) {
                Text(preview.sourceName,style=MaterialTheme.typography.titleSmall)
                Text("账单来源："+platformLabel(preview.platform))
                Text("账单日期："+date(preview.startAt)+" 至 "+date(preview.endAt))
                Text("解析总笔数："+preview.total)
                Text("预计新增："+preview.newCount+" 笔",
                    color=MaterialTheme.colorScheme.primary,
                    style=MaterialTheme.typography.titleMedium)
                Text("已有或文件内重复："+preview.duplicateCount+" 笔")
                Text("待人工确认："+preview.pendingCount+" 笔")
                Text("已识别信用卡还款："+preview.creditRepaymentCount+" 笔")
                Text("异常金额："+preview.invalidAmountCount+" 笔",
                    color=if(preview.invalidAmountCount>0)
                        MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface)
                if(!preview.canCommit) {
                    Text(
                        if(preview.platform==com.sockc.billinsight.model.Platform.UNKNOWN)
                            "无法确定微信或支付宝账单来源，请重新选择正确文件。"
                        else if(preview.invalidAmountCount>0)
                            "检测到未忽略的零金额或负金额记录。为避免污染统计，本次不允许直接导入，请修正源文件。"
                        else "当前文件没有有效交易。",
                        color=MaterialTheme.colorScheme.error
                    )
                }
                SectionHeader("流水样例","仅展示前 12 笔；确认前不会写入数据库")
                preview.samples.forEach { row ->
                    androidx.compose.material3.HorizontalDivider()
                    Text(
                        row.counterparty.ifBlank{"未知交易对方"}+" · "+
                            row.amountCent.toYuanText(),
                        style=MaterialTheme.typography.bodyMedium
                    )
                    Text(row.flowType.displayName()+" · "+row.category,
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "重复交易依靠原始账单指纹去重。已手动修改过的交易不会因重复导入而被覆盖。",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton={
            Button(onClick=onConfirm,enabled=preview.canCommit&&!loading) {
                Text(if(loading) "正在保存…" else "确认导入 "+preview.newCount+" 笔")
            }
        },
        dismissButton={
            TextButton(onClick=onDismiss,enabled=!loading) { Text("取消") }
        }
    )
}
