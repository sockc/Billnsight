package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState

@Composable
fun SettingsScreen(state: BillUiState, onImport: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageTitle("我的", "V0.1 · 本地优先")
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("导入账单")
        }
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("本地数据", style = MaterialTheme.typography.titleMedium)
                Text("已保存 ${state.totalStored} 笔流水")
                Text("当前不需要账号、不上传服务器。", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("当前支持", style = MaterialTheme.typography.titleMedium)
                Text("• CSV 账单")
                Text("• ZIP 内的 CSV/TXT 账单")
                Text("• 微信 / 支付宝表头自动识别")
                Text("• 重复导入自动去重")
                Text("• 商户分类记忆")
                Text("• XLS/XLSX 将在后续版本直接解析")
            }
        }
    }
}
