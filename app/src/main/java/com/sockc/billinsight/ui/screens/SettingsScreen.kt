package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState

@Composable
fun SettingsScreen(
    state:BillUiState,
    onImport:()->Unit,
    onExportBackup:()->Unit,
    onRestoreBackup:()->Unit,
    onRunAudit:()->Unit,
    onOpenAnalysis:()->Unit,
    onOpenPending:()->Unit,
    onOpenCredit:()->Unit,
    onOpenLoan:()->Unit,
    onOpenRules:()->Unit,
    onOpenTrends:()->Unit,
    onOpenScan:()->Unit,
) {
    var advanced by remember {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item {PageTitle("我的","导入账单、保存备份，就这么简单")}
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("我的账本",style=MaterialTheme.typography.titleLarge,
                        fontWeight=FontWeight.Bold)
                    Text(state.totalStored.toString()+" 笔已保存流水",
                        style=MaterialTheme.typography.headlineMedium,
                        color=MaterialTheme.colorScheme.primary)
                    Text("只在本机解析微信、支付宝官方账单，不需要连接支付账户。",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick=onImport,modifier=Modifier.fillMaxWidth(),
                        shape=RoundedCornerShape(13.dp)){Text("导入微信 / 支付宝账单")}
                    Text("导入后自动识别、去重和统计，无须再配置分类。",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("加密备份",style=MaterialTheme.typography.titleMedium)
                    Text("换机前备份完整账本，包括旧版设置和已纠正的流水。",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick=onExportBackup,
                        modifier=Modifier.fillMaxWidth()){Text("导出加密备份")}
                    OutlinedButton(onClick=onRestoreBackup,
                        modifier=Modifier.fillMaxWidth()){Text("恢复加密备份")}
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    TextButton(onClick={advanced=!advanced},
                        modifier=Modifier.fillMaxWidth()) {
                        Text(if(advanced)"收起高级工具 ▲" else "高级工具（不影响正常使用） ▼")
                    }
                    if(advanced) {
                        HorizontalDivider()
                        Text("只有需要纠错或维护旧记录时才打开。",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick=onOpenCredit){Text("信用卡历史补录")}
                        TextButton(onClick=onOpenLoan){Text("贷款明细与拆分")}
                        TextButton(onClick=onOpenScan){Text("查看旧版手动扫码记录")}
                        TextButton(onClick=onOpenRules){Text("分类及商户别名")}
                        TextButton(onClick=onOpenTrends){Text("历史趋势")}
                        if(state.pendingCount>0)
                            TextButton(onClick=onOpenPending){
                                Text("需要核对的记录："+state.pendingCount.toString()+" 笔")
                            }
                        OutlinedButton(onClick=onRunAudit,enabled=!state.isLoading) {
                            Text("检查账本完整性")
                        }
                        state.dataAudit?.let { report ->
                            Text(if(report.databaseIntegrityOk)"数据库完整性检查通过"
                                else "数据库完整性异常，请保留备份",
                                color=if(report.databaseIntegrityOk)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item {
            Text("BillInsight V0.2.2 · 本地优先 · 自动归类",
                modifier=Modifier.fillMaxWidth().padding(vertical=19.dp),
                textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
