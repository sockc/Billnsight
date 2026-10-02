package com.sockc.billinsight

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sockc.billinsight.analysis.ProductAnalysis
import com.sockc.billinsight.analysis.MerchantAnalysis
import com.sockc.billinsight.analysis.MerchantGroup
import com.sockc.billinsight.data.BillDatabase
import com.sockc.billinsight.data.BackupManager
import com.sockc.billinsight.data.DataAuditReport
import com.sockc.billinsight.importer.BillImporter
import com.sockc.billinsight.importer.PasswordRequiredException
import com.sockc.billinsight.model.CategoryTotal
import com.sockc.billinsight.model.DailyTotal
import com.sockc.billinsight.model.DashboardSummary
import com.sockc.billinsight.model.ImportResult
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.LoanRepaymentDetail
import com.sockc.billinsight.model.LinkKind
import com.sockc.billinsight.model.ExpenseLink
import com.sockc.billinsight.model.MerchantTotal
import com.sockc.billinsight.model.ProductGroup
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.RecurringExpense
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.currentYearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BillInsightViewModel(application: Application) : AndroidViewModel(application) {
    private val db = BillDatabase(application)
    private val importer = BillImporter(application, application.contentResolver)
    private val backupManager = BackupManager(application, db)
    private var pendingImportUri: Uri? = null
    private var latestRefresh = 0

    private val _uiState = MutableStateFlow(BillUiState())
    val uiState: StateFlow<BillUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh(
        month: YearMonth = _uiState.value.month,
        platform: Platform? = _uiState.value.platformFilter,
        smallThresholdYuan: Int = _uiState.value.smallThresholdYuan,
        searchQuery: String = _uiState.value.searchQuery,
        searchFlowFilter: String = _uiState.value.searchFlowFilter,
        searchLimit: Int = _uiState.value.searchLimit,
        merchantPeriod: String = _uiState.value.merchantPeriod,
    ) {
        val ticket = ++latestRefresh
        viewModelScope.launch {
            val previousMessage = _uiState.value.message
            _uiState.value = _uiState.value.copy(isLoading = true)
            val state = withContext(Dispatchers.IO) {
                synchronized(db) {
                val thresholdCent = smallThresholdYuan * 100L
                val categories = db.categoryTotals(month, platform)
                val monthly = db.monthTransactions(month, platform)
                val aliases = db.productAliases()
                val merchantAliases = db.merchantAliases()
                val history = if (merchantPeriod == "MONTH") monthly else
                    db.merchantHistoryTransactions(month, platform, merchantPeriod)
                BillUiState(
                    searchQuery = searchQuery,
                    searchFlowFilter = searchFlowFilter,
                    searchLimit = searchLimit,
                    searchResults = db.searchTransactions(searchQuery, platform, searchFlowFilter, searchLimit),
                    monthlyTransactions = monthly,
                    loanDetails = db.loanDetails(),
                    productGroups = ProductAnalysis.groups(monthly, aliases, merchantAliases),
                    productAliases = aliases,
                    merchantAliases = merchantAliases,
                    merchantPeriod = merchantPeriod,
                    merchantGroups = MerchantAnalysis.groups(history, merchantAliases),
                    monthlyMerchantGroups = MerchantAnalysis.groups(monthly, merchantAliases),
                    links = db.linksForMonth(month, platform),
                    linkedReceiptIds = db.linkedReceiptIds(),
                    linkableReceipts = db.searchTransactions("", null, "INCOME", 2000)
                        .filter { it.flowType in setOf(FlowType.INCOME, FlowType.REFUND) },
                    month = month,
                    platformFilter = platform,
                    smallThresholdYuan = smallThresholdYuan,
                    summary = db.summary(month, platform, thresholdCent),
                    previousSummary = db.summary(month.minusMonths(1), platform, thresholdCent),
                    transactions = db.loadTransactions(platform),
                    pendingTransactions = db.pendingTransactions(platform),
                    pendingCount = db.pendingTotal(platform),
                    categories = categories,
                    merchants = db.merchantTotals(month, platform),
                    categoryMerchants = categories.associate { category ->
                        category.category to db.merchantTotals(
                            month = month,
                            platform = platform,
                            category = category.category,
                            limit = 8,
                        )
                    },
                    largestExpenses = db.largestExpenses(month, platform),
                    dailyTotals = db.dailyTotals(month, platform),
                    recurringExpenses = db.recurringExpenses(month, platform),
                    totalStored = db.transactionCount(),
                    isLoading = false,
                    message = previousMessage,
                )
                }
            }
            if (ticket == latestRefresh) _uiState.value = state
        }
    }

    fun importBill(uri: Uri, zipPassword: String? = null) {
        _uiState.value = _uiState.value.copy(isLoading = true, message = null, needsZipPassword = false)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val parsed = importer.parse(uri, synchronized(db) { db.merchantRules() }, zipPassword)
                    val (inserted, duplicated) = synchronized(db) { db.insertAll(parsed.transactions) }
                    ImportResult(
                        parsed = parsed.transactions.size,
                        inserted = inserted,
                        duplicated = duplicated,
                        ignored = parsed.transactions.count { it.flowType.name == "IGNORE" },
                        platform = parsed.platform,
                        sourceName = parsed.sourceName,
                        startAt = parsed.transactions.minOfOrNull { it.occurredAt },
                        endAt = parsed.transactions.maxOfOrNull { it.occurredAt },
                    )
                }
            }
            result.onSuccess { imported ->
                pendingImportUri = null
                val source = when (imported.platform) {
                    Platform.WECHAT -> "微信账单"
                    Platform.ALIPAY -> "支付宝账单"
                    Platform.UNKNOWN -> "账单"
                }
                val range = if (imported.startAt != null && imported.endAt != null) {
                    " · ${formatDate(imported.startAt)}～${formatDate(imported.endAt)}"
                } else {
                    ""
                }
                _uiState.value = _uiState.value.copy(
                    message = "$source$range · 导入 ${imported.inserted} 笔 · 重复 ${imported.duplicated} 笔",
                    isLoading = false,
                    needsZipPassword = false,
                )
                refresh()
            }.onFailure { error ->
                if (error is PasswordRequiredException) {
                    pendingImportUri = uri
                    _uiState.value = _uiState.value.copy(
                        needsZipPassword = true,
                        message = null,
                        isLoading = false,
                    )
                } else {
                    val retryPassword = zipPassword != null && pendingImportUri != null
                    _uiState.value = _uiState.value.copy(
                        message = error.message ?: "导入失败",
                        isLoading = false,
                        needsZipPassword = retryPassword,
                    )
                }
            }
        }
    }

    fun updateCategory(transaction: Transaction, category: String, rememberMerchant: Boolean = true) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                synchronized(db) { db.updateCategory(transaction.id, transaction.counterparty, category, rememberMerchant) }
            }
            _uiState.value = _uiState.value.copy(
                message = "已改为 $category${if (rememberMerchant) "，并记住该商户" else ""}"
            )
            refresh()
        }
    }


    fun recheckCreditRepayments() {
        if (_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(message = "请等待当前操作完成")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    synchronized(db) { db.recheckCreditRepayments() }
                }
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = result.fold(
                    onSuccess = { count ->
                        if (count > 0) "已补正 $count 笔旧账单信用卡还款，请核对当前月份和来源"
                        else "旧账单中未找到可安全自动修正的还款；请检查月份、来源和原始账单"
                    },
                    onFailure = { it.message ?: "重新识别失败，请保留原始账单" }
                )
            )
            if (result.isSuccess) refresh()
        }
    }

    fun saveLoanDetail(
        transactionId: Long, principalCent: Long, interestCent: Long, feeCent: Long
    ) {
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    synchronized(db) {
                        db.saveLoanDetail(transactionId,principalCent,interestCent,feeCent)
                    }
                }
            }
            _uiState.value = _uiState.value.copy(
                dataAudit = null,
                message = outcome.fold(
                    onSuccess = { "贷款本金、利息和手续费已保存" },
                    onFailure = { it.message ?: "贷款还款拆分保存失败" }
                )
            )
            if (outcome.isSuccess) refresh()
        }
    }

    fun clearLoanDetail(transactionId: Long) {
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    synchronized(db) { db.clearLoanDetail(transactionId) }
                }
            }
            _uiState.value = _uiState.value.copy(
                dataAudit = null,
                message = if (outcome.isSuccess) "已恢复为未拆分贷款还款"
                else outcome.exceptionOrNull()?.message ?: "撤销拆分失败"
            )
            if (outcome.isSuccess) refresh()
        }
    }

    fun setMerchantPeriod(period: String) {
        if (period !in setOf("MONTH","THREE_MONTHS","ALL")) return
        if (period == _uiState.value.merchantPeriod) return
        refresh(merchantPeriod = period)
    }

    fun saveMerchantAlias(original: String, canonical: String) {
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    synchronized(db) { db.saveMerchantAlias(original, canonical) }
                }
            }
            _uiState.value = _uiState.value.copy(
                dataAudit = null,
                message = if (outcome.isSuccess) "已合并商户排行，原始账单保留不变"
                else outcome.exceptionOrNull()?.message ?: "合并商户失败"
            )
            if (outcome.isSuccess) refresh()
        }
    }

    fun bulkConfirmPending(ids: List<Long>, nature: FlowType, category: String) {
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    synchronized(db) { db.bulkConfirmPending(ids, nature, category) }
                }
            }
            _uiState.value = _uiState.value.copy(
                dataAudit = null,
                message = outcome.fold(
                    onSuccess = { "已确认 $it 笔交易" },
                    onFailure = { it.message ?: "批量确认失败，未修改任何记录" },
                )
            )
            if (outcome.isSuccess) refresh()
        }
    }

    fun runDataAudit() {
        if (_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(message = "请等待当前操作完成")
            return
        }
        viewModelScope.launch {
            val month = _uiState.value.month
            val platform = _uiState.value.platformFilter
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    synchronized(db) { db.dataAudit(month, platform) }
                }
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                dataAudit = outcome.getOrNull(),
                auditTime = if (outcome.isSuccess) System.currentTimeMillis() else null,
                message = outcome.exceptionOrNull()?.message,
            )
        }
    }

    fun saveProductAlias(merchant: String, alias: String, canonical: String) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { synchronized(db) { db.saveProductAlias(merchant, alias, canonical) } }
            }
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "同一商户商品名称已合并"
                          else result.exceptionOrNull()?.message ?: "合并失败"
            )
            if (result.isSuccess) refresh()
        }
    }

    fun linkRecovery(expenseId: Long, receiptId: Long, kind: LinkKind, amountCent: Long) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { synchronized(db) { db.createLink(expenseId, receiptId, kind, amountCent) } }
            }
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "关联成功，已重新计算净消费"
                          else result.exceptionOrNull()?.message ?: "关联失败"
            )
            if (result.isSuccess) refresh()
        }
    }

    fun unlinkRecovery(linkId: Long) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { synchronized(db) { db.deleteLink(linkId) } }
            }
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "已撤销关联"
                          else result.exceptionOrNull()?.message ?: "撤销失败"
            )
            if (result.isSuccess) refresh()
        }
    }

    fun updateNature(transaction: Transaction, flowType: FlowType, category: String) {
        viewModelScope.launch {
            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    synchronized(db) { db.updateNature(transaction.id, flowType, category) }
                }
            }
            _uiState.value = _uiState.value.copy(
                message = if (saved.isSuccess) "交易性质已保存" else saved.exceptionOrNull()?.message ?: "保存失败"
            )
            if (saved.isSuccess) refresh()
        }
    }


    fun exportEncryptedBackup(uri: Uri, password: String) {
        if (_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(message = "请先完成当前操作")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, message = null)
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) { synchronized(db) { backupManager.exportEncrypted(uri, password) } }
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = if (outcome.isSuccess) "加密账本备份已保存"
                    else outcome.exceptionOrNull()?.message ?: "备份失败",
            )
        }
    }

    fun restoreEncryptedBackup(uri: Uri, password: String) {
        if (_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(message = "请先完成当前操作")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, message = null)
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) { synchronized(db) { backupManager.restoreEncrypted(uri, password) } }
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = if (outcome.isSuccess) "加密账本已恢复，全部统计重新加载"
                    else outcome.exceptionOrNull()?.message ?: "恢复失败",
            )
            if (outcome.isSuccess) refresh()
        }
    }

    fun setSearchQuery(query: String) {
        val value = query.take(100)
        _uiState.value = _uiState.value.copy(searchQuery = value, searchLimit = 200)
        refresh(searchQuery = value, searchLimit = 200)
    }

    fun setSearchFlowFilter(flow: String) {
        if (flow !in setOf("ALL", "EXPENSE", "INCOME", "OTHER")) return
        _uiState.value = _uiState.value.copy(searchFlowFilter = flow, searchLimit = 200)
        refresh(searchFlowFilter = flow, searchLimit = 200)
    }

    fun loadMoreSearch() {
        val next = (_uiState.value.searchLimit + 200).coerceAtMost(10000)
        if (next != _uiState.value.searchLimit) refresh(searchLimit = next)
    }

    fun setPlatformFilter(platform: Platform?) {
        if (_uiState.value.platformFilter == platform) return
        refresh(platform = platform)
    }

    fun setSmallThreshold(yuan: Int) {
        if (yuan !in setOf(20, 50, 100) || _uiState.value.smallThresholdYuan == yuan) return
        refresh(smallThresholdYuan = yuan)
    }

    fun submitZipPassword(password: String) {
        pendingImportUri?.let { importBill(it, password) }
    }

    fun cancelZipPassword() {
        pendingImportUri = null
        _uiState.value = _uiState.value.copy(needsZipPassword = false)
    }

    fun previousMonth() = refresh(_uiState.value.month.minusMonths(1))
    fun nextMonth() = refresh(_uiState.value.month.plusMonths(1))
    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }

    private fun formatDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}

data class BillUiState(
    val month: YearMonth = currentYearMonth(),
    val platformFilter: Platform? = null,
    val smallThresholdYuan: Int = 50,
    val summary: DashboardSummary = DashboardSummary(),
    val previousSummary: DashboardSummary = DashboardSummary(),
    val transactions: List<Transaction> = emptyList(),
    val monthlyTransactions: List<Transaction> = emptyList(),
    val loanDetails: Map<Long, LoanRepaymentDetail> = emptyMap(),
    val productGroups: List<ProductGroup> = emptyList(),
    val productAliases: Map<String,String> = emptyMap(),
    val merchantAliases: Map<String,String> = emptyMap(),
    val merchantPeriod: String = "MONTH",
    val merchantGroups: List<MerchantGroup> = emptyList(),
    val monthlyMerchantGroups: List<MerchantGroup> = emptyList(),
    val dataAudit: DataAuditReport? = null,
    val auditTime: Long? = null,
    val links: List<ExpenseLink> = emptyList(),
    val linkedReceiptIds: Set<Long> = emptySet(),
    val linkableReceipts: List<Transaction> = emptyList(),
    val searchQuery: String = "",
    val searchFlowFilter: String = "ALL",
    val searchLimit: Int = 200,
    val searchResults: List<Transaction> = emptyList(),
    val pendingTransactions: List<Transaction> = emptyList(),
    val pendingCount: Int = 0,
    val categories: List<CategoryTotal> = emptyList(),
    val merchants: List<MerchantTotal> = emptyList(),
    val categoryMerchants: Map<String, List<MerchantTotal>> = emptyMap(),
    val largestExpenses: List<Transaction> = emptyList(),
    val dailyTotals: List<DailyTotal> = emptyList(),
    val recurringExpenses: List<RecurringExpense> = emptyList(),
    val totalStored: Int = 0,
    val isLoading: Boolean = true,
    val message: String? = null,
    val needsZipPassword: Boolean = false,
)
