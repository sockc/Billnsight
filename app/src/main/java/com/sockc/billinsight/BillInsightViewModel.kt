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
import com.sockc.billinsight.importer.ImportReview
import com.sockc.billinsight.importer.ScanPaymentClassifier
import com.sockc.billinsight.importer.PasswordRequiredException
import com.sockc.billinsight.model.CategoryTotal
import com.sockc.billinsight.model.CategoryEditPreview
import com.sockc.billinsight.model.DailyTotal
import com.sockc.billinsight.model.DashboardSummary
import com.sockc.billinsight.model.ImportResult
import com.sockc.billinsight.model.FlowType
import com.sockc.billinsight.model.LoanRepaymentDetail
import com.sockc.billinsight.model.ImportPreview
import com.sockc.billinsight.model.CreditCenter
import com.sockc.billinsight.model.LoanProfile
import com.sockc.billinsight.model.ManualCreditRepayment
import com.sockc.billinsight.model.MerchantRule
import com.sockc.billinsight.model.TrendPoint
import com.sockc.billinsight.model.LinkKind
import com.sockc.billinsight.model.ExpenseLink
import com.sockc.billinsight.model.MerchantTotal
import com.sockc.billinsight.model.ProductGroup
import com.sockc.billinsight.model.ReportPeriod
import com.sockc.billinsight.model.PlatformCategoryRule
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
import java.time.LocalDate
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BillInsightViewModel(application: Application) : AndroidViewModel(application) {
    private val db = BillDatabase(application)
    private val importer = BillImporter(application, application.contentResolver)
    private val backupManager = BackupManager(application, db)
    private var pendingImportUri: Uri? = null
    private var pendingParsedBill: BillImporter.ParsedBill? = null
    private var latestRefresh = 0
    private var homePeriodKey = "MONTH"
    private var homeCustomStart: LocalDate? = null
    private var homeCustomEnd: LocalDate? = null

    private val _uiState = MutableStateFlow(BillUiState())
    val uiState: StateFlow<BillUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun setHomePeriod(key: String, customStart: LocalDate? = null, customEnd: LocalDate? = null) {
        require(key in setOf("MONTH","LAST_MONTH","LAST_7","YEAR","LAST_YEAR","CUSTOM","ALL_HISTORY"))
        if (key == "CUSTOM") require(customStart != null && customEnd != null && !customStart.isAfter(customEnd))
        homePeriodKey=key
        homeCustomStart=customStart
        homeCustomEnd=customEnd
        refresh()
    }

    fun setHomeMonth(month: YearMonth) {
        homePeriodKey="MONTH"
        homeCustomStart=null
        homeCustomEnd=null
        refresh(month=month)
    }

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
        val homeKey=homePeriodKey
        val customStart=homeCustomStart
        val customEnd=homeCustomEnd
        val now=LocalDate.now()
        val homeDates=ReportPeriod.resolve(homeKey,month,customStart,customEnd,now)
        val homeStartMillis=homeDates.first.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val homeEndMillis=homeDates.second.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        viewModelScope.launch {
            val previousMessage = _uiState.value.message
            val previousPreview = _uiState.value.importPreview
            val previousReport = _uiState.value.lastImportResult
            val previousAudit = _uiState.value.dataAudit
            val previousCategoryPreviewId=_uiState.value.categoryPreviewId
            val previousCategoryPreviewCount=_uiState.value.categoryPreviewCount
            val previousCategoryPreview=_uiState.value.categoryPreview
            val sameScope=_uiState.value.month==month &&
                _uiState.value.platformFilter==platform
            val previousTrendDetails=if(sameScope) _uiState.value.trendDetails
                else emptyList()
            val previousTrendLabel=if(sameScope) _uiState.value.trendSelectionLabel
                else null
            _uiState.value = _uiState.value.copy(isLoading = true)
            val state = withContext(Dispatchers.IO) {
                synchronized(db) {
                val thresholdCent = smallThresholdYuan * 100L
                val categories = db.categoryTotals(month, platform)
                val rangeRows = db.rangeTransactions(homeStartMillis,homeEndMillis,platform,10000)
                val monthly = db.monthTransactions(month, platform)
                val aliases = db.productAliases()
                val merchantAliases = db.merchantAliases()
                val scanLabels = db.scanMerchantLabels()
                val history = if (merchantPeriod == "MONTH") monthly else
                    db.merchantHistoryTransactions(month, platform, merchantPeriod)
                BillUiState(
                    categoryPreviewId=previousCategoryPreviewId,
                    categoryPreviewCount=previousCategoryPreviewCount,
                    categoryPreview=previousCategoryPreview,
                    searchQuery = searchQuery,
                    searchFlowFilter = searchFlowFilter,
                    searchLimit = searchLimit,
                    searchResults = db.searchTransactions(searchQuery, platform, searchFlowFilter, searchLimit,homeStartMillis,homeEndMillis),
                    monthlyTransactions = monthly,
                    periodTransactions = rangeRows,
                    periodCategories = db.rangeCategoryTotals(homeStartMillis,homeEndMillis,platform),
                    periodManualRepayments = if(platform==null) db.rangeManualCreditPayments(homeStartMillis,homeEndMillis) else emptyList(),
                    scanMerchantLabels = scanLabels,
                    manualScanLinks = db.manualScanLinks(),
                    scanHistory = db.scanHistory(),
                    creditCenter = db.creditCenter(month),
                    creditHistory = db.creditHistory(),
                    loanHistory = db.loanHistory(),
                    loanProfiles = db.loanProfiles(),
                    categoryRules = db.categoryRules(),
                    platformCategoryRules = db.platformCategoryRules(),
                    trendDays = db.trend30(month,platform),
                    trendMonths = db.trend12(month,platform),
                    trendDetails = previousTrendDetails,
                    trendSelectionLabel = previousTrendLabel,
                    loanDetails = db.loanDetails(),
                    productGroups = ProductAnalysis.groups(monthly, aliases, merchantAliases, scanLabels),
                    productAliases = aliases,
                    merchantAliases = merchantAliases,
                    merchantPeriod = merchantPeriod,
                    merchantGroups = MerchantAnalysis.groups(history, merchantAliases, scanLabels),
                    monthlyMerchantGroups = MerchantAnalysis.groups(monthly, merchantAliases, scanLabels),
                    links = db.linksForMonth(month, platform),
                    linkedReceiptIds = db.linkedReceiptIds(),
                    linkableReceipts = db.searchTransactions("", null, "INCOME", 2000)
                        .filter { it.flowType in setOf(FlowType.INCOME, FlowType.REFUND) },
                    month = month,
                    homePeriod = homeKey,
                    homeStart = homeDates.first,
                    homeEnd = homeDates.second,
                    homeSummary = db.homeRangeSummary(homeStartMillis,homeEndMillis,platform),
                    homeRecent = db.homeRangeTransactions(homeStartMillis,homeEndMillis,platform),
                    platformFilter = platform,
                    smallThresholdYuan = smallThresholdYuan,
                    summary = db.summary(month, platform, thresholdCent),
                    previousSummary = db.summary(month.minusMonths(1), platform, thresholdCent),
                    transactions = db.loadTransactions(platform),
                    pendingTransactions = db.pendingTransactions(platform,200,homeStartMillis,homeEndMillis),
                    pendingCount = db.pendingTotal(platform,homeStartMillis,homeEndMillis),
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
                    importPreview = previousPreview,
                    lastImportResult = previousReport,
                    dataAudit = previousAudit,
                    isLoading = false,
                    message = previousMessage,
                )
                }
            }
            if (ticket == latestRefresh) _uiState.value = state
        }
    }

    /**
     * Parsing is read-only. Wait for the user's confirmation before inserting any row.
     * A ZIP password is needed only to parse the chosen archive.
     */
    fun importBill(uri: Uri, zipPassword: String? = null) {
        if (_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(message="请先完成当前操作")
            return
        }
        pendingParsedBill=null
        _uiState.value = _uiState.value.copy(
            isLoading=true,message=null,needsZipPassword=false,
            importPreview=null,lastImportResult=null
        )
        viewModelScope.launch {
            val outcome=runCatching {
                withContext(Dispatchers.IO) {
                    val raw=importer.parse(
                        uri,synchronized(db) { db.merchantRules() },zipPassword
                    )
                    val parsed=synchronized(db) {
                        raw.copy(transactions=db.applyPlatformCategoryRules(db.applyMerchantNatureRules(raw.transactions)))
                    }
                    val fingerprints=synchronized(db) {
                        db.existingFingerprints(parsed.transactions.map { it.fingerprint }) +
                            db.crossPlatformDuplicateFingerprints(parsed.transactions)
                    }
                    parsed to ImportReview.preview(parsed,fingerprints)
                }
            }
            outcome.onSuccess { (parsed,preview) ->
                pendingImportUri=null
                if (!preview.canCommit) {
                    // Only malformed or unsupported bills need intervention.
                    pendingParsedBill=parsed
                    _uiState.value=_uiState.value.copy(
                        importPreview=preview,isLoading=false,needsZipPassword=false,
                        message="账单来源或金额异常，请检查原文件；未写入任何记录"
                    )
                } else {
                    val saved=runCatching {
                        withContext(Dispatchers.IO) {
                            synchronized(db) { db.insertAll(parsed.transactions) }
                        }
                    }
                    saved.onSuccess { (inserted,duplicates) ->
                        pendingParsedBill=null
                        _uiState.value=_uiState.value.copy(
                            importPreview=null,isLoading=false,needsZipPassword=false,
                            lastImportResult=null,
                            message="已自动识别并导入 "+inserted+" 笔，跳过重复 "+
                                duplicates+" 笔"
                        )
                        val newest=parsed.transactions
                            .filter { it.flowType!=FlowType.IGNORE }
                            .maxOfOrNull { it.occurredAt }
                        val target=if(inserted>0 && newest!=null)
                            YearMonth.from(
                                Instant.ofEpochMilli(newest)
                                    .atZone(ZoneId.systemDefault())
                            ) else _uiState.value.month
                        refresh(month=target)
                    }.onFailure { error ->
                        pendingParsedBill=parsed
                        _uiState.value=_uiState.value.copy(
                            importPreview=preview,isLoading=false,
                            message=(error.message?:"保存账单失败")+"；未丢弃解析结果"
                        )
                    }
                }
            }.onFailure { error ->
                if (error is PasswordRequiredException) {
                    pendingImportUri=uri
                    _uiState.value=_uiState.value.copy(
                        needsZipPassword=true,isLoading=false,message=null
                    )
                } else {
                    _uiState.value=_uiState.value.copy(
                        isLoading=false,
                        needsZipPassword=zipPassword!=null && pendingImportUri!=null,
                        message=error.message?:"解析账单失败；未写入任何数据"
                    )
                }
            }
        }
    }

    fun confirmImport() {
        val parsed=pendingParsedBill ?: return
        val preview=_uiState.value.importPreview ?: return
        if (_uiState.value.isLoading || !preview.canCommit) {
            _uiState.value=_uiState.value.copy(message="请先解决导入预览中的异常")
            return
        }
        _uiState.value=_uiState.value.copy(isLoading=true)
        viewModelScope.launch {
            val outcome=runCatching {
                withContext(Dispatchers.IO) {
                    synchronized(db) {
                        val (inserted,duplicates)=db.insertAll(parsed.transactions)
                        ImportResult(
                            parsed=parsed.transactions.size,inserted=inserted,
                            duplicated=duplicates,
                            ignored=parsed.transactions.count { it.flowType==FlowType.IGNORE },
                            platform=parsed.platform,sourceName=parsed.sourceName,
                            startAt=preview.startAt,endAt=preview.endAt,
                            qrExpenseCount=preview.qrExpenseCount,
                            qrMerchantReviewCount=preview.qrMerchantReviewCount,
                        )
                    }
                }
            }
            outcome.onSuccess { result ->
                pendingParsedBill=null
                _uiState.value=_uiState.value.copy(
                    isLoading=false,importPreview=null,lastImportResult=result,
                    message="导入完成：新增 "+result.inserted+" 笔，重复 "+
                        result.duplicated+" 笔"
                )
                refresh()
            }.onFailure { error ->
                _uiState.value=_uiState.value.copy(
                    isLoading=false,
                    message=(error.message?:"保存账单失败") + "；可重新确认或取消"
                )
            }
        }
    }

    fun cancelImportPreview() {
        pendingParsedBill=null
        pendingImportUri=null
        _uiState.value=_uiState.value.copy(importPreview=null,needsZipPassword=false)
    }

    fun clearImportReport() {
        _uiState.value=_uiState.value.copy(lastImportResult=null)
    }

    fun updateCategory(
        transaction:Transaction, category:String, rememberMerchant:Boolean=true
    ) {
        val meaningfulMerchant=rememberMerchant &&
            !ScanPaymentClassifier.isGenericCounterparty(transaction.counterparty)
        viewModelScope.launch {
            val result=runCatching {
                withContext(Dispatchers.IO) {
                    synchronized(db) {
                        db.updateCategory(transaction.id,transaction.counterparty,
                            category,meaningfulMerchant)
                    }
                }
            }
            _uiState.value=_uiState.value.copy(
                message=result.fold(
                    onSuccess={
                        "已改为 "+category+
                            if(meaningfulMerchant) "，并记住该商户" else "（仅此交易）"
                    },
                    onFailure={ it.message?:"分类保存失败" }
                )
            )
            if(result.isSuccess) refresh()
        }
    }

    fun previewExpenseCategory(tx: Transaction) {
        _uiState.value=_uiState.value.copy(
            categoryPreviewId=tx.id,categoryPreviewCount=null,categoryPreview=null)
        viewModelScope.launch {
            val result=runCatching { withContext(Dispatchers.IO) {
                synchronized(db) { db.previewExpenseCategory(tx.id) }
            }}
            if(_uiState.value.categoryPreviewId==tx.id) {
                _uiState.value=_uiState.value.copy(
                    categoryPreview=result.getOrNull(),
                    categoryPreviewCount=result.getOrNull()?.eligibleCount,
                    message=result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun changeExpenseCategory(tx: Transaction, category: String, scope: String) {
        if(_uiState.value.isLoading) return
        viewModelScope.launch {
            val outcome=runCatching { withContext(Dispatchers.IO) {
                synchronized(db) { db.changeExpenseCategory(tx.id,category,scope) }
            }}
            _uiState.value=_uiState.value.copy(
                message=outcome.fold(
                    onSuccess={ count -> when(scope) {
                        "FUTURE" -> "已记住该商户以后导入的分类，历史记录不变"
                        "MERCHANT" -> "已修改本笔并更新 ${(count-1).coerceAtLeast(0)} 笔未人工分类的历史消费"
                        else -> "已修改当前这一笔分类"
                    }},
                    onFailure={it.message?:"分类保存失败"}
                ),
                categoryPreviewId=null,
                categoryPreviewCount=null,
                categoryPreview=null
            )
            if(outcome.isSuccess) refresh()
        }
    }

    private fun financeChange(success: String, action: BillDatabase.() -> Unit) {
        if (_uiState.value.isLoading) {
            _uiState.value=_uiState.value.copy(message="请先完成当前操作")
            return
        }
        viewModelScope.launch {
            val outcome=runCatching {
                withContext(Dispatchers.IO) { synchronized(db) { db.action() } }
            }
            _uiState.value=_uiState.value.copy(
                message=if(outcome.isSuccess) success else
                    outcome.exceptionOrNull()?.message?:"操作未保存"
            )
            if(outcome.isSuccess) refresh()
        }
    }

    fun addManualCredit(name:String,amountCent:Long,occurredAt:Long,note:String) =
        financeChange("手动信用卡还款已保存") {
            addManualCreditRepayment(name,amountCent,occurredAt,note)
        }

    fun linkManualCredit(manualId:Long,sourceId:Long) =
        financeChange("已关联原账单，该笔补录不再重复计入还款") {
            linkManualCreditRepayment(manualId,sourceId)
        }

    fun unlinkManualCredit(manualId:Long) =
        financeChange("已取消原账单关联，请核对是否重复") {
            unlinkManualCreditRepayment(manualId)
        }

    fun deleteManualCredit(manualId:Long) =
        financeChange("已删除补录还款，原始导入流水未更改") {
            deleteManualCreditRepayment(manualId)
        }

    fun renameCreditCard(source:String,display:String) =
        financeChange("信用卡显示名称已更新") {
            renameCreditCard(source,display)
        }

    fun saveLoanProfile(institution:String,original:Long?,remaining:Long?) =
        financeChange("贷款档案已保存；余额来自手动填写，不做自动推算") {
            saveLoanProfile(institution,original,remaining)
        }

    fun deleteLoanProfile(institution:String) =
        financeChange("贷款档案已删除，历史还款记录仍保留") {
            deleteLoanProfile(institution)
        }

    fun selectTrendRange(startAt:Long,endAt:Long,label:String) {
        if(startAt>=endAt) return
        viewModelScope.launch {
            val platform=_uiState.value.platformFilter
            val result=runCatching {
                withContext(Dispatchers.IO) {
                    synchronized(db) { db.rangeTransactions(startAt,endAt,platform) }
                }
            }
            _uiState.value=_uiState.value.copy(
                trendDetails=result.getOrDefault(emptyList()),
                trendSelectionLabel=if(result.isSuccess) label else null,
                message=result.exceptionOrNull()?.message
            )
        }
    }

    fun clearTrendSelection() {
        _uiState.value=_uiState.value.copy(
            trendDetails=emptyList(),trendSelectionLabel=null
        )
    }

    fun saveScanMerchantLabel(id:Long,name:String) =
        financeChange("已补充商户名称，原始账单保持不变") {
            saveScanMerchantLabel(id,name)
        }

    fun removeScanMerchantLabel(id:Long) =
        financeChange("已取消自定义商户名称") { removeScanMerchantLabel(id) }

    fun addManualScanExpense(
        merchant:String,amountCent:Long,occurredAt:Long,category:String,note:String
    ) = financeChange("手动扫码消费已记录，导入正式账单后可关联去重") {
        addManualScanExpense(merchant,amountCent,occurredAt,category,note)
    }

    fun linkManualScanExpense(manualId:Long,importedId:Long) =
        financeChange("已关联正式账单，手动记录不再重复计入消费") {
            linkManualScanExpense(manualId,importedId)
        }

    fun unlinkManualScanExpense(manualId:Long) =
        financeChange("已解除关联，手动消费重新计入统计") {
            unlinkManualScanExpense(manualId)
        }

    fun deleteManualScanExpense(id:Long) =
        financeChange("已删除手动记录，导入账单不受影响") {
            deleteManualScanExpense(id)
        }

    fun previewCategoryRule(merchant:String) {
        if (merchant.isBlank()) {
            _uiState.value=_uiState.value.copy(
                rulePreviewMerchant=null,rulePreviewCount=0
            )
            return
        }
        viewModelScope.launch {
            val outcome=runCatching {
                withContext(Dispatchers.IO) {
                    synchronized(db) { db.ruleAffectedCount(merchant) }
                }
            }
            _uiState.value=_uiState.value.copy(
                rulePreviewMerchant=if(outcome.isSuccess) merchant.trim() else null,
                rulePreviewCount=outcome.getOrDefault(0),
                message=outcome.exceptionOrNull()?.message
            )
        }
    }

    fun saveCategoryRule(merchant:String,category:String,applyExisting:Boolean) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            val result=runCatching {
                withContext(Dispatchers.IO) {
                    synchronized(db) { db.saveCategoryRule(merchant,category,applyExisting) }
                }
            }
            _uiState.value=_uiState.value.copy(
                message=result.fold(
                    onSuccess={ count ->
                        "规则已保存"+if(applyExisting) "，更新 $count 笔未人工确认流水" else
                            "，仅影响以后导入的账单"
                    },
                    onFailure={ it.message?:"规则保存失败" }
                )
            )
            if(result.isSuccess) refresh()
        }
    }

    fun deletePlatformCategoryRule(platform:Platform,merchant:String) =
        financeChange("已删除此来源下的自动分类规则，历史账单保留") {
            deletePlatformCategoryRule(platform,merchant)
        }

    fun deleteCategoryRule(merchant:String) =
        financeChange("已删除自动分类规则；历史流水未更改") {
            deleteCategoryRule(merchant)
        }

    fun deleteMerchantAlias(source:String) =
        financeChange("商户别名已删除；原始流水未更改") {
            deleteMerchantAlias(source)
        }

    fun deleteProductAlias(key:String) =
        financeChange("商品别名已删除；原始流水未更改") {
            deleteProductAlias(key)
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

    fun updateNature(
        transaction:Transaction,flowType:FlowType,category:String,
        applyMerchant:Boolean=false,
    ) {
        viewModelScope.launch {
            val saved=runCatching {
                withContext(Dispatchers.IO) {
                    synchronized(db) {
                        db.updateMerchantNature(transaction,flowType,category,applyMerchant)
                    }
                }
            }
            _uiState.value=_uiState.value.copy(
                message=saved.fold(
                    onSuccess={ updated ->
                        if(applyMerchant && updated>0)
                            "已修改该商家，并自动归类另外 "+updated+" 笔历史流水；以后导入自动沿用"
                        else if(applyMerchant)
                            "已修改；同名同平台的普通流水将自动沿用，已确认或特殊流水保留原分类"
                        else "交易性质已保存"
                    },
                    onFailure={it.message?:"保存失败"}
                )
            )
            if(saved.isSuccess) refresh()
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
        if (flow !in setOf("ALL", "EXPENSE", "INCOME", "OTHER", "RECEIPTS", "OUTFLOW", "CONSUMPTION", "REPAYMENT")) return
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
    val categoryPreviewId: Long? = null,
    val categoryPreviewCount: Int? = null,
    val categoryPreview: CategoryEditPreview? = null,
    val homePeriod: String = "MONTH",
    val homeStart: LocalDate = LocalDate.now().withDayOfMonth(1),
    val homeEnd: LocalDate = LocalDate.now(),
    val homeSummary: DashboardSummary = DashboardSummary(),
    val homeRecent: List<Transaction> = emptyList(),
    val platformFilter: Platform? = null,
    val smallThresholdYuan: Int = 50,
    val summary: DashboardSummary = DashboardSummary(),
    val previousSummary: DashboardSummary = DashboardSummary(),
    val transactions: List<Transaction> = emptyList(),
    val monthlyTransactions: List<Transaction> = emptyList(),
    val periodTransactions: List<Transaction> = emptyList(),
    val periodCategories: List<CategoryTotal> = emptyList(),
    val periodManualRepayments: List<ManualCreditRepayment> = emptyList(),
    val scanMerchantLabels: Map<Long,String> = emptyMap(),
    val manualScanLinks: Map<Long,Long> = emptyMap(),
    val scanHistory: List<Transaction> = emptyList(),
    val creditCenter: CreditCenter = CreditCenter(),
    val creditHistory: List<Transaction> = emptyList(),
    val trendDays: List<TrendPoint> = emptyList(),
    val trendMonths: List<TrendPoint> = emptyList(),
    val trendDetails: List<Transaction> = emptyList(),
    val trendSelectionLabel: String? = null,
    val loanHistory: List<Transaction> = emptyList(),
    val loanProfiles: List<LoanProfile> = emptyList(),
    val categoryRules: List<MerchantRule> = emptyList(),
    val platformCategoryRules: List<PlatformCategoryRule> = emptyList(),
    val rulePreviewMerchant: String? = null,
    val rulePreviewCount: Int = 0,
    val importPreview: ImportPreview? = null,
    val lastImportResult: ImportResult? = null,
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
