package com.sockc.billinsight

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sockc.billinsight.data.BillDatabase
import com.sockc.billinsight.importer.BillImporter
import com.sockc.billinsight.importer.PasswordRequiredException
import com.sockc.billinsight.model.CategoryTotal
import com.sockc.billinsight.model.DashboardSummary
import com.sockc.billinsight.model.ImportResult
import com.sockc.billinsight.model.MerchantTotal
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.currentYearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

class BillInsightViewModel(application: Application) : AndroidViewModel(application) {
    private val db = BillDatabase(application)
    private val importer = BillImporter(application, application.contentResolver)
    private var pendingImportUri: Uri? = null

    private val _uiState = MutableStateFlow(BillUiState())
    val uiState: StateFlow<BillUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh(month: YearMonth = _uiState.value.month) {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                BillUiState(
                    month = month,
                    summary = db.summary(month),
                    transactions = db.loadTransactions(),
                    categories = db.categoryTotals(month),
                    merchants = db.merchantTotals(month),
                    largestExpenses = db.largestExpenses(month),
                    totalStored = db.transactionCount(),
                    isLoading = false,
                    message = _uiState.value.message,
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
                    )
                }
            }
            result.onSuccess { imported ->
                pendingImportUri = null
                _uiState.value = _uiState.value.copy(
                    message = "导入 ${imported.inserted} 笔，跳过重复 ${imported.duplicated} 笔",
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
            _uiState.value = _uiState.value.copy(message = "已改为 $category${if (rememberMerchant) "，并记住该商户" else ""}")
            refresh()
        }
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
}

data class BillUiState(
    val month: YearMonth = currentYearMonth(),
    val summary: DashboardSummary = DashboardSummary(),
    val transactions: List<Transaction> = emptyList(),
    val categories: List<CategoryTotal> = emptyList(),
    val merchants: List<MerchantTotal> = emptyList(),
    val largestExpenses: List<Transaction> = emptyList(),
    val totalStored: Int = 0,
    val isLoading: Boolean = true,
    val message: String? = null,
    val needsZipPassword: Boolean = false,
)
