package com.sockc.billinsight.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.time.LocalDate
import com.sockc.billinsight.BillInsightViewModel
import com.sockc.billinsight.ui.screens.FinancialCenterScreen
import com.sockc.billinsight.ui.screens.CategoryOrganizerScreen
import com.sockc.billinsight.ui.screens.AnalysisScreen
import com.sockc.billinsight.ui.screens.HomeScreen
import com.sockc.billinsight.ui.screens.SettingsScreen
import com.sockc.billinsight.ui.screens.TransactionsScreen
import com.sockc.billinsight.ui.screens.CreditCenterScreen
import com.sockc.billinsight.ui.screens.LoanCenterScreen
import com.sockc.billinsight.ui.screens.RuleCenterScreen
import com.sockc.billinsight.ui.screens.TrendScreen
import com.sockc.billinsight.ui.screens.ScanCenterScreen
import com.sockc.billinsight.ui.screens.ImportPreviewDialog
import com.sockc.billinsight.model.FlowType

private data class Destination(val label: String, val icon: ImageVector)

@Composable
fun BillInsightApp(viewModel: BillInsightViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context=LocalContext.current
    var selected by remember { mutableIntStateOf(0) }
    var detailPage by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled=detailPage!=null) { detailPage=null }
    var reviewMode by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val destinations = remember {
        listOf(
            Destination("首页", Icons.Outlined.Home),
            Destination("流水", Icons.Outlined.ReceiptLong),
            Destination("分析", Icons.Outlined.BarChart),
            Destination("金融", Icons.Outlined.AccountBalance),
            Destination("我的", Icons.Outlined.PersonOutline),
        )
    }

    var zipPassword by remember { mutableStateOf("") }
    var backupAction by remember { mutableStateOf<String?>(null) }
    var backupDraft by remember { mutableStateOf("") }
    var backupConfirm by remember { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    val backupSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.exportEncryptedBackup(it, backupPassword) }
        backupPassword = ""
    }
    val backupReader = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreEncryptedBackup(it, backupPassword) }
        backupPassword = ""
    }
    val startBackup = { backupAction = "export"; backupDraft = ""; backupConfirm = "" }
    val startRestore = { backupAction = "restore"; backupDraft = ""; backupConfirm = "" }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBill)
    }
    val openImport = {
        importer.launch(
            arrayOf(
                "text/*",
                "application/zip",
                "application/octet-stream",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            )
        )
    }

    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            val action=if(msg.contains("可撤销") &&
                state.latestCategoryBatch!=null)"撤销" else null
            val result=snackbar.showSnackbar(msg,actionLabel=action,
                withDismissAction=action!=null)
            viewModel.clearMessage()
            if(result==SnackbarResult.ActionPerformed)
                viewModel.undoLastCategoryBatch()
        }
    }

    if (backupAction != null) {
        val exporting = backupAction == "export"
        AlertDialog(
            onDismissRequest = { backupAction = null; backupDraft = ""; backupConfirm = "" },
            title = { Text(if (exporting) "导出加密备份" else "从加密备份恢复") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        if (exporting) "设置至少 8 位的备份密码。忘记密码将无法恢复。"
                        else "恢复将覆盖当前账本，请先确认已另行备份现有数据。",
                    )
                    OutlinedTextField(
                        value = backupDraft, onValueChange = { backupDraft = it },
                        label = { Text("备份密码（至少 8 位）") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    if (exporting) {
                        OutlinedTextField(
                            value = backupConfirm, onValueChange = { backupConfirm = it },
                            label = { Text("再次输入密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                        if (backupConfirm.isNotBlank() && backupDraft != backupConfirm) {
                            Text("两次密码不一致")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = backupDraft.length >= 8 && (!exporting || backupDraft == backupConfirm),
                    onClick = {
                        backupPassword = backupDraft
                        backupDraft = ""
                        backupConfirm = ""
                        backupAction = null
                        if (exporting) {
                            backupSaver.launch("BillInsight-${LocalDate.now()}-encrypted.bia")
                        } else {
                            backupReader.launch(arrayOf("*/*"))
                        }
                    }
                ) { Text(if (exporting) "选择保存位置" else "选择 .bia 备份") }
            },
            dismissButton = {
                TextButton(onClick = {
                    backupAction = null; backupDraft = ""; backupConfirm = ""
                }) { Text("取消") }
            },
        )
    }

    state.importPreview?.let { preview ->
        ImportPreviewDialog(
            preview=preview,
            loading=state.isLoading,
            onConfirm=viewModel::confirmImport,
            onDismiss=viewModel::cancelImportPreview
        )
    }

    if (state.needsZipPassword) {
        AlertDialog(
            onDismissRequest = viewModel::cancelZipPassword,
            title = { Text("输入账单解压码") },
            text = {
                OutlinedTextField(
                    value = zipPassword,
                    onValueChange = { zipPassword = it },
                    label = { Text("微信 / 支付宝提供的解压码") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.submitZipPassword(zipPassword)
                        zipPassword = ""
                    },
                    enabled = zipPassword.isNotBlank(),
                ) { Text("解压并导入") }
            },
            dismissButton = {
                TextButton(onClick = {
                    zipPassword = ""
                    viewModel.cancelZipPassword()
                }) { Text("取消") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor=MaterialTheme.colorScheme.background,
        bottomBar = {
            if(detailPage==null) NavigationBar(
                containerColor=MaterialTheme.colorScheme.surface,
                tonalElevation=1.dp,
            ) {
                destinations.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = {
                            selected = index
                            if(index==3) viewModel.openFinanceInstallments()
                        },
                        icon = { Icon(item.icon,contentDescription=item.label) },
                        label = { Text(item.label) },
                        alwaysShowLabel=true,
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if(state.startupError!=null) {
                Column(
                    Modifier.fillMaxSize().padding(22.dp),
                    verticalArrangement=Arrangement.Center,
                    horizontalAlignment=Alignment.CenterHorizontally
                ) {
                    Text("账本读取失败",style=MaterialTheme.typography.titleLarge)
                    Text("本地账单未被清除。请不要卸载或清除应用数据。",
                        modifier=Modifier.padding(top=12.dp,bottom=16.dp))
                    Text(state.startupError.orEmpty().take(1100),
                        style=MaterialTheme.typography.bodySmall,
                        modifier=Modifier.padding(bottom=15.dp))
                    Button(onClick={viewModel.refresh()}){Text("重新读取")}
                    OutlinedButton(onClick={
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                            ?.setPrimaryClip(ClipData.newPlainText(
                                "BillInsight 启动故障",state.startupError
                            ))
                    }){Text("复制故障信息")}
                }
            } else when (detailPage) {
                "credit" -> CreditCenterScreen(
                    state=state,
                    onBack={detailPage=null},
                    onPrevious=viewModel::previousMonth,
                    onNext=viewModel::nextMonth,
                    onAdd=viewModel::addManualCredit,
                    onLink=viewModel::linkManualCredit,
                    onUnlink=viewModel::unlinkManualCredit,
                    onDelete=viewModel::deleteManualCredit,
                    onRename=viewModel::renameCreditCard,
                    onConfirmSuspected={
                        viewModel.updateNature(it,FlowType.CREDIT_REPAYMENT,"信用卡还款")
                    }
                )
                "loans" -> LoanCenterScreen(
                    state=state,onBack={detailPage=null},
                    onPrevious=viewModel::previousMonth,onNext=viewModel::nextMonth,
                    onSaveProfile=viewModel::saveLoanProfile,
                    onDeleteProfile=viewModel::deleteLoanProfile,
                    onSaveSplit=viewModel::saveLoanDetail,
                    onClearSplit=viewModel::clearLoanDetail
                )
                "organize" -> CategoryOrganizerScreen(
                    state=state,
                    onBack={viewModel.closeCategoryOrganizer();detailPage=null},
                    onRefresh=viewModel::openCategoryOrganizer,
                    onApplyPreview=viewModel::applyCategoryReviewPreview,
                    onUndo=viewModel::undoLastCategoryBatch,
                    onPreviewTransaction=viewModel::previewExpenseCategory,
                    onChangeCategory=viewModel::changeExpenseCategory,
                )
                "rules" -> RuleCenterScreen(
                    state=state,onBack={detailPage=null},
                    onPreview=viewModel::previewCategoryRule,
                    onReclassify={ _ ->
                        detailPage="organize"
                        viewModel.openCategoryOrganizer()
                    },
                    onSaveCategory=viewModel::saveCategoryRule,
                    onDeleteCategory=viewModel::deleteCategoryRule,
                    onDeletePlatformCategory=viewModel::deletePlatformCategoryRule,
                    onSaveMerchantAlias=viewModel::saveMerchantAlias,
                    onDeleteMerchantAlias=viewModel::deleteMerchantAlias,
                    onSaveProductAlias=viewModel::saveProductAlias,
                    onDeleteProductAlias=viewModel::deleteProductAlias
                )
                "scan" -> ScanCenterScreen(
                    state=state,
                    onBack={detailPage=null},
                    onPlatformChange=viewModel::setPlatformFilter,
                    onUpdateCategory=viewModel::updateCategory,
                    onSaveLabel=viewModel::saveScanMerchantLabel,
                    onRemoveLabel=viewModel::removeScanMerchantLabel,
                    onAddManual=viewModel::addManualScanExpense,
                    onLink=viewModel::linkManualScanExpense,
                    onUnlink=viewModel::unlinkManualScanExpense,
                    onDeleteManual=viewModel::deleteManualScanExpense,
                    onOpenRules={detailPage="rules"},
                )
                "trends" -> TrendScreen(
                    state=state,onBack={detailPage=null},
                    onSelect=viewModel::selectTrendRange,
                    onClear=viewModel::clearTrendSelection,
                    onPlatformChange=viewModel::setPlatformFilter
                )
                else -> when (selected) {
                0 -> HomeScreen(
                    state=state,
                    onImport=openImport,
                    onPlatformChange=viewModel::setPlatformFilter,
                    onOpenAnalysis={selected=2},
                    onSelectMonth=viewModel::setHomeMonth,
                    onSelectPeriod=viewModel::setHomePeriod,
                    onOpenOrganize={
                        detailPage="organize"
                        viewModel.openCategoryOrganizer()
                    },
                    onOpenFinance={
                        selected=3
                        viewModel.openFinanceInstallments()
                    },
                    onOpenLedgerFilter={ filter ->
                        reviewMode=false
                        viewModel.setSearchFlowFilter(filter)
                        selected=1
                    },
                )
                1 -> TransactionsScreen(
                    state=state,
                    onSelectMonth=viewModel::setHomeMonth,
                    onSelectPeriod=viewModel::setHomePeriod,
                    onChangeExpenseCategory=viewModel::changeExpenseCategory,
                    onPreviewExpenseCategory=viewModel::previewExpenseCategory,
                    transactions = state.searchResults,
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    searchFlowFilter = state.searchFlowFilter,
                    onSearchFlowChange = viewModel::setSearchFlowFilter,
                    searchLimit = state.searchLimit,
                    onLoadMore = viewModel::loadMoreSearch,
                    pendingTransactions = state.pendingTransactions,
                    pendingCount = state.pendingCount,
                    reviewMode = reviewMode,
                    onReviewModeChange = { reviewMode = it },
                    platformFilter = state.platformFilter,
                    onPlatformChange = viewModel::setPlatformFilter,
                    onNatureChange = viewModel::updateNature,
                    onCorrectAmount = viewModel::correctImportedAmount,
                    loanDetails = state.loanDetails,
                    onSaveLoan = viewModel::saveLoanDetail,
                    onClearLoan = viewModel::clearLoanDetail,
                    onBulkConfirm = viewModel::bulkConfirmPending,
                    onCreateFinance=viewModel::createInstallment,
                    onOpenFinance={selected=3;viewModel.openFinanceInstallments()},
                )
                2 -> AnalysisScreen(
                    state=state,
                    onPlatformChange=viewModel::setPlatformFilter,
                    onSelectMonth=viewModel::setHomeMonth,
                    onSelectPeriod=viewModel::setHomePeriod,
                    onChangeExpenseCategory=viewModel::changeExpenseCategory,
                    onPreviewExpenseCategory=viewModel::previewExpenseCategory,
                    onOpenOrganize={
                        detailPage="organize"
                        viewModel.openCategoryOrganizer()
                    },
                    onOpenFinance={
                        selected=3
                        viewModel.openFinanceInstallments()
                    },
                    onOpenTrends={detailPage="trends"},
                )
                3 -> FinancialCenterScreen(
                    state=state,
                    onYearChange=viewModel::setFinanceYear,
                    onRecheck=viewModel::recheckFinancialHistory,
                    onOpenCredit={
                        viewModel.setHomeMonth(java.time.YearMonth.of(state.financeYear,12))
                        detailPage="credit"
                    },
                    onOpenLoan={
                        viewModel.setHomeMonth(java.time.YearMonth.of(state.financeYear,12))
                        detailPage="loans"
                    },
                    onSearchOrigins=viewModel::searchFinanceOrigins,
                    onCreate=viewModel::createInstallment,
                    onUpdate=viewModel::updateInstallment,
                    onDelete=viewModel::deleteInstallment,
                    onSearchLinks=viewModel::searchFinanceLinks,
                    onLink=viewModel::associateInstallment,
                    onUnlink=viewModel::dissociateInstallment,
                )
                else -> SettingsScreen(
                    state = state,
                    onImport = openImport,
                    onExportBackup = startBackup,
                    onRestoreBackup = startRestore,
                    onRunAudit = viewModel::runDataAudit,
                    onOpenAnalysis = { selected = 2 },
                    onOpenPending = { reviewMode = true; selected = 1 },
                    onOpenCredit={detailPage="credit"},
                    onOpenLoan={detailPage="loans"},
                    onOpenRules={detailPage="rules"},
                    onOpenOrganize={detailPage="organize";viewModel.openCategoryOrganizer()},
                    onOpenTrends={detailPage="trends"},
                    onOpenScan={detailPage="scan"},
                )
                }
            }

            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}
