package com.budgetapp.ocr.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.local.entity.toMonths
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.data.repository.BillRepository
import com.budgetapp.ocr.data.repository.CategoryRepository
import com.budgetapp.ocr.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import com.budgetapp.ocr.databinding.FragmentDashboardBinding
import com.budgetapp.ocr.utils.CurrencyFormatter
import com.budgetapp.ocr.ui.bills.BillsListAdapter
import com.budgetapp.ocr.ui.bills.EditBillDialog
import com.budgetapp.ocr.ui.common.CategoryIcons
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import androidx.fragment.app.activityViewModels
import com.budgetapp.ocr.ui.shared.PendingFilter
import com.budgetapp.ocr.ui.shared.SharedFilterViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private val sharedFilterViewModel: SharedFilterViewModel by activityViewModels()
    private lateinit var categoryAdapter: CategoryBreakdownAdapter
    private lateinit var unpaidBillsAdapter: BillsListAdapter
    private lateinit var budgetAlertAdapter: BudgetAlertAdapter
    private lateinit var accountBalanceAdapter: AccountBalanceAdapter

    @Inject lateinit var billRepository: BillRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var accountRepository: com.budgetapp.ocr.data.repository.AccountRepository
    @Inject lateinit var categoryRepository: CategoryRepository

    private var currentCurrency = "PLN"
    private var lastSelectedCategory: String? = null
    private var lastSelectionTime: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupAccountBalances()
        setupUnpaidBillsList()
        setupBudgetAlerts()
        setupPieChart()
        setupMonthNavigation()
        setupHeroMetricPicker()
        setupHeroPanelClicks()
        observeState()
        observeAccountBalances()
        observeHeroState()
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryBreakdownAdapter { summary ->
            val yearMonthStr = viewModel.selectedMonth.value
                .format(DateTimeFormatter.ofPattern("yyyy-MM"))
            // Pass filter via SharedViewModel, then switch the bottom nav tab
            // — no back-stack entry is added, so Back works normally
            val isBillCategory = CategoryKey.billCategories()
                .any { it.name == summary.category }
            sharedFilterViewModel.setPendingFilter(
                PendingFilter(
                    category = summary.category,
                    yearMonth = yearMonthStr,
                    targetTab = if (isBillCategory) 1 else 0
                )
            )
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav)
                .selectedItemId = R.id.transactionsFragment
        }
        binding.rvCategoryBreakdown.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }
    }

    private fun setupAccountBalances() {
        accountBalanceAdapter = AccountBalanceAdapter { item ->
            // Navigate to Finance tab (Konta)
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav)
                .selectedItemId = R.id.reportsFragment
        }
        binding.rvAccountBalances.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = accountBalanceAdapter
        }
    }

    private fun observeAccountBalances() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.accountBalances.collect { balances ->
                    accountBalanceAdapter.currency = currentCurrency
                    accountBalanceAdapter.submitList(balances)
                    binding.rvAccountBalances.isVisible = balances.isNotEmpty()
                }
            }
        }
    }

    private fun setupUnpaidBillsList() {
        unpaidBillsAdapter = BillsListAdapter(
            onItemClick = { bill ->
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
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
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
                    dialog.show(childFragmentManager, "edit_bill_dashboard")
                }
            }
        )
        binding.rvUnpaidBills.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = unpaidBillsAdapter
        }
    }

    private fun setupBudgetAlerts() {
        budgetAlertAdapter = BudgetAlertAdapter { _ ->
            sharedFilterViewModel.triggerBudgetsNavigation()
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav)
                .selectedItemId = R.id.reportsFragment
        }
        binding.rvBudgetAlerts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = budgetAlertAdapter
        }
    }

    private fun setupPieChart() {
        binding.pieChart.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 48f
            transparentCircleRadius = 52f
            setHoleColor(ContextCompat.getColor(requireContext(), R.color.surface))
            setTransparentCircleColor(ContextCompat.getColor(requireContext(), R.color.surface))
            setDrawEntryLabels(false)
            setDrawCenterText(true)
            setCenterTextSize(13f)
            setCenterTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            legend.isEnabled = true
            legend.textSize = 11f
            legend.textColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface))
            setNoDataText(getString(R.string.no_data))
            setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
                override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                    val pieEntry = e as? PieEntry ?: return
                    val categoryName = pieEntry.data as? String ?: return
                    // First tap → show category name in center
                    lastSelectedCategory = categoryName
                    lastSelectionTime = System.currentTimeMillis()
                    binding.pieChart.setCenterText(pieEntry.label)
                }
                override fun onNothingSelected() {
                    val elapsed = System.currentTimeMillis() - lastSelectionTime
                    val category = lastSelectedCategory
                    if (category != null && elapsed < 800) {
                        // Tap on same segment again (deselect) → navigate
                        lastSelectedCategory = null
                        binding.pieChart.setCenterText("")
                        navigateToFilteredTransactions(category)
                    } else {
                        lastSelectedCategory = null
                        binding.pieChart.setCenterText("")
                    }
                }
            })
        }
    }

    private fun updatePieChart(categories: List<CategorySummary>) {
        if (categories.isEmpty()) {
            binding.cardPieChart.visibility = View.GONE
            binding.pieChart.clear()
            return
        }
        binding.cardPieChart.visibility = View.VISIBLE

        val entries = categories.map { summary ->
            val label = CategoryKey.localizedName(requireContext(), summary.category)
            PieEntry(summary.amount.toFloat(), label, summary.category)
        }
        val colors = categories.map {
            ContextCompat.getColor(requireContext(), CategoryIcons.getColorRes(it.category))
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            sliceSpace = 2f
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            valueFormatter = PercentFormatter(binding.pieChart)
        }

        binding.pieChart.data = PieData(dataSet)
        binding.pieChart.animateY(400)
        binding.pieChart.invalidate()
    }

    private fun navigateToFilteredTransactions(categoryName: String) {
        val yearMonthStr = viewModel.selectedMonth.value
            .format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val isBillCategory = CategoryKey.billCategories()
            .any { it.name == categoryName }
        sharedFilterViewModel.setPendingFilter(
            PendingFilter(
                category = categoryName,
                yearMonth = yearMonthStr,
                targetTab = if (isBillCategory) 1 else 0
            )
        )
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav)
            .selectedItemId = R.id.transactionsFragment
    }

    private fun setupHeroPanelClicks() {
        binding.panelIncome.setOnClickListener {
            navigateToFilteredByType(TransactionType.INCOME)
        }
        binding.panelExpense.setOnClickListener {
            navigateToFilteredByType(TransactionType.EXPENSE)
        }
    }

    private fun navigateToFilteredByType(type: TransactionType) {
        val yearMonthStr = viewModel.selectedMonth.value
            .format(DateTimeFormatter.ofPattern("yyyy-MM"))
        sharedFilterViewModel.setPendingFilter(
            PendingFilter(
                yearMonth = yearMonthStr,
                type = type.name
            )
        )
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav)
            .selectedItemId = R.id.transactionsFragment
    }

    private fun setupMonthNavigation() {
        binding.btnPrevMonth.setOnClickListener {
            viewModel.previousMonth()
        }
        binding.btnNextMonth.setOnClickListener {
            viewModel.nextMonth()
        }
    }

    private fun setupHeroMetricPicker() {
        binding.layoutHeroBalance.setOnClickListener {
            showHeroMetricSheet()
        }
    }

    private fun showHeroMetricSheet() {
        val accounts = viewModel.accountBalances.value
        val heroState = viewModel.heroState.value

        val options = mutableListOf(
            HeroMetricPickerSheet.HeroOption(
                metric = HeroMetric.MONTHLY_RESULT,
                title = getString(R.string.hero_option_monthly),
                subtitle = getString(R.string.balance_subtitle),
                drawableIconRes = R.drawable.ic_transactions,
                iconBgColor = "#6366F1"
            ),
            HeroMetricPickerSheet.HeroOption(
                metric = HeroMetric.TOTAL_BALANCE,
                title = getString(R.string.hero_option_total),
                subtitle = getString(R.string.hero_total_balance_sub),
                drawableIconRes = R.drawable.ic_wallet,
                iconBgColor = "#3B82F6"
            )
        )
        accounts.forEach { account ->
            options.add(
                HeroMetricPickerSheet.HeroOption(
                    metric = HeroMetric.ACCOUNT_BALANCE,
                    accountId = account.accountId,
                    title = account.name,
                    subtitle = formatAmount(account.balance),
                    emojiIcon = account.icon,
                    iconBgColor = account.color
                )
            )
        }

        HeroMetricPickerSheet(
            options = options,
            currentMetric = heroState.metric,
            currentAccountId = heroState.accountId,
            onSelect = { metric, accountId ->
                viewModel.setHeroMetric(metric, accountId)
            }
        ).show(childFragmentManager, "hero_metric_picker")
    }

    private fun observeHeroState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.heroState.collect { heroState ->
                    binding.tvBalance.text = formatAmount(heroState.amount)
                    binding.tvHeroLabel.text = when (heroState.metric) {
                        HeroMetric.MONTHLY_RESULT -> getString(R.string.balance_label)
                        HeroMetric.TOTAL_BALANCE  -> getString(R.string.hero_total_balance)
                        HeroMetric.ACCOUNT_BALANCE -> getString(R.string.hero_account_balance)
                    }
                    binding.tvHeroSubtitle.text = when (heroState.metric) {
                        HeroMetric.MONTHLY_RESULT  -> getString(R.string.balance_subtitle)
                        HeroMetric.TOTAL_BALANCE   -> getString(R.string.hero_total_balance_sub)
                        HeroMetric.ACCOUNT_BALANCE -> heroState.accountName
                    }
                }
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        // Update summary cards
                        binding.tvTotalIncome.text = formatAmount(state.totalIncome)
                        binding.tvTotalExpenses.text = formatAmount(state.totalExpenses)

                        // Update pie chart
                        updatePieChart(state.categoryBreakdown)

                        // Spending progress indicator
                        if (state.totalIncome > 0) {
                            val spendingPercent = (state.totalExpenses / state.totalIncome * 100)
                                .toInt().coerceIn(0, 100)
                            binding.cardSpendingProgress.visibility = View.VISIBLE
                            binding.progressSpending.progress = spendingPercent
                            binding.tvSpendingPercent.text = "$spendingPercent%"
                            val spendingColor = when {
                                spendingPercent >= 100 -> ContextCompat.getColor(requireContext(), R.color.dash_expense)
                                spendingPercent >= 80  -> ContextCompat.getColor(requireContext(), R.color.dash_warning)
                                else                   -> ContextCompat.getColor(requireContext(), R.color.dash_accent)
                            }
                            binding.progressSpending.setIndicatorColor(spendingColor)
                            binding.tvSpendingPercent.setTextColor(spendingColor)
                        } else {
                            binding.cardSpendingProgress.visibility = View.GONE
                        }

                        // Update category breakdown
                        if (state.categoryBreakdown.isEmpty() && !state.isLoading) {
                            binding.emptyState.visibility = View.VISIBLE
                            binding.cardCategoryBreakdown.visibility = View.GONE
                        } else {
                            binding.emptyState.visibility = View.GONE
                            binding.cardCategoryBreakdown.visibility = View.VISIBLE
                            categoryAdapter.submitList(state.categoryBreakdown)
                        }

                        // Budget alerts
                        if (state.budgetAlerts.isEmpty()) {
                            binding.cardBudgetAlerts.visibility = View.GONE
                        } else {
                            binding.cardBudgetAlerts.visibility = View.VISIBLE
                            budgetAlertAdapter.submitList(state.budgetAlerts)
                        }
                    }
                }

                launch {
                    viewModel.selectedMonth.collect { yearMonth ->
                        binding.tvCurrentMonth.text = formatMonth(yearMonth)
                    }
                }

                launch {
                    viewModel.unpaidBills.collect { bills ->
                        if (bills.isEmpty()) {
                            binding.cardUnpaidBills.visibility = View.GONE
                        } else {
                            binding.cardUnpaidBills.visibility = View.VISIBLE
                            unpaidBillsAdapter.submitList(bills)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            currentCurrency = settingsRepository.getValueByKey(
                SettingsRepository.Keys.DEFAULT_CURRENCY, "PLN"
            )
            categoryAdapter.currency = currentCurrency
            unpaidBillsAdapter.currency = currentCurrency
            accountBalanceAdapter.currency = currentCurrency
        }
        // Refresh account balances every time Dashboard appears
        viewModel.refreshAccountBalances()
    }

    private fun formatAmount(amount: Double): String =
        CurrencyFormatter.format(amount, currentCurrency)

    private fun formatMonth(yearMonth: YearMonth): String {
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
        return "${monthName.replaceFirstChar { it.uppercase() }} ${yearMonth.year}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
