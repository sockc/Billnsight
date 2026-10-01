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
        PageTitle("我的", "V0.1.3 · 本地优先")
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
                Text("V0.1.3 新增", style = MaterialTheme.typography.titleMedium)
                Text("• 分类固定颜色")
                Text("• 微信 / 支付宝来源 Badge 与筛选")
                Text("• 本月 vs 上月消费变化")
                Text("• 分类 → 商户二级下钻")
                Text("• 疑似固定支出识别")
                Text("• 小额高频 ¥20 / ¥50 / ¥100")
                Text("• 月度消费日历")
            }
        }
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("账单格式", style = MaterialTheme.typography.titleMedium)
                Text("• 微信 XLSX")
                Text("• 支付宝 CSV / XLSX")
                Text("• ZIP 内 XLSX / CSV / TXT")
                Text("• 加密 ZIP")
                Text("• 旧版 XLS 暂不支持")
            }
        }
    }
}
