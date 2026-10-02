package com.sockc.billinsight.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.time.LocalDate
import com.sockc.billinsight.BillInsightViewModel
import com.sockc.billinsight.ui.screens.AnalysisScreen
import com.sockc.billinsight.ui.screens.DiscoverScreen
import com.sockc.billinsight.ui.screens.HomeScreen
import com.sockc.billinsight.ui.screens.SettingsScreen
import com.sockc.billinsight.ui.screens.TransactionsScreen

private data class Destination(val label: String, val short: String)

@Composable
fun BillInsightApp(viewModel: BillInsightViewModel) {
    val state by viewModel.uiState.collectAsState()
    var selected by remember { mutableIntStateOf(0) }
    var reviewMode by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val destinations = remember {
        listOf(
            Destination("首页", "首"),
            Destination("流水", "流"),
            Destination("分析", "析"),
            Destination("发现", "发"),
            Destination("我的", "我"),
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
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
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
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Text(item.short) },
                        label = { Text(item.label) },
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                0 -> HomeScreen(
                    state = state,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                    onImport = openImport,
                    onPlatformChange = viewModel::setPlatformFilter,
                    onReviewPending = { reviewMode = true; selected = 1 },
                )
                1 -> TransactionsScreen(
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
                )
                2 -> AnalysisScreen(
                    state = state,
                    onPlatformChange = viewModel::setPlatformFilter,
                    onProductAlias = viewModel::saveProductAlias,
                    onLinkRecovery = viewModel::linkRecovery,
                    onDeleteLink = viewModel::unlinkRecovery,
                )
                3 -> DiscoverScreen(
                    state = state,
                    onPlatformChange = viewModel::setPlatformFilter,
                    onSmallThresholdChange = viewModel::setSmallThreshold,
                )
                else -> SettingsScreen(
                    state = state,
                    onImport = openImport,
                    onExportBackup = startBackup,
                    onRestoreBackup = startRestore,
                )
            }

            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}
