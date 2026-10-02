package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.TrendPoint
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.util.toYuanText
import kotlin.math.max

@Composable
fun TrendScreen(
    state:BillUiState,
    onBack:()->Unit,
    onSelect:(Long,Long,String)->Unit,
    onClear:()->Unit,
    onPlatformChange:(Platform?)->Unit,
) {
    var period by remember { mutableStateOf("7") }
    val points=when(period) {
        "7" -> state.trendDays.takeLast(7)
        "30" -> state.trendDays
        else -> state.trendMonths
    }
    val peak=points.maxOfOrNull { it.expenseCent }?.coerceAtLeast(1L) ?: 1L
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
                TextButton(onClick={onBack;onClear()}){Text("‹ 返回")}
            }
            PageTitle("消费趋势","点击任一天或任一月份，展开对应原始账单")
            SourceFilterRow(state.platformFilter,onPlatformChange)
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("7" to "最近 7 天","30" to "最近 30 天","12" to "最近 12 个月")
                    .forEach { (key,label) ->
                        FilterChip(selected=period==key,onClick={period=key;onClear()},
                            label={Text(label)})
                    }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp),
                shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    val total=points.sumOf { it.expenseCent }
                    Text("所选期间净消费合计",style=MaterialTheme.typography.titleSmall)
                    Text(total.toYuanText(),
                        style=MaterialTheme.typography.headlineMedium,
                        fontWeight=FontWeight.Bold)
                    Text("已关联退款与分摊按原消费日期抵扣；贷款仅计已拆分的利息和手续费。",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(5.dp),
                        verticalAlignment=Alignment.Bottom
                    ) {
                        points.forEach { point ->
                            Column(
                                Modifier.width(if(period=="7") 57.dp else 54.dp)
                                    .clickable {
                                        onSelect(point.startAt,point.endAt,point.label)
                                    },
                                horizontalAlignment=Alignment.CenterHorizontally,
                                verticalArrangement=Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    when {
                                        point.expenseCent>=10000000L ->
                                            (point.expenseCent/100000L).toString()+"k"
                                        point.expenseCent>=1000000L ->
                                            (point.expenseCent/100000L).toString()+"k"
                                        else -> (point.expenseCent/100).toString()
                                    },
                                    style=MaterialTheme.typography.labelSmall,
                                    maxLines=1
                                )
                                Box(Modifier.height(120.dp),contentAlignment=Alignment.BottomCenter) {
                                    Box(
                                        Modifier.width(25.dp)
                                            .height((4f+116f*point.expenseCent.toFloat()/peak.toFloat()).dp)
                                            .background(
                                                if(state.trendSelectionLabel==point.label)
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.secondary.copy(alpha=0.75f),
                                                RoundedCornerShape(topStart=6.dp,topEnd=6.dp)
                                            )
                                    )
                                }
                                Text(point.label,
                                    style=MaterialTheme.typography.labelSmall,maxLines=1)
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionHeader(
                state.trendSelectionLabel?.let { "已选择 "+it+" 的原始流水" }
                    ?: "点击柱状图查看明细",
                "净消费柱状值与下面原始流水金额口径不同；原始流水还包含转账和还款。"
            )
        }
        if(state.trendSelectionLabel==null) {
            item { EmptyFinanceCard("点击任一天或任一月份，即可查看该时段的全部原始交易。") }
        } else if(state.trendDetails.isEmpty()) {
            item { EmptyFinanceCard("该时段暂无原始交易") }
        } else {
            items(DailyLedger.group(state.trendDetails.take(200)),key={it.first.toString()}) { (day,rows) ->
                DailyLedgerHeader(day,rows)
                rows.forEach { TransactionCard(it) }
            }
            if(state.trendDetails.size>200) item {
                Text("为避免卡顿，明细只展示最近 200 笔；可在流水页搜索全部历史。",
                    modifier=Modifier.padding(16.dp),
                    style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
