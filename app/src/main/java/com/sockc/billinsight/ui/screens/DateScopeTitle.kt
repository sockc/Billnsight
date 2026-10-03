package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** One bottom sheet shared by home, ledger and analysis; swipe is limited to date surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateScopeTitle(title:String,subtitle:String,state:BillUiState,
    onSelectMonth:(YearMonth)->Unit,
    onSelectPeriod:(String,LocalDate?,LocalDate?)->Unit
){
    var opened by remember { mutableStateOf(false) }
    val dateFormat=remember {DateTimeFormatter.ofPattern("yyyy年M月d日")}
    val currentMonth=YearMonth.now()
    val label=when(state.homePeriod){
        "MONTH"->"${state.month.year}年${state.month.monthValue}月"
        "DAY"->state.homeStart.format(dateFormat)
        "YEAR"->"${state.month.year}年"
        "ALL_HISTORY"->"全部日期"
        else->state.homeStart.format(dateFormat)+"—"+state.homeEnd.format(dateFormat)
    }
    val swipeThreshold=with(LocalDensity.current){55.dp.toPx()}
    Row(Modifier.fillMaxWidth().padding(start=16.dp,end=10.dp,top=12.dp,bottom=5.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(5.dp)){
        Column(Modifier.weight(1f)){
            Text(title,style=MaterialTheme.typography.titleLarge,
                fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(subtitle,style=MaterialTheme.typography.labelSmall,
                maxLines=1,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(state.homePeriod!="MONTH" || state.month!=currentMonth)
            TextButton(onClick={onSelectMonth(currentMonth)},
                contentPadding=PaddingValues(horizontal=2.dp)){
                Text("本月",style=MaterialTheme.typography.labelSmall)
            }
        Surface(
            Modifier.pointerInput(state.homePeriod,state.month,state.homeStart){
                var drag=0f
                detectHorizontalDragGestures(
                    onDragStart={drag=0f},
                    onDragEnd={
                        if(abs(drag)>=swipeThreshold){
                            if(state.homePeriod=="MONTH")
                                onSelectMonth(if(drag>0)state.month.plusMonths(1)
                                    else state.month.minusMonths(1))
                            if(state.homePeriod=="DAY"){
                                val date=if(drag>0)state.homeStart.plusDays(1)
                                    else state.homeStart.minusDays(1)
                                onSelectPeriod("DAY",date,date)
                            }
                        }
                    },
                    onHorizontalDrag={change,amount->change.consume();drag+=amount}
                )
            }.clickable {opened=true},
            shape=RoundedCornerShape(12.dp),
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
            Row(Modifier.padding(horizontal=8.dp,vertical=10.dp),
                verticalAlignment=Alignment.CenterVertically){
                Text(label,maxLines=1,overflow=TextOverflow.Ellipsis,
                    style=MaterialTheme.typography.labelMedium)
                Icon(Icons.Outlined.KeyboardArrowDown,"选择日期",Modifier.size(17.dp))
            }
        }
    }
    if(opened){
        var mode by remember {mutableStateOf(when(state.homePeriod){
            "DAY"->"DAY";"YEAR"->"YEAR";"CUSTOM"->"CUSTOM"
            "ALL_HISTORY"->"ALL_HISTORY";else->"MONTH"
        })}
        var chosenYear by remember {mutableIntStateOf(state.month.year)}
        var yearMenu by remember {mutableStateOf(false)}
        var rangeStart by remember {mutableStateOf<LocalDate?>(null)}
        val selectableYears=(state.availableYears+
            (state.month.year-1..state.month.year+1).toList()+
            LocalDate.now().year).distinct().sortedDescending()
        val picker=rememberDatePickerState(
            initialSelectedDateMillis=null,
            initialDisplayedMonthMillis=(if(state.homePeriod=="ALL_HISTORY")
                LocalDate.now() else state.homeStart).withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange=1000..3000
        )
        LaunchedEffect(mode,picker.selectedDateMillis){
            val millis=picker.selectedDateMillis ?: return@LaunchedEffect
            if(mode !in setOf("DAY","CUSTOM"))return@LaunchedEffect
            val day=Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            if(mode=="DAY"){
                onSelectPeriod("DAY",day,day);opened=false
            }else if(rangeStart==null || day.isBefore(rangeStart)){
                rangeStart=day
                picker.selectedDateMillis=null
            }else{
                onSelectPeriod("CUSTOM",rangeStart,day);opened=false
            }
        }
        ModalBottomSheet(
            onDismissRequest={opened=false},
            sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)
        ){
            Column(Modifier.fillMaxWidth().padding(horizontal=14.dp)
                .padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("选择账单日期",style=MaterialTheme.typography.titleMedium,
                    fontWeight=FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    listOf("MONTH" to "月份","DAY" to "单日","YEAR" to "全年",
                        "CUSTOM" to "日期范围","ALL_HISTORY" to "全部日期").forEach{(key,display)->
                        FilterChip(selected=mode==key,onClick={
                            mode=key;rangeStart=null;picker.selectedDateMillis=null
                            if(key=="ALL_HISTORY"){
                                onSelectPeriod("ALL_HISTORY",null,null);opened=false
                            }
                        },label={Text(display)})
                    }
                }
                if(mode=="MONTH" || mode=="YEAR"){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        Box{
                            OutlinedButton(onClick={yearMenu=true}){Text("${chosenYear}年 ▾")}
                            DropdownMenu(expanded=yearMenu,onDismissRequest={yearMenu=false}){
                                selectableYears.forEach{selected->
                                    DropdownMenuItem(text={Text("${selected}年")},onClick={
                                        chosenYear=selected;yearMenu=false
                                        if(mode=="YEAR"){
                                            onSelectPeriod("YEAR",LocalDate.of(selected,1,1),null)
                                            opened=false
                                        }
                                    })
                                }
                            }
                        }
                        TextButton(onClick={chosenYear--}){Text("‹")}
                        TextButton(onClick={chosenYear++}){Text("›")}
                        if(mode=="YEAR")Button(onClick={
                            onSelectPeriod("YEAR",LocalDate.of(chosenYear,1,1),null)
                            opened=false
                        }){Text("查看全年")}
                    }
                }
                if(mode=="MONTH"){
                    (1..12).chunked(4).forEach{group->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.spacedBy(5.dp)){
                            group.forEach{m->
                                OutlinedButton(
                                    modifier=Modifier.weight(1f),
                                    contentPadding=PaddingValues(horizontal=1.dp,vertical=8.dp),
                                    onClick={
                                        onSelectMonth(YearMonth.of(chosenYear,m));opened=false
                                    }
                                ){Text("${m}月")}
                            }
                        }
                    }
                }
                if(mode=="DAY" || mode=="CUSTOM"){
                    Text(if(mode=="DAY")"点一天即生效；顶部左右滑可切前后一天"
                        else rangeStart?.let{"已选开始：$it · 请选结束日期"}
                            ?: "先选择开始日期，再选择结束日期；支持跨年",
                        style=MaterialTheme.typography.labelSmall)
                    DatePicker(state=picker,showModeToggle=false)
                    if(mode=="CUSTOM" && rangeStart!=null)
                        TextButton(onClick={rangeStart=null;picker.selectedDateMillis=null}){
                            Text("重新选择开始日期")
                        }
                }
            }
        }
    }
}
