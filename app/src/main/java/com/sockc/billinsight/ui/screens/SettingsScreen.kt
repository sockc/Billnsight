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
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("我的", "V0.1.6 · 本地优先") }
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
                    Text("V0.1.6 新增", style = MaterialTheme.typography.titleMedium)
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
