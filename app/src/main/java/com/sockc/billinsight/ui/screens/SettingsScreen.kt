package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState

@Composable
fun SettingsScreen(
    state: BillUiState,
    onImport: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onRunAudit: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenCredit: () -> Unit,
    onOpenLoan: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenTrends: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(),
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        item { PageTitle("我的","数据管理、分类规则与隐私安全") }
        item {
            SettingsSection("我的本地账本","账单始终保存在当前设备") {
                Text("${state.totalStored} 笔已保存流水",
                    style=MaterialTheme.typography.headlineMedium,
                    fontWeight=FontWeight.Bold)
                Text("支持微信 XLSX、支付宝 CSV，以及带密码的 ZIP 账单。",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick=onImport,modifier=Modifier.fillMaxWidth().padding(top=4.dp),
                    shape=RoundedCornerShape(13.dp)
                ) {
                    Icon(Icons.Outlined.FolderOpen,contentDescription=null)
                    Text("  导入账单")
                }
            }
        }
        item {
            SettingsSection("财务管理中心","信用卡、贷款及实际净消费分开核算") {
                SettingsAction(
                    "信用卡管理","查看每张卡还款、补录银行扣款、关联去重",
                    onOpenCredit
                )
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingsAction(
                    "贷款管理","按机构查看累计还款，登记原始贷款与剩余本金",
                    onOpenLoan
                )
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingsAction(
                    "消费趋势","近 7 天、30 天和最近 12 个月，点击核对流水",
                    onOpenTrends
                )
            }
        }
        item {
            SettingsSection("分类与规则","优化商户合并与待确认交易") {
                SettingsAction(
                    "分类规则管理","查看、编辑及撤销商户和商品别名",
                    onOpenRules
                )
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingsAction(
                    "商户消费排行","按时间查看商户、商品与对应流水",
                    onOpenAnalysis
                )
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingsAction(
                    "待确认交易","${state.pendingCount} 笔待核对，可按同方向批量处理",
                    onOpenPending
                )
            }
        }
        item {
            SettingsSection("数据安全","加密备份可以保留分类及还款拆分记录") {
                OutlinedButton(
                    onClick=onExportBackup,
                    modifier=Modifier.fillMaxWidth(),
                    shape=RoundedCornerShape(13.dp)
                ) {
                    Icon(Icons.Outlined.Backup,contentDescription=null)
                    Text("  导出加密账本")
                }
                OutlinedButton(
                    onClick=onRestoreBackup,
                    modifier=Modifier.fillMaxWidth(),
                    shape=RoundedCornerShape(13.dp)
                ) {
                    Text("从 .bia 加密备份恢复")
                }
                Text(
                    "恢复会覆盖当前账本。恢复前请先导出备份，并妥善保管密码；忘记密码无法解密。",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SettingsSection("数据健康检查","只读取数据，不自动删除或修改流水") {
                OutlinedButton(
                    onClick=onRunAudit,enabled=!state.isLoading,
                    modifier=Modifier.fillMaxWidth(),
                    shape=RoundedCornerShape(13.dp)
                ) {
                    Icon(Icons.Outlined.FactCheck,contentDescription=null)
                    Text("  ${if(state.dataAudit==null) "检查账本" else "重新检查"}")
                }
                val report=state.dataAudit
                if(report==null) {
                    Text("核对待确认流水、退款关联、拆分贷款及数据库完整性。",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        if(report.databaseIntegrityOk) "数据库完整性 · 通过" else "数据库完整性 · 异常",
                        style=MaterialTheme.typography.titleSmall,
                        color=if(report.databaseIntegrityOk) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.error
                    )
                    listOf(
                        "全部流水" to "${report.totalTransactions} 笔",
                        "待确认" to "${report.pendingTransactions} 笔",
                        "未关联退款" to "${report.unmatchedRefunds} 笔",
                        "未拆分贷款还款" to "${report.loanUnallocatedCount} 笔",
                        "贷款拆分异常" to "${report.loanBreakdownInvalid} 笔",
                        "异常金额" to "${report.nonPositiveAmounts} 笔",
                        "收支方向疑点" to "${report.directionMismatches} 笔",
                        "可能重复的交易单号" to "${report.duplicatePlatformOrderIds} 笔",
                        "关联异常" to "${report.brokenLinks} 处",
                        "所选月消费核对差额" to "${report.expenseDifferenceCent/100.0} 元",
                    ).forEach { (label,value) ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(label,style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value,style=MaterialTheme.typography.bodySmall,
                                fontWeight=FontWeight.Medium)
                        }
                    }
                    Text("疑似重复或异常只是核对线索，系统不会擅自删除原始数据。",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SettingsSection("关于 BillInsight","V0.1.9 · 本地优先") {
                Text("支持自动识别贷款到账与还款，并可手动拆分本金、利息和手续费。",
                    style=MaterialTheme.typography.bodyMedium)
                Text("根据系统设置自动切换深色/浅色主题。所有统计均可查看对应原始流水。",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text("私人账单 · 本地计算 · 不连接账户",
                modifier=Modifier.fillMaxWidth().padding(vertical=22.dp),
                textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SettingsSection(
    title:String,subtitle:String,content:@Composable ColumnScope.()->Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp),
        shape=RoundedCornerShape(20.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun SettingsAction(title:String,subtitle:String,onClick:()->Unit) {
    TextButton(
        onClick=onClick,modifier=Modifier.fillMaxWidth(),
        contentPadding=androidx.compose.foundation.layout.PaddingValues(vertical=6.dp)
    ) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(title,style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold)
            Text(subtitle,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ArrowForward,contentDescription=null)
    }
}
