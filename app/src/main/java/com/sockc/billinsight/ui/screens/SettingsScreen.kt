package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState

@Composable
private fun SettingsMenuRow(
    name:String,subtitle:String,icon:ImageVector,tint:Color,onClick:()->Unit
) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=15.dp,vertical=13.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
        Box(Modifier.size(42.dp).background(tint.copy(alpha=.12f),
            RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) {
            Icon(icon,null,tint=tint,modifier=Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(name,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
            Text(subtitle,style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ArrowForwardIos,null,
            tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(13.dp))
    }
}

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
    var advanced by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageTitle("我的","账单导入、管理与安全备份") }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(24.dp),
                colors=CardDefaults.cardColors(containerColor=Color(0xFFEAF2FF))) {
                Column(Modifier.padding(19.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("我的账本",style=MaterialTheme.typography.titleMedium,color=Color(0xFF31529F))
                    Text(state.totalStored.toString()+" 笔流水",
                        style=MaterialTheme.typography.headlineMedium,
                        fontWeight=FontWeight.ExtraBold,color=Color(0xFF285FE5))
                    Text("全部账单仅在本机解析，原始记录保留",
                        style=MaterialTheme.typography.bodySmall,color=Color(0xFF59719E))
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(21.dp),
                colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                Column {
                    SettingsMenuRow("账单导入","微信、支付宝、京东、抖音、美团",
                        Icons.Outlined.FileUpload,Color(0xFF20AF7B),onImport)
                    HorizontalDivider(Modifier.padding(horizontal=15.dp),
                        color=MaterialTheme.colorScheme.outlineVariant)
                    SettingsMenuRow("分类与规则","商户自动分类及别名管理",
                        Icons.Outlined.LocalOffer,Color(0xFFF48638),onOpenRules)
                    HorizontalDivider(Modifier.padding(horizontal=15.dp),
                        color=MaterialTheme.colorScheme.outlineVariant)
                    SettingsMenuRow("信用卡管理","还款记录、手动补录与核对",
                        Icons.Outlined.CreditCard,Color(0xFF8657EC),onOpenCredit)
                    HorizontalDivider(Modifier.padding(horizontal=15.dp),
                        color=MaterialTheme.colorScheme.outlineVariant)
                    SettingsMenuRow("贷款管理","贷款机构与还款明细",
                        Icons.Outlined.AccountBalance,Color(0xFFEE665C),onOpenLoan)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(21.dp),
                colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                Column {
                    SettingsMenuRow("加密备份","导出本地加密账本",
                        Icons.Outlined.Lock,Color(0xFF3C85F7),onExportBackup)
                    HorizontalDivider(Modifier.padding(horizontal=15.dp),
                        color=MaterialTheme.colorScheme.outlineVariant)
                    SettingsMenuRow("恢复备份","从 .bia 文件恢复数据",
                        Icons.Outlined.CloudDownload,Color(0xFF22B9AF),onRestoreBackup)
                }
            }
        }
        item {
            TextButton(onClick={advanced=!advanced},
                modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
                Text(if(advanced)"收起高级工具 ▲" else "展开高级工具 ▼")
            }
        }
        if(advanced) item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(21.dp)) {
                Column {
                    SettingsMenuRow("扫码历史","查看旧版手动扫码账单",
                        Icons.Outlined.QrCodeScanner,Color(0xFF3396B9),onOpenScan)
                    SettingsMenuRow("历史趋势","查看消费变化",
                        Icons.Outlined.AutoGraph,Color(0xFF9B60DA),onOpenTrends)
                    if(state.pendingCount>0) SettingsMenuRow("待核对",
                        state.pendingCount.toString()+" 笔待确认记录",
                        Icons.Outlined.FactCheck,Color(0xFFE39C32),onOpenPending)
                    SettingsMenuRow("检查数据","核对账本完整性",
                        Icons.Outlined.FactCheck,Color(0xFF4884F2),onRunAudit)
                    state.dataAudit?.let { report ->
                        Text(if(report.databaseIntegrityOk)"数据库完整性检查通过"
                            else "数据库完整性异常，请保留备份",
                            color=if(report.databaseIntegrityOk) Color(0xFF16A275)
                                else MaterialTheme.colorScheme.error,
                            modifier=Modifier.padding(15.dp))
                    }
                }
            }
        }
        item {
            Text("BillInsight V0.3.0 · 基于稳定 V0.2.6 重建",
                modifier=Modifier.fillMaxWidth().padding(vertical=16.dp),
                textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
