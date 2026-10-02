package com.sockc.billinsight.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.util.toYuanText
import java.time.LocalDate
import java.time.YearMonth

private val Blue=Color(0xFF3678FA)
private val Green=Color(0xFF17A67B)
private val Violet=Color(0xFF8A5BE9)
private val Coral=Color(0xFFEF715C)

@Composable
fun HomeScreen(
    state: BillUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onImport: () -> Unit,
    onPlatformChange: (Platform?) -> Unit,
    onOpenAnalysis: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onSelectPeriod: (String, LocalDate?, LocalDate?) -> Unit,
) {
    val summary=state.homeSummary
    val customStart=remember { mutableStateOf(state.homeStart) }
    val customEnd=remember { mutableStateOf(state.homeEnd) }
    var showCustom by remember { mutableStateOf(false) }
    var showMonth by remember { mutableStateOf(false) }
    val context=LocalContext.current
    fun openPicker(initial: LocalDate, onDate: (LocalDate)->Unit) {
        DatePickerDialog(context,{ _,year,month,day -> onDate(LocalDate.of(year,month+1,day)) },
            initial.year,initial.monthValue-1,initial.dayOfMonth).show()
    }
    val periods=listOf(
        "LAST_7" to "近7天", "MONTH" to "本月", "LAST_MONTH" to "上月",
        "YEAR" to "今年", "LAST_YEAR" to "去年", "CUSTOM" to "自定义"
    )
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start=20.dp,end=18.dp,top=22.dp,bottom=4.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BillInsight",style=MaterialTheme.typography.headlineSmall,
                        fontWeight=FontWeight.ExtraBold,color=MaterialTheme.colorScheme.onBackground)
                    Text("看清每一笔钱，过好每一天",style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape=RoundedCornerShape(15.dp),color=Color(0xFFE9F0FF),
                    modifier=Modifier.clickable(onClick=onImport)) {
                    Icon(Icons.Outlined.FileUpload,contentDescription="导入账单",
                        tint=Blue,modifier=Modifier.padding(13.dp).size(23.dp))
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                verticalArrangement=Arrangement.spacedBy(7.dp)) {
                periods.chunked(3).forEach { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        row.forEach { (key,label) ->
                            val selected=state.homePeriod==key
                            Surface(
                                modifier=Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                                    .clickable {
                                        when(key) {
                                            "MONTH" -> onSelectMonth(YearMonth.now())
                                            "CUSTOM" -> {
                                                customStart.value=state.homeStart
                                                customEnd.value=state.homeEnd
                                                showCustom=true
                                            }
                                            else -> onSelectPeriod(key,null,null)
                                        }
                                    },
                                color=if(selected) Blue else MaterialTheme.colorScheme.surface,
                                contentColor=if(selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                shadowElevation=if(selected) 3.dp else 0.dp
                            ) {
                                Text(label,modifier=Modifier.padding(vertical=11.dp),
                                    style=MaterialTheme.typography.labelMedium,
                                    textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                                    fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { showMonth=true }
                    .padding(horizontal=6.dp,vertical=4.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.Center) {
                    Icon(Icons.Outlined.CalendarMonth,null,tint=Blue,modifier=Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    val periodText=if(state.homePeriod=="MONTH")
                        state.month.year.toString()+"年"+state.month.monthValue+"月  ·  点击选择年月"
                    else state.homeStart.toString()+"  至  "+state.homeEnd.toString()
                    Text(periodText,style=MaterialTheme.typography.labelMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SourceFilterRow(state.platformFilter,onPlatformChange) }
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal=16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3D86FD),Color(0xFF4053E7))))
            ) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text("所选期间实际净消费",color=Color.White.copy(alpha=.83f),
                        style=MaterialTheme.typography.titleSmall)
                    Text(summary.netExpenseCent.toYuanText(),
                        style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold,
                        color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("已扣除关联退款与分摊，信用卡还款不重复计入消费",
                        style=MaterialTheme.typography.bodySmall,
                        color=Color.White.copy(alpha=.88f))
                    HorizontalDivider(color=Color.White.copy(alpha=.24f))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("共 "+summary.transactionCount+" 笔流水",
                            color=Color.White,style=MaterialTheme.typography.labelMedium)
                        Text(state.homeStart.toString()+" — "+state.homeEnd.toString(),
                            color=Color.White.copy(alpha=.85f),
                            style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    FinanceStatCard("收入",summary.incomeCent,"所选期间收入",Green,
                        Icons.Outlined.AccountBalanceWallet,Modifier.weight(1f))
                    FinanceStatCard("资金支出",summary.cashOutflowCent,"已记录支出",Coral,
                        Icons.Outlined.Payments,Modifier.weight(1f))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    FinanceStatCard("信用卡还款",summary.creditRepaymentCent,
                        summary.creditRepaymentCount.toString()+" 笔",Violet,
                        Icons.Outlined.CreditCard,Modifier.weight(1f))
                    FinanceStatCard("贷款还款",summary.loanRepaymentCent,
                        summary.loanRepaymentCount.toString()+" 笔",Color(0xFFDE982F),
                        Icons.Outlined.AccountBalanceWallet,Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Button(onClick=onImport,modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.FileUpload,null,modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("导入账单")
                }
                OutlinedButton(onClick=onOpenAnalysis,modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.Insights,null,modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("收支分析")
                }
            }
        }
        if(state.homePeriod=="MONTH") {
            val days=state.trendDays.takeLast(7)
            val peak=days.maxOfOrNull { it.expenseCent }?.coerceAtLeast(1L)?:1L
            item { SectionHeader("近7天消费趋势","每日金额标注") }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                    shape=RoundedCornerShape(20.dp),
                    colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.Bottom,
                        horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        days.forEach { day ->
                            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,
                                verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text((day.expenseCent/100).toString(),
                                    style=MaterialTheme.typography.labelSmall,maxLines=1,
                                    color=MaterialTheme.colorScheme.onSurface)
                                Box(Modifier.height(68.dp),contentAlignment=Alignment.BottomCenter) {
                                    Box(Modifier.width(23.dp)
                                        .height((5f+59f*day.expenseCent.toFloat()/peak.toFloat()).dp)
                                        .background(Brush.verticalGradient(
                                            listOf(Color(0xFF86B7FF),Blue)),
                                            RoundedCornerShape(topStart=7.dp,topEnd=7.dp)))
                                }
                                Text(day.label,style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeader("近期流水","当前选择的日期范围",
            trailing={TextButton(onClick=onOpenAnalysis){Text("查看分析 ›")}}) }
        if(state.homeRecent.isEmpty())
            item {EmptyFinanceCard("所选日期暂无账单记录")}
        else items(state.homeRecent,key={it.id}) { TransactionCard(it) }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if(showMonth) {
        var year by remember(showMonth) { mutableIntStateOf(state.month.year) }
        AlertDialog(onDismissRequest={showMonth=false},title={Text("选择年份和月份")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={year--}){Text("‹ 前一年")}
                        Text(year.toString(),fontWeight=FontWeight.Bold)
                        TextButton(onClick={year++}){Text("后一年 ›")}
                    }
                    (1..12).chunked(4).forEach { group ->
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            group.forEach { month ->
                                OutlinedButton(onClick={
                                    onSelectMonth(YearMonth.of(year,month))
                                    showMonth=false
                                },modifier=Modifier.weight(1f),
                                    contentPadding=PaddingValues(horizontal=3.dp,vertical=8.dp)) {
                                    Text(month.toString()+"月")
                                }
                            }
                        }
                    }
                }
            },confirmButton={TextButton(onClick={showMonth=false}){Text("关闭")}})
    }
    if(showCustom) {
        AlertDialog(onDismissRequest={showCustom=false},title={Text("自定义日期 · 支持跨年")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick={
                        openPicker(customStart.value) { customStart.value=it }
                    },modifier=Modifier.fillMaxWidth()) {
                        Text("开始日期  "+customStart.value.toString())
                    }
                    OutlinedButton(onClick={
                        openPicker(customEnd.value) { customEnd.value=it }
                    },modifier=Modifier.fillMaxWidth()) {
                        Text("结束日期  "+customEnd.value.toString())
                    }
                    if(customStart.value.isAfter(customEnd.value))
                        Text("开始日期不能晚于结束日期",color=MaterialTheme.colorScheme.error)
                }
            },confirmButton={
                Button(enabled=!customStart.value.isAfter(customEnd.value),onClick={
                    onSelectPeriod("CUSTOM",customStart.value,customEnd.value)
                    showCustom=false
                }) { Text("确定") }
            },dismissButton={TextButton(onClick={showCustom=false}){Text("取消")}})
    }
}

@Composable
private fun FinanceStatCard(
    label:String,amount:Long,hint:String,tint:Color,
    icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier
) {
    Card(modifier,shape=RoundedCornerShape(19.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(28.dp).background(tint.copy(alpha=.13f),
                    RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center) {
                    Icon(icon,null,tint=tint,modifier=Modifier.size(16.dp))
                }
                Text(label,style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(amount.toYuanText(),style=MaterialTheme.typography.titleLarge,
                fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(hint,style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
