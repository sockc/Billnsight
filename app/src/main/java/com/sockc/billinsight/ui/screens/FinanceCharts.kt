package com.sockc.billinsight.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.model.CategoryTotal
import com.sockc.billinsight.util.toYuanText

private val chartColors=listOf(
    Color(0xFFFC7656), Color(0xFF9D67E9), Color(0xFF367CFA),
    Color(0xFF18BAA7), Color(0xFFFFB743), Color(0xFFF25D9A),
    Color(0xFF5BA6E6), Color(0xFF7CBF71)
)

@Composable
fun CategoryDonutCard(categories: List<CategoryTotal>, totalCent: Long) {
    val shown=categories.filter { it.amountCent>0 }.take(6)
    if(shown.isEmpty()) return
    val total=shown.sumOf { it.amountCent }.coerceAtLeast(1L)
    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
        shape=RoundedCornerShape(22.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("消费分类分布",style=MaterialTheme.typography.titleMedium,
                fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(146.dp),contentAlignment=Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                        var start=-90f
                        shown.forEachIndexed { index,c ->
                            val sweep=c.amountCent.toFloat()/total.toFloat()*360f
                            drawArc(chartColors[index%chartColors.size],start,sweep,
                                useCenter=false,style=Stroke(width=23.dp.toPx(),cap=StrokeCap.Butt))
                            start+=sweep
                        }
                    }
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("净消费",style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(totalCent.toYuanText(),
                            fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,
                            maxLines=1)
                    }
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    shown.forEachIndexed { index,c ->
                        Row(verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(8.dp).background(chartColors[index%chartColors.size],CircleShape))
                            Text(c.category,Modifier.weight(1f),
                                style=MaterialTheme.typography.labelSmall,maxLines=1,
                                overflow=TextOverflow.Ellipsis)
                            Text((c.amountCent*100/total).toString()+"%",
                                style=MaterialTheme.typography.labelSmall,
                                fontWeight=FontWeight.Bold)
                        }
                    }
                }
            }
            Text("图表按主要消费类别占比展示，中心为已扣除关联退款的净消费。",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
