package com.budgetapp.ocr.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.ocr.data.local.entity.BillEntity
import com.budgetapp.ocr.data.local.entity.BillStatus
import com.budgetapp.ocr.data.local.entity.BudgetEntity
import com.budgetapp.ocr.data.local.entity.TransactionEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.local.entity.AccountEntity
import com.budgetapp.ocr.data.repository.AccountRepository
import com.budgetapp.ocr.data.repository.BillRepository
import com.budgetapp.ocr.data.repository.BudgetRepository
import com.budgetapp.ocr.data.repository.CategoryRepository
import com.budgetapp.ocr.data.repository.SettingsRepository
import com.budgetapp.ocr.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * ViewModel for the Dashboard screen
 * Per REALISTIC_PLAN.md - without Use Cases
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val billRepository: BillRepository,
    private val budgetRepository: BudgetRepository,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _unpaidBills = MutableStateFlow<List<BillEntity>>(emptyList())
    val unpaidBills: StateFlow<List<BillEntity>> = _unpaidBills.asStateFlow()

    private val _accountBalances = MutableStateFlow<List<AccountBalanceItem>>(emptyList())
    val accountBalances: StateFlow<List<AccountBalanceItem>> = _accountBalances.asStateFlow()

    private val _heroMetric = MutableStateFlow(HeroMetric.MONTHLY_RESULT)
    private val _heroAccountId = MutableStateFlow(-1L)
    private val _heroState = MutableStateFlow(HeroState())
    val heroState: StateFlow<HeroState> = _heroState.asStateFlow()

    init {
        viewModelScope.launch {
            _selectedMonth
                .flatMapLatest { yearMonth ->
                    val ym = yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    combine(
                        transactionRepository.getTransactionsForMonth(ym),
                        billRepository.getBillsForMonth(ym),
                        budgetRepository.getBudgetsForMonth(ym)
                    ) { transactions, bills, budgets ->
                        withContext(Dispatchers.Default) {
                            buildDashboardState(transactions, bills, budgets)
                        }
                    }
                }
                .collect { newState -> _state.value = newState }
        }
        loadUnpaidBills()
        loadAccountBalances()
        loadHeroPrefs()
        observeHeroMetric()
    }

    private fun loadHeroPrefs() {
        viewModelScope.launch {
            val savedMetric = settingsRepository.getSettingByKey(SettingsRepository.Keys.HERO_METRIC)
            if (savedMetric == null) {
                // First launch — default to default account balance
                val defaultAccountId = accountRepository.getDefaultAccountId()
                _heroMetric.value = HeroMetric.ACCOUNT_BALANCE
                _heroAccountId.value = defaultAccountId
            } else {
                val accountId = settingsRepository.getValueByKey(
                    SettingsRepository.Keys.HERO_ACCOUNT_ID, "-1"
                ).toLongOrNull() ?: -1L
                _heroMetric.value = HeroMetric.entries.find { it.name == savedMetric.value }
                    ?: HeroMetric.ACCOUNT_BALANCE
                _heroAccountId.value = accountId
            }
        }
    }

    private fun observeHeroMetric() {
        viewModelScope.launch {
            combine(_state, _accountBalances, _heroMetric, _heroAccountId) { state, balances, metric, accountId ->
                computeHeroState(state, balances, metric, accountId)
            }.collect { _heroState.value = it }
        }
    }

    private fun computeHeroState(
        state: DashboardState,
        balances: List<AccountBalanceItem>,
        metric: HeroMetric,
        accountId: Long
    ): HeroState {
        return when (metric) {
            HeroMetric.MONTHLY_RESULT -> HeroState(
                metric = metric,
                amount = state.balance,
                accountId = -1L,
                accountName = ""
            )
            HeroMetric.TOTAL_BALANCE -> HeroState(
                metric = metric,
                amount = balances.sumOf { it.balance },
                accountId = -1L,
                accountName = ""
            )
            HeroMetric.ACCOUNT_BALANCE -> {
                val account = balances.find { it.accountId == accountId } ?: balances.firstOrNull()
                HeroState(
                    metric = metric,
                    amount = account?.balance ?: 0.0,
                    accountId = account?.accountId ?: -1L,
                    accountName = account?.name ?: ""
                )
            }
        }
    }

    fun setHeroMetric(metric: HeroMetric, accountId: Long = -1L) {
        _heroMetric.value = metric
        _heroAccountId.value = accountId
        viewModelScope.launch {
            settingsRepository.setSetting(SettingsRepository.Keys.HERO_METRIC, metric.name)
            settingsRepository.setSetting(SettingsRepository.Keys.HERO_ACCOUNT_ID, accountId.toString())
        }
    }

    private fun buildDashboardState(
        transactions: List<TransactionEntity>,
        bills: List<BillEntity>,
        budgets: List<BudgetEntity>
    ): DashboardState {
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
        val income   = transactions.filter { it.type == TransactionType.INCOME }

        val transactionExpenses = expenses.sumOf { it.amount }
        val paidBills           = bills.filter { it.status == BillStatus.PAID }
        val billExpenses        = paidBills.sumOf { it.amount }

        val totalExpenses = transactionExpenses + billExpenses
        val totalIncome   = income.sumOf { it.amount }
        val balance       = totalIncome - totalExpenses

        val expenseCategoryMap = mutableMapOf<String, Double>()
        val expenseCountMap    = mutableMapOf<String, Int>()

        expenses.forEach { tx ->
            expenseCategoryMap[tx.category] = (expenseCategoryMap[tx.category] ?: 0.0) + tx.amount
            expenseCountMap[tx.category]    = (expenseCountMap[tx.category] ?: 0) + 1
        }
        paidBills.forEach { bill ->
            expenseCategoryMap[bill.category] = (expenseCategoryMap[bill.category] ?: 0.0) + bill.amount
            expenseCountMap[bill.category]    = (expenseCountMap[bill.category] ?: 0) + 1
        }

        val categoryBreakdown = expenseCategoryMap.map { (category, amount) ->
            CategorySummary(
                category         = category,
                amount           = amount,
                percentage       = if (totalExpenses > 0) (amount / totalExpenses * 100) else 0.0,
                transactionCount = expenseCountMap[category] ?: 0
            )
        }.sortedByDescending { it.amount }

        // Build a budget-aware spending map: bill sub-categories consolidated under BILLS umbrella
        val billCategoryNames = com.budgetapp.ocr.data.model.CategoryKey
            .billCategories().map { it.name }.toSet()
        val budgetSpendingMap = mutableMapOf<String, Double>()
        expenses.forEach { tx ->
            budgetSpendingMap[tx.category] = (budgetSpendingMap[tx.category] ?: 0.0) + tx.amount
        }
        // Use ALL bills (not just paid) — consistent with BudgetViewModel spending calculation
        bills.forEach { bill ->
            val budgetCat = if (bill.category in billCategoryNames)
                com.budgetapp.ocr.data.model.CategoryKey.BILLS.name else bill.category
            budgetSpendingMap[budgetCat] = (budgetSpendingMap[budgetCat] ?: 0.0) + bill.amount
        }

        val budgetAlerts = budgets.map { budget ->
            BudgetAlertItem(
                category    = budget.category,
                spentAmount = budgetSpendingMap[budget.category] ?: 0.0,
                limitAmount = budget.limitAmount
            )
        }.filter { it.percentage >= 80f }
         .sortedByDescending { it.percentage }

        return DashboardState(
            transactions     = transactions,
            totalExpenses    = totalExpenses,
            totalIncome      = totalIncome,
            balance          = balance,
            categoryBreakdown = categoryBreakdown,
            budgetAlerts     = budgetAlerts,
            isLoading        = false,
            error            = null
        )
    }

    fun loadMonthData(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
    }

    private fun loadUnpaidBills() {
        viewModelScope.launch {
            // Instead of the first 3 bills, show all from the current month + overdue
            billRepository.getCurrentMonthAndOverdueBills().collect { bills ->
                _unpaidBills.value = bills
            }
        }
    }

    private fun loadAccountBalances() {
        viewModelScope.launch {
            accountRepository.getAllAccounts().collect { accounts ->
                refreshBalancesForAccounts(accounts)
            }
        }
    }

    fun refreshAccountBalances() {
        viewModelScope.launch {
            val accounts = accountRepository.getAllAccountsList()
            refreshBalancesForAccounts(accounts)
        }
    }

    private suspend fun refreshBalancesForAccounts(accounts: List<AccountEntity>) {
        val balances = accounts.map { account ->
            AccountBalanceItem(
                accountId = account.id,
                name = account.name,
                icon = account.icon,
                color = account.color,
                balance = accountRepository.getAccountBalance(account.id)
            )
        }
        _accountBalances.value = balances
    }

    fun selectMonth(yearMonth: YearMonth) {
        loadMonthData(yearMonth)
    }

    fun previousMonth() {
        val newMonth = _selectedMonth.value.minusMonths(1)
        selectMonth(newMonth)
    }

    fun nextMonth() {
        val newMonth = _selectedMonth.value.plusMonths(1)
        selectMonth(newMonth)
    }

    fun refresh() {
        loadMonthData(_selectedMonth.value)
        refreshAccountBalances()
    }
}

data class AccountBalanceItem(
    val accountId: Long,
    val name: String,
    val icon: String,
    val color: String,
    val balance: Double
)

/**
 * UI State for Dashboard
 */
data class DashboardState(
    val transactions: List<TransactionEntity> = emptyList(),
    val totalExpenses: Double = 0.0,
    val totalIncome: Double = 0.0,
    val balance: Double = 0.0,
    val categoryBreakdown: List<CategorySummary> = emptyList(),
    val budgetAlerts: List<BudgetAlertItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * A budget category that has reached ≥ 80% of its monthly limit.
 */
data class BudgetAlertItem(
    val category: String,
    val spentAmount: Double,
    val limitAmount: Double
) {
    val percentage: Float
        get() = if (limitAmount > 0) (spentAmount / limitAmount * 100f).toFloat() else 0f
    val isOverBudget: Boolean
        get() = spentAmount > limitAmount
    val remaining: Double
        get() = limitAmount - spentAmount
}

/**
 * Expense summary per category
 */
data class CategorySummary(
    val category: String,
    val amount: Double,
    val percentage: Double,
    val transactionCount: Int
)

enum class HeroMetric {
    MONTHLY_RESULT,
    TOTAL_BALANCE,
    ACCOUNT_BALANCE
}

data class HeroState(
    val metric: HeroMetric = HeroMetric.MONTHLY_RESULT,
    val amount: Double = 0.0,
    val accountId: Long = -1L,
    val accountName: String = ""
)
