package com.sockc.billinsight.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** One compact right-aligned date control, shared across home, ledger and analysis. */
@Composable
fun DateScopeTitle(
    title: String,
    subtitle: String,
    state: BillUiState,
    onSelectMonth: (YearMonth) -> Unit,
    onSelectPeriod: (String, LocalDate?, LocalDate?) -> Unit,
) {
    var showScope by remember { mutableStateOf(false) }
    var showMonth by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    val dateLabel = when (state.homePeriod) {
        "MONTH" -> "${state.month.year}年${state.month.monthValue}月"
        "LAST_MONTH" -> "上月"
        "LAST_7" -> "近7天"
        "YEAR" -> "今年"
        "LAST_YEAR" -> "去年"
        "ALL_HISTORY" -> "全部历史"
        else -> state.homeStart.format(DateTimeFormatter.ofPattern("yy/MM/dd")) + "—" +
            state.homeEnd.format(DateTimeFormatter.ofPattern("yy/MM/dd"))
    }
    Row(
        modifier=Modifier.fillMaxWidth().padding(start=20.dp,end=16.dp,top=16.dp,bottom=5.dp),
        horizontalArrangement=Arrangement.spacedBy(8.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(subtitle,style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
        }
        Surface(
            modifier=Modifier.clickable { showScope=true },
            color=MaterialTheme.colorScheme.surface,
            shape=RoundedCornerShape(13.dp),
            tonalElevation=1.dp,
            border=androidx.compose.foundation.BorderStroke(
                1.dp,MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.padding(horizontal=10.dp,vertical=9.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.CalendarMonth,contentDescription=null,
                    tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(17.dp))
                Text(dateLabel,style=MaterialTheme.typography.labelMedium,
                    maxLines=1,color=MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Outlined.KeyboardArrowDown,contentDescription="切换日期",
                    modifier=Modifier.size(15.dp))
            }
        }
    }
    if (showScope) {
        val choices=listOf(
            "LAST_7" to "近7天","MONTH" to "本月","LAST_MONTH" to "上月",
            "YEAR" to "今年","LAST_YEAR" to "去年","CUSTOM" to "自定义"
        )
        AlertDialog(
            onDismissRequest={showScope=false},
            title={Text("选择统计日期")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    choices.chunked(3).forEach { group ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            group.forEach { (key,label) ->
                                val selected=state.homePeriod==key
                                if(selected) Button(
                                    onClick={
                                        showScope=false
                                        when(key) {
                                            "MONTH" -> onSelectMonth(YearMonth.now())
                                            "CUSTOM" -> showCustom=true
                                            else -> onSelectPeriod(key,null,null)
                                        }
                                    },
                                    modifier=Modifier.weight(1f),
                                    contentPadding=PaddingValues(horizontal=3.dp,vertical=9.dp)
                                ) { Text(label,style=MaterialTheme.typography.labelMedium) }
                                else OutlinedButton(
                                    onClick={
                                        showScope=false
                                        when(key) {
                                            "MONTH" -> onSelectMonth(YearMonth.now())
                                            "CUSTOM" -> showCustom=true
                                            else -> onSelectPeriod(key,null,null)
                                        }
                                    },
                                    modifier=Modifier.weight(1f),
                                    contentPadding=PaddingValues(horizontal=3.dp,vertical=9.dp)
                                ) { Text(label,style=MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick={showScope=false;showMonth=true},
                        modifier=Modifier.fillMaxWidth(),
                        shape=RoundedCornerShape(13.dp)
                    ) { Text("直接选择年份和月份 ›") }
                }
            },
            confirmButton={TextButton(onClick={showScope=false}){Text("关闭")}}
        )
    }
    if(showMonth) {
        var year by remember(showMonth) { mutableIntStateOf(state.month.year) }
        AlertDialog(
            onDismissRequest={showMonth=false},title={Text("选择年份和月份")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        TextButton(onClick={year--}){Text("‹")}
                        Text("${year}年",fontWeight=FontWeight.Bold)
                        TextButton(onClick={year++}){Text("›")}
                    }
                    (1..12).chunked(4).forEach { group ->
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            group.forEach { month ->
                                OutlinedButton(
                                    modifier=Modifier.weight(1f),
                                    contentPadding=PaddingValues(horizontal=1.dp,vertical=7.dp),
                                    onClick={
                                        onSelectMonth(YearMonth.of(year,month))
                                        showMonth=false
                                    }
                                ) { Text("${month}月") }
                            }
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={showMonth=false}){Text("关闭")}}
        )
    }
    if(showCustom) {
        val context=LocalContext.current
        var start by remember(showCustom) { mutableStateOf(state.homeStart) }
        var end by remember(showCustom) { mutableStateOf(state.homeEnd) }
        fun pick(initial: LocalDate, result:(LocalDate)->Unit) {
            DatePickerDialog(
                context,{ _,y,m,d->result(LocalDate.of(y,m+1,d)) },
                initial.year,initial.monthValue-1,initial.dayOfMonth
            ).show()
        }
        AlertDialog(
            onDismissRequest={showCustom=false},
            title={Text("自定义日期 · 支持跨年")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick={pick(start){start=it}},
                        modifier=Modifier.fillMaxWidth()) { Text("开始日期  $start") }
                    OutlinedButton(onClick={pick(end){end=it}},
                        modifier=Modifier.fillMaxWidth()) { Text("结束日期  $end") }
                    if(start.isAfter(end)) Text("开始日期不能晚于结束日期",
                        color=MaterialTheme.colorScheme.error)
                }
            },
            confirmButton={
                Button(enabled=!start.isAfter(end),onClick={
                    onSelectPeriod("CUSTOM",start,end)
                    showCustom=false
                }) {Text("确定")}
            },
            dismissButton={TextButton(onClick={showCustom=false}){Text("取消")}}
        )
    }
}
