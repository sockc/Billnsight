package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState

@Composable
fun SettingsScreen(
    state: BillUiState,
    onImport: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onRunAudit: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("我的", "V0.1.7 · 本地优先") }
        item {
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("导入微信 / 支付宝账单")
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("本地账本", style = MaterialTheme.typography.titleMedium)
                    Text("已保存 ${state.totalStored} 笔流水")
                    Text("无需登录，账单不上传服务器。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("账本数据检查", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "只读检查原始流水、未确认交易、未关联退款、异常金额、疑似重复交易单号和净消费核算，不会自动删除或更改数据。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = onRunAudit,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) { Text("立即检查数据") }
                    val report = state.dataAudit
                    if (report == null) {
                        Text("尚未检查，点击上方按钮执行。",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(
                            if (report.databaseIntegrityOk) "数据库完整性检查：通过"
                            else "数据库完整性检查：异常",
                            color = if (report.databaseIntegrityOk)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                        Text("总流水：${report.totalTransactions} 笔")
                        Text("待确认：${report.pendingTransactions} 笔")
                        Text("未关联退款：${report.unmatchedRefunds} 笔")
                        Text("非正金额：${report.nonPositiveAmounts} 笔")
                        Text("收支方向疑似冲突：${report.directionMismatches} 笔")
                        Text("同平台交易单号疑似重复：${report.duplicatePlatformOrderIds} 笔")
                        Text("关联异常：${report.brokenLinks} 处")
                        Text("当前筛选月份消费核对差额：${report.expenseDifferenceCent / 100.0} 元")
                        Text(
                            if (report.needsAttention) "发现需要核实的数据，请查看原始流水。"
                            else if (report.hasFollowUp) "账本校验通过，另有待确认或未关联的流水。"
                            else "本次检查未发现异常。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "疑似重复单号不等于重复消费；系统不会自动删除流水。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("加密备份与恢复", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "使用 AES-256 密码加密的 .bia 文件，保存在你选择的位置；本地备份密码不会上传或保存在应用中。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text("导出加密账本备份")
                    }
                    OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("从加密备份恢复")
                    }
                    Text(
                        "恢复会覆盖当前账本。建议恢复前先导出当前备份，并妥善保管密码；密码丢失无法解密。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("V0.1.7 新增", style = MaterialTheme.typography.titleMedium)
                    Text("• 商户别名持久化与排行按本月 / 三个月 / 全部历史切换")
                    Text("• 首页、流水按日期分组，显示每日收付款小计")
                    Text("• 待确认交易同方向批量处理（最多 100 笔）")
                    Text("• 只读账本数据检查与异常提示")
                    Text("• 加密备份错误密码、损坏文件及大小限制测试")
                    Text("• 信用卡还款单独计入支出，避免净消费重复计算")
                    Text("• 商户排行卡片排版、备注过滤和规范化合并")
                    Text("• 退款、AA 关联原消费，净消费及跨月支持")
                    Text("• 收入类型 → 付款人 → 全部收入下钻")
                    Text("• 同一商户商品别名归并，不覆盖原始账单")
                    Text("• 加密备份兼容新版交易关联")
                    Text("• 二维码付款直接计个人消费、收款直接计个人收入")
                    Text("• 所有收入支出可原位置展开全部明细")
                    Text("• 分类 → 商户 → 商品 → 每笔消费")
                    Text("• 全历史交易搜索、来源及收支筛选")
                    Text("• AES-256 加密 .bia 账本备份与恢复")
                    Text("• 保留原有数据及个人手工修改")
                }
            }
        }
    }
}
