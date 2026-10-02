package com.sockc.billinsight

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sockc.billinsight.data.BillDatabase
import com.sockc.billinsight.importer.BillImporter
import com.sockc.billinsight.importer.PasswordRequiredException
import com.sockc.billinsight.model.CategoryTotal
import com.sockc.billinsight.model.DailyTotal
import com.sockc.billinsight.model.DashboardSummary
import com.sockc.billinsight.model.ImportResult
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.MerchantTotal
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
    private var pendingImportUri: Uri? = null

    private val _uiState = MutableStateFlow(BillUiState())
    val uiState: StateFlow<BillUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh(
        month: YearMonth = _uiState.value.month,
        platform: Platform? = _uiState.value.platformFilter,
        smallThresholdYuan: Int = _uiState.value.smallThresholdYuan,
    ) {
        viewModelScope.launch {
            val previousMessage = _uiState.value.message
            _uiState.value = _uiState.value.copy(isLoading = true)
            val state = withContext(Dispatchers.IO) {
                val thresholdCent = smallThresholdYuan * 100L
                val categories = db.categoryTotals(month, platform)
                BillUiState(
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
            _uiState.value = state
        }
    }

    fun importBill(uri: Uri, zipPassword: String? = null) {
        _uiState.value = _uiState.value.copy(isLoading = true, message = null, needsZipPassword = false)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val parsed = importer.parse(uri, db.merchantRules(), zipPassword)
                    val (inserted, duplicated) = db.insertAll(parsed.transactions)
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
                db.updateCategory(transaction.id, transaction.counterparty, category, rememberMerchant)
            }
            _uiState.value = _uiState.value.copy(
                message = "已改为 $category${if (rememberMerchant) "，并记住该商户" else ""}"
            )
            refresh()
        }
    }

    fun updateNature(transaction: Transaction, flowType: FlowType, category: String) {
        viewModelScope.launch {
            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    db.updateNature(transaction.id, flowType, category)
                }
            }
            _uiState.value = _uiState.value.copy(
                message = if (saved.isSuccess) "交易性质已保存" else saved.exceptionOrNull()?.message ?: "保存失败"
            )
            if (saved.isSuccess) refresh()
        }
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
