package com.budgetapp.ocr.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.ocr.data.local.entity.BillEntity
import com.budgetapp.ocr.data.local.entity.TransactionEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.repository.BillRepository
import com.budgetapp.ocr.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.budgetapp.ocr.ui.transactions.HistoryItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ── Transaction enums ────────────────────────────────────────────────────────

enum class DateRange { ALL, THIS_MONTH, LAST_3_MONTHS, LAST_6_MONTHS, LAST_YEAR, SPECIFIC_MONTH }
enum class SortOrder { NEWEST, OLDEST, HIGHEST, LOWEST }

data class FilterParams(
    val type: TransactionType? = null,
    val searchQuery: String = "",
    val category: String? = null,
    val accountId: Long? = null,
    val dateRange: DateRange = DateRange.ALL,
    val specificMonth: String = "",
    val sortOrder: SortOrder = SortOrder.NEWEST
)

// ── Bill enums ───────────────────────────────────────────────────────────────

enum class BillSortOrder { DUE_SOON, DUE_LATE, AMOUNT_HIGH, AMOUNT_LOW }

data class BillFilterParams(
    val searchQuery: String = "",
    val category: String? = null,
    val dateRange: DateRange = DateRange.ALL,
    val specificMonth: String = "",
    val sortOrder: BillSortOrder = BillSortOrder.DUE_SOON
)

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ExpenseListState())
    val state: StateFlow<ExpenseListState> = _state.asStateFlow()

    private val _filterParams = MutableStateFlow(FilterParams())
    val filterParams: StateFlow<FilterParams> = _filterParams.asStateFlow()

    private val _billFilterParams = MutableStateFlow(BillFilterParams())
    val billFilterParams: StateFlow<BillFilterParams> = _billFilterParams.asStateFlow()

    val unifiedHistory: StateFlow<List<HistoryItem>> = combine(
        _state.map { it.transactions }.distinctUntilChanged(),
        _state.map { it.bills }.distinctUntilChanged(),
        _filterParams.map { it.sortOrder }.distinctUntilChanged()
    ) { transactions, bills, sortOrder ->
        val txItems = transactions.map { HistoryItem.TransactionItem(it) }
        val billItems = bills.map { HistoryItem.BillItem(it) }
        val combined = txItems + billItems
        when (sortOrder) {
            SortOrder.NEWEST  -> combined.sortedByDescending { it.sortDate }
            SortOrder.OLDEST  -> combined.sortedBy { it.sortDate }
            SortOrder.HIGHEST -> combined.sortedByDescending {
                when (it) {
                    is HistoryItem.TransactionItem -> it.transaction.amount
                    is HistoryItem.BillItem -> it.bill.amount
                }
            }
            SortOrder.LOWEST  -> combined.sortedBy {
                when (it) {
                    is HistoryItem.TransactionItem -> it.transaction.amount
                    is HistoryItem.BillItem -> it.bill.amount
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadTransactions()
        loadBills()
    }

    // ── Transactions ─────────────────────────────────────────────────────────

    private fun loadTransactions() {
        viewModelScope.launch {
            combine(
                transactionRepository.getAllTransactions(),
                _filterParams
            ) { transactions, params ->
                applyTransactionFilters(transactions, params)
            }.collect { filtered ->
                _state.update { it.copy(transactions = filtered, isLoading = false) }
            }
        }
    }

    private fun applyTransactionFilters(
        transactions: List<TransactionEntity>,
        params: FilterParams
    ): List<TransactionEntity> {
        var result = transactions

        if (params.type != null) result = result.filter { it.type == params.type }

        if (!params.category.isNullOrBlank())
            result = result.filter { it.category == params.category }

        if (params.accountId != null)
            result = result.filter { it.accountId == params.accountId }

        result = applyDateFilter(result, params.dateRange, params.specificMonth) { it.date }

        if (params.searchQuery.isNotBlank()) {
            val q = params.searchQuery
            result = result.filter {
                it.merchantName?.contains(q, ignoreCase = true) == true ||
                        it.category.contains(q, ignoreCase = true) ||
                        it.notes.contains(q, ignoreCase = true)
            }
        }

        return when (params.sortOrder) {
            SortOrder.NEWEST  -> result.sortedByDescending { it.date }
            SortOrder.OLDEST  -> result.sortedBy { it.date }
            SortOrder.HIGHEST -> result.sortedByDescending { it.amount }
            SortOrder.LOWEST  -> result.sortedBy { it.amount }
        }
    }

    fun setFilterType(type: TransactionType?) = _filterParams.update { it.copy(type = type) }
    fun setSearchQuery(query: String) = _filterParams.update { it.copy(searchQuery = query) }
    fun setFilterCategory(category: String?) = _filterParams.update { it.copy(category = category) }
    fun setFilterAccount(accountId: Long?) = _filterParams.update { it.copy(accountId = accountId) }
    fun setDateRange(range: DateRange) = _filterParams.update { it.copy(dateRange = range, specificMonth = "") }
    fun setSpecificMonth(yearMonth: String) = _filterParams.update { it.copy(dateRange = DateRange.SPECIFIC_MONTH, specificMonth = yearMonth) }
    fun setSortOrder(sort: SortOrder) = _filterParams.update { it.copy(sortOrder = sort) }
    fun clearFilters() = _filterParams.update { FilterParams(type = it.type) }

    // ── Bills ────────────────────────────────────────────────────────────────

    private fun loadBills() {
        viewModelScope.launch {
            combine(
                billRepository.getAllBills(),
                _billFilterParams
            ) { bills, params ->
                applyBillFilters(bills, params)
            }.collect { filtered ->
                _state.update { it.copy(bills = filtered) }
            }
        }
    }

    private fun applyBillFilters(
        bills: List<BillEntity>,
        params: BillFilterParams
    ): List<BillEntity> {
        var result = bills

        if (!params.category.isNullOrBlank())
            result = result.filter { it.category == params.category }

        result = applyDateFilter(result, params.dateRange, params.specificMonth) { it.dueDate }

        if (params.searchQuery.isNotBlank()) {
            val q = params.searchQuery
            result = result.filter {
                it.vendorName.contains(q, ignoreCase = true) ||
                        it.category.contains(q, ignoreCase = true) ||
                        it.notes.contains(q, ignoreCase = true)
            }
        }

        return when (params.sortOrder) {
            BillSortOrder.DUE_SOON    -> result.sortedBy { it.dueDate }
            BillSortOrder.DUE_LATE    -> result.sortedByDescending { it.dueDate }
            BillSortOrder.AMOUNT_HIGH -> result.sortedByDescending { it.amount }
            BillSortOrder.AMOUNT_LOW  -> result.sortedBy { it.amount }
        }
    }

    fun setBillSearchQuery(query: String) = _billFilterParams.update { it.copy(searchQuery = query) }
    fun setBillFilterCategory(category: String?) = _billFilterParams.update { it.copy(category = category) }
    fun setBillDateRange(range: DateRange) = _billFilterParams.update { it.copy(dateRange = range, specificMonth = "") }
    fun setBillSpecificMonth(yearMonth: String) = _billFilterParams.update { it.copy(dateRange = DateRange.SPECIFIC_MONTH, specificMonth = yearMonth) }
    fun setBillSortOrder(sort: BillSortOrder) = _billFilterParams.update { it.copy(sortOrder = sort) }
    fun clearBillFilters() = _billFilterParams.update { BillFilterParams(searchQuery = it.searchQuery) }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private fun <T> applyDateFilter(
        items: List<T>,
        range: DateRange,
        specificMonth: String,
        dateOf: (T) -> String
    ): List<T> = when (range) {
        DateRange.ALL -> items
        DateRange.THIS_MONTH -> {
            val prefix = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
            items.filter { dateOf(it).startsWith(prefix) }
        }
        DateRange.LAST_3_MONTHS -> {
            val cutoff = LocalDate.now().minusMonths(3).toString()
            items.filter { dateOf(it) >= cutoff }
        }
        DateRange.LAST_6_MONTHS -> {
            val cutoff = LocalDate.now().minusMonths(6).toString()
            items.filter { dateOf(it) >= cutoff }
        }
        DateRange.LAST_YEAR -> {
            val cutoff = LocalDate.now().minusMonths(12).toString()
            items.filter { dateOf(it) >= cutoff }
        }
        DateRange.SPECIFIC_MONTH -> {
            if (specificMonth.isNotBlank())
                items.filter { dateOf(it).startsWith(specificMonth) }
            else items
        }
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch { transactionRepository.delete(transaction) }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch { transactionRepository.update(transaction) }
    }

    fun refresh() {
        loadTransactions()
        loadBills()
    }
}

data class ExpenseListState(
    val transactions: List<TransactionEntity> = emptyList(),
    val bills: List<BillEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
