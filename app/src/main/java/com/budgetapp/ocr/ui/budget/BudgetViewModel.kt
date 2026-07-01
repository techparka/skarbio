package com.budgetapp.ocr.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.ocr.data.local.entity.BillStatus
import com.budgetapp.ocr.data.local.entity.BudgetEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.repository.BillRepository
import com.budgetapp.ocr.data.repository.BudgetRepository
import com.budgetapp.ocr.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class BudgetStatus(
    val category: String,
    val yearMonth: String,
    val limitAmount: Double,
    val spentAmount: Double
) {
    val percentage: Float
        get() = if (limitAmount > 0) (spentAmount / limitAmount * 100f).toFloat() else 0f
    val isOverBudget: Boolean
        get() = spentAmount > limitAmount
    val remaining: Double
        get() = limitAmount - spentAmount
}

data class BudgetUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val budgetStatuses: List<BudgetStatus> = emptyList(),
    val availableMonths: List<YearMonth> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM")

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    private val _state = MutableStateFlow(BudgetUiState())
    val state: StateFlow<BudgetUiState> = _state.asStateFlow()

    init {
        observeBudgets()
        loadAvailableMonths()
    }

    private fun observeBudgets() {
        viewModelScope.launch {
            _selectedMonth
                .flatMapLatest { month ->
                    val monthStr = month.format(fmt)
                    combine(
                        budgetRepository.getBudgetsForMonth(monthStr),
                        transactionRepository.getTransactionsForMonth(monthStr),
                        billRepository.getBillsForMonth(monthStr)
                    ) { budgets, allTransactions, billsForMonth ->
                        val spendingByCategory = mutableMapOf<String, Double>()

                        // Expense transactions for the selected month
                        allTransactions
                            .filter { tx -> tx.type == TransactionType.EXPENSE }
                            .forEach { tx ->
                                spendingByCategory[tx.category] =
                                    (spendingByCategory[tx.category] ?: 0.0) + tx.amount
                            }

                        // All bills for the selected month — map bill-specific categories
                        // (TELECOM, ENERGY, WATER, GAS, INTERNET, OTHER_BILL) to BILLS umbrella
                        // so they count toward the "Rachunki" budget entry.
                        val billCategoryNames = com.budgetapp.ocr.data.model.CategoryKey
                            .billCategories().map { it.name }.toSet()
                        billsForMonth.forEach { bill ->
                            val budgetCat = if (bill.category in billCategoryNames)
                                com.budgetapp.ocr.data.model.CategoryKey.BILLS.name
                            else
                                bill.category
                            spendingByCategory[budgetCat] =
                                (spendingByCategory[budgetCat] ?: 0.0) + bill.amount
                        }

                        Pair(month, budgets.map { budget ->
                            BudgetStatus(
                                category    = budget.category,
                                yearMonth   = budget.yearMonth,
                                limitAmount = budget.limitAmount,
                                spentAmount = spendingByCategory[budget.category] ?: 0.0
                            )
                        }.sortedByDescending { it.percentage })
                    }
                }
                .collect { (month, statuses) ->
                    _state.update { it.copy(
                        selectedMonth  = month,
                        budgetStatuses = statuses,
                        isLoading      = false
                    ) }
                }
        }
    }

    private fun loadAvailableMonths() {
        viewModelScope.launch {
            val months = budgetRepository.getAvailableMonths()
                .map { YearMonth.parse(it, fmt) }
                .sortedDescending()
            // Always include current and previous month in picker
            val extended = (months + listOf(YearMonth.now(), YearMonth.now().minusMonths(1)))
                .distinct().sortedDescending()
            _state.update { it.copy(availableMonths = extended) }
        }
    }

    fun selectMonth(month: YearMonth) {
        _selectedMonth.value = month
    }

    fun upsertBudget(category: String, limitAmount: Double) {
        viewModelScope.launch {
            val monthStr = _selectedMonth.value.format(fmt)
            // Fetch the existing row to reuse its primary key; @Upsert only resolves PK conflicts,
            // not UNIQUE index conflicts, so inserting with id=0 would silently fail on update.
            val existing = budgetRepository.getBudgetForCategoryMonth(category, monthStr)
            val entity = existing?.copy(limitAmount = limitAmount)
                ?: BudgetEntity(category = category, yearMonth = monthStr, limitAmount = limitAmount)
            budgetRepository.upsert(entity)
            loadAvailableMonths()
        }
    }

    fun deleteBudget(status: BudgetStatus) {
        viewModelScope.launch {
            budgetRepository.deleteBudgetForMonth(status.category, status.yearMonth)
            loadAvailableMonths()
        }
    }

    /** Copy all budgets from the previous month into the current month (if none exist). */
    fun copyFromPreviousMonth() {
        viewModelScope.launch {
            val current = _selectedMonth.value
            val previous = current.minusMonths(1).format(fmt)
            val currentStr = current.format(fmt)
            val existing = budgetRepository.getAvailableMonths()
            if (currentStr in existing) return@launch  // already has budgets
            val prevBudgets = budgetRepository.getBudgetsForMonth(previous).first()
            prevBudgets.forEach { prev ->
                budgetRepository.upsert(
                    BudgetEntity(
                        category = prev.category,
                        yearMonth = currentStr,
                        limitAmount = prev.limitAmount
                    )
                )
            }
            loadAvailableMonths()
        }
    }
}
