package com.budgetapp.ocr.ui.reports

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.local.entity.toMonths
import com.budgetapp.ocr.data.repository.AccountRepository
import com.budgetapp.ocr.data.repository.BillRepository
import com.budgetapp.ocr.data.repository.CategoryRepository
import com.budgetapp.ocr.data.repository.SettingsRepository
import com.budgetapp.ocr.databinding.FragmentAccountsTabBinding
import com.budgetapp.ocr.databinding.FragmentBudgetTabBinding
import com.budgetapp.ocr.databinding.FragmentCashflowTabBinding
import com.budgetapp.ocr.databinding.FragmentReportsBinding
import com.budgetapp.ocr.ui.bills.EditBillDialog
import com.budgetapp.ocr.ui.budget.AddEditBudgetDialog
import com.budgetapp.ocr.ui.budget.BudgetAdapter
import com.budgetapp.ocr.ui.budget.BudgetViewModel
import com.budgetapp.ocr.ui.cashflow.CashflowAdapter
import com.budgetapp.ocr.ui.cashflow.CashflowViewModel
import com.budgetapp.ocr.ui.finance.accounts.AccountsAdapter
import com.budgetapp.ocr.ui.finance.accounts.AccountsViewModel
import com.budgetapp.ocr.ui.finance.accounts.AccountWithBalance
import com.budgetapp.ocr.ui.finance.accounts.AddEditAccountDialog
import com.budgetapp.ocr.ui.finance.accounts.TransferDialog
import com.budgetapp.ocr.utils.CurrencyFormatter
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.budgetapp.ocr.ui.shared.SharedFilterViewModel
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private var _budgetBinding: FragmentBudgetTabBinding? = null
    private val budgetBinding get() = _budgetBinding!!

    private var _accountsBinding: FragmentAccountsTabBinding? = null
    private val accountsBinding get() = _accountsBinding!!

    private var _cashflowBinding: FragmentCashflowTabBinding? = null
    private val cashflowBinding get() = _cashflowBinding!!

    private val reportsViewModel: ReportsViewModel by viewModels()
    private val budgetViewModel: BudgetViewModel by viewModels()
    private val accountsViewModel: AccountsViewModel by viewModels()
    private val cashflowViewModel: CashflowViewModel by viewModels()
    private val sharedFilterViewModel: SharedFilterViewModel by activityViewModels()

    private lateinit var comparisonAdapter: CategoryComparisonAdapter
    private lateinit var topStoresAdapter: TopStoresAdapter
    private lateinit var budgetAdapter: BudgetAdapter
    private lateinit var accountsAdapter: AccountsAdapter
    private lateinit var cashflowAdapter: CashflowAdapter

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var billRepository: BillRepository
    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var categoryRepository: CategoryRepository

    private var currentTab = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        _accountsBinding = FragmentAccountsTabBinding.inflate(inflater, binding.accountsContent, true)
        _budgetBinding = FragmentBudgetTabBinding.inflate(inflater, binding.budgetContent, true)
        _cashflowBinding = FragmentCashflowTabBinding.inflate(inflater, binding.calendarContent, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupBarChart()
        setupRecyclerViews()
        setupAccountsTab()
        setupBudgetTab()
        setupCashflowTab()
        observeReportsState()
        observeAccountsState()
        observeBudgetState()
        observeBudgetsNavigation()
        observeAccountEvents()
        observeCashflowState()
    }

    // ── Tabs ─────────────────────────────────────────────────────────────

    private fun setupTabs() {
        binding.tabLayoutAnalytics.apply {
            addTab(newTab().setText(getString(R.string.tab_accounts)))
            addTab(newTab().setText(getString(R.string.tab_budgets)))
            addTab(newTab().setText(getString(R.string.tab_reports)))
            addTab(newTab().setText(getString(R.string.tab_calendar)))

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    currentTab = tab.position
                    switchTab(currentTab)
                }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }
        switchTab(0)
    }

    private fun switchTab(tab: Int) {
        binding.accountsContent.isVisible  = (tab == 0)
        binding.budgetContent.isVisible    = (tab == 1)
        binding.reportsContent.isVisible   = (tab == 2)
        binding.calendarContent.isVisible  = (tab == 3)
    }

    // ── Accounts tab ─────────────────────────────────────────────────────

    private fun setupAccountsTab() {
        accountsAdapter = AccountsAdapter(
            onClick = { item -> showEditAccountDialog(item) },
            onLongClick = { item -> showAccountOptionsMenu(item) }
        )

        accountsBinding.rvAccounts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = accountsAdapter
        }

        accountsBinding.fabAddAccount.setOnClickListener {
            showAddAccountDialog()
        }

        accountsBinding.btnTransfer.setOnClickListener {
            showTransferDialog()
        }
    }

    private fun showAddAccountDialog() {
        AddEditAccountDialog().apply {
            onSave = { name, type, icon, color, isDefault ->
                accountsViewModel.saveAccount(null, name, type, icon, color, isDefault)
            }
        }.show(childFragmentManager, "add_account")
    }

    private fun showEditAccountDialog(item: AccountWithBalance) {
        AddEditAccountDialog().apply {
            setExisting(item.account)
            onSave = { name, type, icon, color, isDefault ->
                accountsViewModel.saveAccount(item.account.id, name, type, icon, color, isDefault)
            }
        }.show(childFragmentManager, "edit_account")
    }

    private fun showAccountOptionsMenu(item: AccountWithBalance) {
        val options = mutableListOf(
            getString(R.string.edit_account),
            getString(R.string.adjust_balance),
            getString(R.string.account_set_default)
        )
        if (!item.account.isDefault) {
            options.add(getString(R.string.delete_account))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(item.account.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showEditAccountDialog(item)
                    1 -> showAdjustBalanceDialog(item)
                    2 -> accountsViewModel.saveAccount(
                        item.account.id,
                        item.account.name,
                        item.account.type,
                        item.account.icon,
                        item.account.color,
                        true
                    )
                    3 -> confirmDeleteAccount(item)
                }
            }
            .show()
    }

    private fun confirmDeleteAccount(item: AccountWithBalance) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_account_title)
            .setMessage(getString(R.string.delete_account_message, item.account.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                accountsViewModel.deleteAccount(item.account)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAdjustBalanceDialog(item: AccountWithBalance) {
        viewLifecycleOwner.lifecycleScope.launch {
            val currency = settingsRepository.getValueByKey(
                SettingsRepository.Keys.DEFAULT_CURRENCY, "PLN"
            )
            val formattedBalance = CurrencyFormatter.format(item.balance, currency)

            val container = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val pad = (24 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad / 2, pad, 0)
            }

            val tvMessage = android.widget.TextView(requireContext()).apply {
                text = getString(R.string.adjust_balance_message)
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 14f
            }
            container.addView(tvMessage)

            val tvCurrent = android.widget.TextView(requireContext()).apply {
                text = getString(R.string.current_balance_label, formattedBalance)
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                val topMargin = (12 * resources.displayMetrics.density).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, topMargin, 0, topMargin) }
            }
            container.addView(tvCurrent)

            val etTarget = android.widget.EditText(requireContext()).apply {
                hint = getString(R.string.target_balance_hint)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                        android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                setText(String.format("%.2f", item.balance))
                selectAll()
            }
            container.addView(etTarget)

            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.adjust_balance))
                .setView(container)
                .setPositiveButton(R.string.save) { _, _ ->
                    val target = etTarget.text.toString().replace(",", ".").toDoubleOrNull()
                    if (target != null) {
                        accountsViewModel.adjustBalance(item.account.id, target)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showTransferDialog() {
        val accounts = accountsViewModel.state.value.accounts.map { it.account }
        if (accounts.size < 2) {
            Toast.makeText(requireContext(), R.string.transfer_error_same_account, Toast.LENGTH_SHORT).show()
            return
        }

        TransferDialog().apply {
            setAccounts(accounts)
            onTransfer = { fromId, toId, amount, notes ->
                accountsViewModel.transfer(fromId, toId, amount, notes)
            }
        }.show(childFragmentManager, "transfer")
    }

    private fun observeAccountsState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                accountsViewModel.state.collect { state ->
                    accountsAdapter.submitList(state.accounts)
                    accountsBinding.rvAccounts.isVisible = state.accounts.isNotEmpty()
                    accountsBinding.emptyAccountsState.isVisible = state.accounts.isEmpty() && !state.isLoading
                    accountsBinding.btnTransfer.isVisible = state.accounts.size >= 2
                }
            }
        }
    }

    private fun observeAccountEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                accountsViewModel.event.collect { event ->
                    when (event) {
                        is AccountsViewModel.AccountEvent.AccountSaved ->
                            Toast.makeText(requireContext(), R.string.account_saved, Toast.LENGTH_SHORT).show()
                        is AccountsViewModel.AccountEvent.AccountDeleted ->
                            Toast.makeText(requireContext(), R.string.account_deleted, Toast.LENGTH_SHORT).show()
                        is AccountsViewModel.AccountEvent.TransferCompleted ->
                            Toast.makeText(requireContext(), R.string.transfer_success, Toast.LENGTH_SHORT).show()
                        is AccountsViewModel.AccountEvent.BalanceAdjusted ->
                            Toast.makeText(requireContext(), R.string.balance_adjusted, Toast.LENGTH_SHORT).show()
                        is AccountsViewModel.AccountEvent.BalanceNoChange ->
                            Toast.makeText(requireContext(), R.string.balance_no_change, Toast.LENGTH_SHORT).show()
                        is AccountsViewModel.AccountEvent.Error -> {
                            val msgRes = when (event.type) {
                                "cannot_delete_default" -> R.string.cannot_delete_default_account
                                "cannot_delete_last" -> R.string.cannot_delete_last_account
                                "same_account" -> R.string.transfer_error_same_account
                                "invalid_amount" -> R.string.transfer_error_amount
                                else -> R.string.error
                            }
                            Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // ── Reports tab ───────────────────────────────────────────────────────

    private fun setupBarChart() {
        binding.barChartTrend.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setTouchEnabled(false)
            legend.isEnabled = true
            legend.textColor = ContextCompat.getColor(requireContext(), R.color.text_primary)

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) =
                        if (value >= 1000) "%.0fk".format(value / 1000) else "%.0f".format(value)
                }
            }
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
            }

            setFitBars(true)
            animateY(600)
        }
    }

    private fun setupRecyclerViews() {
        comparisonAdapter = CategoryComparisonAdapter()
        binding.rvComparison.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = comparisonAdapter
        }

        topStoresAdapter = TopStoresAdapter()
        binding.rvTopStores.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = topStoresAdapter
        }
    }

    private fun observeReportsState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                reportsViewModel.state.collect { state ->
                    if (state.isLoading) return@collect

                    val hasData = state.monthlyTrend.any { it.expenses > 0 || it.income > 0 }

                    binding.cardTrend.isVisible = hasData
                    binding.cardComparison.isVisible = hasData && state.categoryComparison.isNotEmpty()
                    binding.cardStores.isVisible = hasData && state.topStores.isNotEmpty()
                    binding.emptyState.isVisible = !hasData

                    if (hasData) {
                        updateBarChart(state.monthlyTrend)
                        comparisonAdapter.submitList(state.categoryComparison)
                        topStoresAdapter.submitList(state.topStores)
                    }
                }
            }
        }
    }

    private fun updateBarChart(data: List<MonthlySpending>) {
        val expenseEntries = data.mapIndexed { i, item -> BarEntry(i.toFloat(), item.expenses.toFloat()) }
        val incomeEntries  = data.mapIndexed { i, item -> BarEntry(i.toFloat(), item.income.toFloat()) }

        val fmt = object : ValueFormatter() {
            override fun getFormattedValue(value: Float) =
                if (value > 0) "%.0f".format(value) else ""
        }

        val expenseDataSet = BarDataSet(expenseEntries, getString(R.string.expenses_label)).apply {
            color = ContextCompat.getColor(requireContext(), R.color.expense_red)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            valueTextSize = 9f
            valueFormatter = fmt
        }
        val incomeDataSet = BarDataSet(incomeEntries, getString(R.string.income_label)).apply {
            color = ContextCompat.getColor(requireContext(), R.color.income_green)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            valueTextSize = 9f
            valueFormatter = fmt
        }

        val barData = BarData(expenseDataSet, incomeDataSet).apply { barWidth = 0.35f }
        binding.barChartTrend.xAxis.valueFormatter = IndexAxisValueFormatter(data.map { it.label })
        binding.barChartTrend.apply {
            this.data = barData
            groupBars(-0.5f, 0.2f, 0.05f)
            xAxis.axisMinimum = -0.5f
            xAxis.axisMaximum = data.size - 0.5f
            invalidate()
        }
    }

    // ── Budget tab ────────────────────────────────────────────────────────

    private fun setupBudgetTab() {
        budgetAdapter = BudgetAdapter { status ->
            viewLifecycleOwner.lifecycleScope.launch {
                val expenseCats = categoryRepository.getCategoriesByType(TransactionType.EXPENSE)
                    .first()
                AddEditBudgetDialog.newInstance(status.category, status.limitAmount).apply {
                    allExpenseCategories = expenseCats
                    onSave = { cat, limit -> budgetViewModel.upsertBudget(cat, limit) }
                    onDelete = { budgetViewModel.deleteBudget(status) }
                }.show(childFragmentManager, "edit_budget")
            }
        }

        budgetBinding.rvBudgets.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = budgetAdapter
        }

        budgetBinding.fabAddBudget.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val expenseCats = categoryRepository.getCategoriesByType(TransactionType.EXPENSE)
                    .first()
                AddEditBudgetDialog.newInstance().apply {
                    allExpenseCategories = expenseCats
                    onSave = { cat, limit -> budgetViewModel.upsertBudget(cat, limit) }
                }.show(childFragmentManager, "add_budget")
            }
        }
    }

    private fun observeBudgetsNavigation() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedFilterViewModel.navigateToBudgets.collect { navigate ->
                    if (navigate) {
                        sharedFilterViewModel.consumeBudgetsNavigation()
                        // Budgets is now tab index 1
                        binding.tabLayoutAnalytics.selectTab(binding.tabLayoutAnalytics.getTabAt(1))
                    }
                }
            }
        }
    }

    private fun observeBudgetState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                budgetViewModel.state.collect { state ->
                    budgetAdapter.submitList(state.budgetStatuses)
                    budgetBinding.emptyBudgetState.isVisible = state.budgetStatuses.isEmpty() && !state.isLoading
                    budgetBinding.rvBudgets.isVisible = state.budgetStatuses.isNotEmpty()
                }
            }
        }
    }

    // ── Cashflow tab ──────────────────────────────────────────────────────

    private fun setupCashflowTab() {
        cashflowAdapter = CashflowAdapter { bill -> showEditBillDialog(bill) }

        cashflowBinding.rvCashflowBills.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = cashflowAdapter
        }

        cashflowBinding.btnCashflowPrev.setOnClickListener { cashflowViewModel.previousMonth() }
        cashflowBinding.btnCashflowNext.setOnClickListener { cashflowViewModel.nextMonth() }
    }

    private fun observeCashflowState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    cashflowViewModel.selectedMonth.collect { ym ->
                        val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
                        cashflowBinding.tvCashflowMonth.text = ym.format(formatter)
                            .replaceFirstChar { it.uppercaseChar() }
                    }
                }
                launch {
                    cashflowViewModel.state.collect { state ->
                        val currency = settingsRepository.getValueByKey(
                            SettingsRepository.Keys.DEFAULT_CURRENCY, "PLN"
                        )
                        cashflowBinding.tvUnpaidTotal.text = CurrencyFormatter.format(state.unpaidTotal, currency)
                        cashflowBinding.tvPaidTotal.text = CurrencyFormatter.format(state.paidTotal, currency)
                        cashflowBinding.tvProjectedBalance.text = CurrencyFormatter.format(state.projectedBalance, currency)

                        cashflowBinding.tvProjectedBalance.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                if (state.projectedBalance >= 0) R.color.income_green else R.color.expense_red
                            )
                        )

                        cashflowAdapter.currency = currency
                        cashflowAdapter.submitList(state.items)
                        cashflowBinding.rvCashflowBills.isVisible = !state.isEmpty
                        cashflowBinding.emptyCashflowState.isVisible = state.isEmpty
                    }
                }
            }
        }
    }

    private fun showEditBillDialog(bill: com.budgetapp.ocr.data.local.entity.BillEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val accounts = accountRepository.getAllAccountsList()
            val defaultAccountId = accountRepository.getDefaultAccountId()
            val customBillCats = categoryRepository.getCategoriesByType(TransactionType.BILL)
                .first().filter { !it.isSystem }

            val dialog = EditBillDialog.newInstance(
                existingBill = bill,
                accounts = accounts,
                defaultAccountId = defaultAccountId,
                customBillCategories = customBillCats,
                onSave = { vendorName, invoiceNumber, amount, dueDate, issueDate, category,
                           recurrence, reminderEnabled, reminderDays, bankAccount, notes, accountId ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        billRepository.update(
                            bill.copy(
                                vendorName = vendorName,
                                invoiceNumber = invoiceNumber,
                                amount = amount,
                                dueDate = dueDate,
                                issueDate = issueDate,
                                category = category,
                                recurrence = recurrence,
                                reminderEnabled = reminderEnabled,
                                reminderDaysBefore = reminderDays,
                                bankAccount = bankAccount,
                                notes = notes,
                                accountId = accountId
                            )
                        )
                        Toast.makeText(requireContext(), getString(R.string.bill_saved), Toast.LENGTH_SHORT).show()
                    }
                },
                onSaveRecurring = { vendorName, invoiceNumber, amount, issueDate, category,
                                    frequency, durationMonths, paymentDay, startMonth,
                                    reminderEnabled, reminderDays, bankAccount, notes, accountId ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val template = com.budgetapp.ocr.data.local.entity.BillEntity(
                            vendorName = vendorName,
                            invoiceNumber = invoiceNumber,
                            amount = amount,
                            category = category,
                            issueDate = issueDate,
                            dueDate = "",
                            recurrence = frequency,
                            bankAccount = bankAccount,
                            notes = notes,
                            reminderEnabled = reminderEnabled,
                            reminderDaysBefore = reminderDays,
                            accountId = accountId
                        )
                        val count = billRepository.generateAndInsertRecurringBills(
                            template, frequency.toMonths(), durationMonths, paymentDay, startMonth
                        )
                        Toast.makeText(requireContext(), getString(R.string.recurring_bills_saved, count), Toast.LENGTH_SHORT).show()
                    }
                },
                onMarkAsPaid = { billToPay ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        billRepository.markAsPaid(billToPay)
                        Toast.makeText(requireContext(), getString(R.string.bill_marked_paid), Toast.LENGTH_SHORT).show()
                    }
                },
                onMarkAsUnpaid = { billToUnpay ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        billRepository.markAsUnpaid(billToUnpay)
                        Toast.makeText(requireContext(), getString(R.string.bill_marked_unpaid), Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = { billToDelete ->
                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.delete_bill))
                        .setMessage(getString(R.string.confirm_delete))
                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                billRepository.delete(billToDelete)
                                Toast.makeText(requireContext(), getString(R.string.bill_deleted), Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                },
                onDeleteSeries = { groupId ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val count = billRepository.countInRecurringGroup(groupId)
                        billRepository.deleteRecurringGroup(groupId)
                        Toast.makeText(requireContext(), getString(R.string.series_deleted, count), Toast.LENGTH_SHORT).show()
                    }
                }
            )
            dialog.show(childFragmentManager, "edit_bill_cashflow")
        }
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            val currency = settingsRepository.getValueByKey(
                SettingsRepository.Keys.DEFAULT_CURRENCY, "PLN"
            )
            comparisonAdapter.currency = currency
            topStoresAdapter.currency = currency
            budgetAdapter.currency = currency
            accountsAdapter.currency = currency
            cashflowAdapter.currency = currency
        }
        // Refresh account balances every time Finance tab appears
        accountsViewModel.refreshBalances()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _accountsBinding = null
        _budgetBinding = null
        _cashflowBinding = null
        _binding = null
    }
}
