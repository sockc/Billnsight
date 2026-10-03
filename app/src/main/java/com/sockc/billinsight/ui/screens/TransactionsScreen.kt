package com.sockc.billinsight.ui.screens

import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sockc.billinsight.BillUiState
import com.sockc.billinsight.model.FinancePlanKind
import com.sockc.billinsight.model.FinanceReference
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.model.LoanRepaymentDetail
import com.sockc.billinsight.util.toYuanText
import java.time.YearMonth
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TransactionsScreen(
    state: BillUiState,
    onSelectMonth: (YearMonth)->Unit,
    onSelectPeriod: (String,LocalDate?,LocalDate?)->Unit,
    onChangeExpenseCategory: (Transaction,String,String)->Unit,
    onPreviewExpenseCategory: (Transaction)->Unit,
    onCreateFinance:(Long,FinancePlanKind,String,String,Long?,Int?,Int?)->Unit,
    onOpenFinance:()->Unit,
    transactions: List<Transaction>,
    pendingTransactions: List<Transaction>,
    pendingCount: Int,
    reviewMode: Boolean,
    onReviewModeChange: (Boolean) -> Unit,
    platformFilter: Platform?,
    onPlatformChange: (Platform?) -> Unit,
    onNatureChange: (Transaction, FlowType, String, Boolean) -> Unit,
    onCorrectAmount: (Long,Long) -> Unit,
    loanDetails: Map<Long,LoanRepaymentDetail>,
    onSaveLoan: (Long,Long,Long,Long) -> Unit,
    onClearLoan: (Long) -> Unit,
    onBulkConfirm: (List<Long>, FlowType, String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchFlowFilter: String,
    onSearchFlowChange: (String) -> Unit,
    searchLimit: Int,
    onLoadMore: () -> Unit,
) {
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var editingCategory by remember { mutableStateOf<Transaction?>(null) }
    var editingLoan by remember { mutableStateOf<Transaction?>(null) }
    var correctingAmount by remember { mutableStateOf<Transaction?>(null) }
    var financeSource by remember { mutableStateOf<Transaction?>(null) }
    financeSource?.let { tx ->
        FinanceInstallmentEditor(source=tx,onDismiss={financeSource=null},
            onCreate={id,kind,person,title,total,terms,due->
                onCreateFinance(id,kind,person,title,total,terms,due)
                financeSource=null
                onOpenFinance()
            })
    }
    correctingAmount?.let { tx ->
        ImportAmountCorrectionDialog(
            transaction=tx,onDismiss={correctingAmount=null},
            onSave={ amount -> onCorrectAmount(tx.id,amount);correctingAmount=null }
        )
    }
    editingLoan?.let { tx ->
        LoanSplitDialog(transaction=tx,existing=loanDetails[tx.id],
            onDismiss={editingLoan=null},onSave=onSaveLoan,onClear=onClearLoan)
    }
    var selectedIds by remember(reviewMode, platformFilter) {
        mutableStateOf<Set<Long>>(emptySet())
    }
    var bulkDialog by remember { mutableStateOf(false) }
    editingCategory?.let { tx ->
        ExpenseCategoryDialog(transaction=tx,
            preview=if(state.categoryPreviewId==tx.id) state.categoryPreview else null,
            onDismiss={editingCategory=null},
            onConfirm={ item, category, scope ->
                onChangeExpenseCategory(item,category,scope)
                editingCategory=null
            })
    }
    editing?.let { tx ->
        TransactionNatureDialog(
            transaction = tx,
            onDismiss = { editing = null },
            onConfirm = { item, nature, category, applyMerchant ->
                onNatureChange(item, nature, category, applyMerchant)
                editing = null
            },
        )
    }
    val shown = if (reviewMode) pendingTransactions else transactions
    val pendingIds = pendingTransactions.map { it.id }.toSet()
    val checked = selectedIds.intersect(pendingIds)
    if (bulkDialog && checked.isNotEmpty()) {
        BulkReviewDialog(
            selected = pendingTransactions.filter { it.id in checked },
            onDismiss = { bulkDialog = false },
            onConfirm = { ids, type, category ->
                onBulkConfirm(ids,type,category)
                selectedIds = emptySet()
                bulkDialog = false
            },
        )
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement=Arrangement.spacedBy(4.dp)) {
        item { DateScopeTitle("流水","按日期查看原始账单",state,onSelectMonth,onSelectPeriod) }
        item { SourceFilterRow(platformFilter, onPlatformChange) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !reviewMode, onClick = { onReviewModeChange(false) },
                    label = { Text("全部流水") },
                    shape=RoundedCornerShape(13.dp),
                )
                if(pendingCount>0) FilterChip(
                    selected=reviewMode,onClick={onReviewModeChange(true)},
                    label={Text("需核对 "+pendingCount+" 笔")},
                    shape=RoundedCornerShape(13.dp),
                )
            }
        }
        if (reviewMode) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        selectedIds = if (checked.isEmpty())
                            pendingTransactions.take(100).map { it.id }.toSet()
                            else emptySet()
                    }) { Text(if (checked.isEmpty()) "选择前 100 笔" else "取消全选") }
                    TextButton(
                        enabled = checked.isNotEmpty(),
                        onClick = { bulkDialog = true }
                    ) { Text("批量确认 ${checked.size} 笔") }
                }
            }
        }
        if (!reviewMode) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("搜索商户、商品、金额或备注") },
                    leadingIcon = { androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Outlined.Search,null) },
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding=androidx.compose.foundation.layout.PaddingValues(horizontal=16.dp),
                    horizontalArrangement=Arrangement.spacedBy(7.dp)
                ) {
                    listOf(
                        "ALL" to "全部","OUTFLOW" to "总支出",
                        "CONSUMPTION" to "消费","RECEIPTS" to "收入",
                        "REPAYMENT" to "还款","OTHER" to "其他"
                    ).forEach { (key,label) ->
                        item {
                            FilterChip(selected=searchFlowFilter==key,
                                onClick={onSearchFlowChange(key)},
                                label={Text(label)},shape=RoundedCornerShape(12.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
                    horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    val total=when(searchFlowFilter) {
                        "OUTFLOW" -> state.homeSummary.cashOutflowCent
                        "CONSUMPTION" -> state.homeSummary.shoppingConsumptionCent
                        "RECEIPTS" -> state.homeSummary.incomeCent
                        "REPAYMENT" -> state.homeSummary.creditRepaymentCent+state.homeSummary.loanRepaymentCent
                        else -> null
                    }
                    Text(if(total==null) "所选期间 · ${shown.size} 笔已显示" else
                        "所选期间合计 ${total.toYuanText()}",
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={onSelectPeriod("ALL_HISTORY",null,null)}) {
                        Text("全部历史")
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (reviewMode) "暂无待确认交易" else "没有匹配的流水",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(DailyLedger.group(shown), key = { "day_${it.first}" }) { (day, records) ->
            DailyLedgerHeader(day, records)
            records.forEach { tx ->
                if (reviewMode) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = tx.id in checked,
                            onCheckedChange = { isChecked ->
                                selectedIds = if (isChecked && checked.size < 100)
                                    checked + tx.id else checked - tx.id
                            }
                        )
                        Text(
                            if (tx.id in checked) "已选择" else "选择此笔",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                TransactionCard(tx,
                    onMerchantClick={editing=tx},
                    onCategoryClick=if(tx.flowType in setOf(FlowType.EXPENSE,FlowType.GIFT_EXPENSE)) ({
                        editingCategory=tx
                        onPreviewExpenseCategory(tx)
                    }) else null,
                    trailing={
                    Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        if(tx.flowType==FlowType.PENDING && tx.amountCent<=0L &&
                            tx.category=="金额待核对") {
                            TextButton(onClick={correctingAmount=tx}) { Text("核对金额") }
                        } else {
                            TextButton(onClick={editing=tx}) {
                                Text(if(tx.flowType==FlowType.PENDING) "确认用途" else "修改性质")
                            }
                        }
                        if(tx.flowType in setOf(FlowType.EXPENSE,FlowType.GIFT_EXPENSE)) {
                            TextButton(onClick={
                                editingCategory=tx
                                onPreviewExpenseCategory(tx)
                            }) {Text("修改分类")}
                        }
                        if(tx.flowType==FlowType.LOAN_REPAYMENT) {
                            TextButton(onClick={editingLoan=tx}) {
                                Text(if(loanDetails.containsKey(tx.id)) "修改还款拆分" else "拆分本金/利息")
                            }
                        }
                    }
                    if(!reviewMode && tx.platform!=Platform.UNKNOWN &&
                        tx.flowType in setOf(
                            FlowType.EXPENSE,FlowType.CREDIT_REPAYMENT,FlowType.LOAN_REPAYMENT
                        )) {
                        val linked=state.financePlans.any { plan ->
                            plan.links.any { it.transaction.id==tx.id }
                        }
                        TextButton(onClick={
                            if(linked)onOpenFinance() else financeSource=tx
                        }) {
                            Text(if(linked)"已归入金融分期 · 查看" else
                                if(FinanceReference.isInstallmentEvidence(tx))
                                    "设为金融分期" else "从此账单创建分期")
                        }
                    }
                    }
                })
            }
        }
        if(!reviewMode && platformFilter==null &&
            searchFlowFilter in setOf("ALL","OUTFLOW","REPAYMENT") &&
            (searchQuery.isBlank() || "信用卡还款".contains(searchQuery.trim()))) {
            val manual=state.periodManualRepayments.filter { it.countsAsRepayment }
            if(manual.isNotEmpty()) {
                item { SectionHeader("手动补录还款","未关联导入账单，已计入本期还款") }
                items(manual,key={ "manual_"+it.id }) { tx ->
                    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                        shape=RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(15.dp),
                            horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(tx.cardName,style=MaterialTheme.typography.bodyMedium)
                                Text(Instant.ofEpochMilli(tx.occurredAt)
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))+
                                    " · 手动补录信用卡还款",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(tx.amountCent.toYuanText(),
                                style=MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        if (!reviewMode && shown.size >= searchLimit && searchLimit < 10000) {
            item {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("查看更多历史流水（已显示 ${shown.size} 笔）")
                }
            }
        }
        if (reviewMode && pendingCount > pendingTransactions.size) {
            item {
                Text(
                    "仅显示最近 ${pendingTransactions.size} 笔，确认后自动加载后续记录。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
