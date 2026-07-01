package com.budgetapp.ocr.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.ocr.data.local.entity.BillStatus
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.repository.BillRepository
import com.budgetapp.ocr.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    init {
        loadReports()
    }

    fun loadReports() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val now = YearMonth.now()
            val months = (5 downTo 0).map { now.minusMonths(it.toLong()) }

            // 1. Monthly spending trend (last 6 months) - includes paid bills
            val monthlyTrend = mutableListOf<MonthlySpending>()
            for (month in months) {
                val yearMonth = month.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                val transactionExpenses = transactionRepository.getTotalForMonth(TransactionType.EXPENSE, yearMonth)
                val billExpenses = billRepository.getPaidBillsTotalForMonth(yearMonth)
                val expenses = transactionExpenses + billExpenses
                val income = transactionRepository.getTotalForMonth(TransactionType.INCOME, yearMonth)
                val label = month.month.getDisplayName(TextStyle.SHORT, Locale("pl"))
                    .replaceFirstChar { it.uppercase() }
                monthlyTrend.add(MonthlySpending(label, month, expenses, income))
            }

            // 2. Category comparison (this month vs last month)
            val thisMonth = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val lastMonth = now.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"))

            transactionRepository.getTransactionsForMonth(thisMonth)
                .combine(transactionRepository.getTransactionsForMonth(lastMonth)) { current, previous ->
                    Pair(current, previous)
                }
                .combine(billRepository.getBillsForMonth(thisMonth)) { (current, previous), currentBills ->
                    Triple(current, previous, currentBills)
                }
                .combine(billRepository.getBillsForMonth(lastMonth)) { (current, previous, currentBills), previousBills ->
                    // Merge transaction expenses + paid bill expenses by category
                    val currentByCategory = mutableMapOf<String, Double>()
                    current.filter { it.type == TransactionType.EXPENSE }
                        .forEach { tx -> currentByCategory[tx.category] = (currentByCategory[tx.category] ?: 0.0) + tx.amount }
                    currentBills.filter { it.status == BillStatus.PAID }
                        .forEach { bill -> currentByCategory[bill.category] = (currentByCategory[bill.category] ?: 0.0) + bill.amount }

                    val previousByCategory = mutableMapOf<String, Double>()
                    previous.filter { it.type == TransactionType.EXPENSE }
                        .forEach { tx -> previousByCategory[tx.category] = (previousByCategory[tx.category] ?: 0.0) + tx.amount }
                    previousBills.filter { it.status == BillStatus.PAID }
                        .forEach { bill -> previousByCategory[bill.category] = (previousByCategory[bill.category] ?: 0.0) + bill.amount }

                    val allCategories = (currentByCategory.keys + previousByCategory.keys).distinct()
                    allCategories.map { category ->
                        CategoryComparison(
                            category = category,
                            currentMonth = currentByCategory[category] ?: 0.0,
                            previousMonth = previousByCategory[category] ?: 0.0
                        )
                    }.sortedByDescending { it.currentMonth }
                }
                .collect { comparison ->
                    // 3. Top stores - collect from ALL transactions
                    transactionRepository.getAllTransactions()
                        .first()
                        .filter { it.type == TransactionType.EXPENSE && !it.merchantName.isNullOrBlank() }
                        .groupBy { it.merchantName!! }
                        .map { (store, txs) ->
                            StoreSummary(
                                storeName = store,
                                totalAmount = txs.sumOf { it.amount },
                                transactionCount = txs.size
                            )
                        }
                        .sortedByDescending { it.totalAmount }
                        .take(10)
                        .let { topStores ->
                            _state.update {
                                it.copy(
                                    monthlyTrend = monthlyTrend,
                                    categoryComparison = comparison,
                                    topStores = topStores,
                                    isLoading = false
                                )
                            }
                        }
                }
        }
    }
}

data class ReportsState(
    val monthlyTrend: List<MonthlySpending> = emptyList(),
    val categoryComparison: List<CategoryComparison> = emptyList(),
    val topStores: List<StoreSummary> = emptyList(),
    val isLoading: Boolean = true
)

data class MonthlySpending(
    val label: String,
    val month: YearMonth,
    val expenses: Double,
    val income: Double
)

data class CategoryComparison(
    val category: String,
    val currentMonth: Double,
    val previousMonth: Double
) {
    val change: Double get() = if (previousMonth > 0) ((currentMonth - previousMonth) / previousMonth * 100) else 0.0
}

data class StoreSummary(
    val storeName: String,
    val totalAmount: Double,
    val transactionCount: Int
)
