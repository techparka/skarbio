package com.budgetapp.ocr.ui.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.BillEntity
import com.budgetapp.ocr.data.local.entity.BillRecurrence
import com.budgetapp.ocr.data.local.entity.BillStatus
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.local.entity.toMonths
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.data.repository.AccountRepository
import com.budgetapp.ocr.data.repository.BillRepository
import com.budgetapp.ocr.data.repository.CategoryRepository
import com.budgetapp.ocr.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import com.budgetapp.ocr.databinding.FragmentTransactionsBinding
import com.budgetapp.ocr.ui.bills.BillsListAdapter
import com.budgetapp.ocr.ui.bills.EditBillDialog
import com.budgetapp.ocr.ui.expenses.BillFilterParams
import com.budgetapp.ocr.ui.expenses.BillSortOrder
import com.budgetapp.ocr.ui.expenses.DateRange
import com.budgetapp.ocr.ui.expenses.ExpenseListViewModel
import com.budgetapp.ocr.ui.expenses.FilterParams
import com.budgetapp.ocr.ui.expenses.SortOrder
import com.budgetapp.ocr.ui.shared.SharedFilterViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class TransactionsFragment : Fragment() {

    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExpenseListViewModel by viewModels()
    private val sharedFilterViewModel: SharedFilterViewModel by activityViewModels()
    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var billsAdapter: BillsListAdapter
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var concatAdapter: ConcatAdapter
    private lateinit var transactionsHeaderAdapter: SectionHeaderAdapter
    private lateinit var billsHeaderAdapter: SectionHeaderAdapter
    private var currentTab = 0 // 0 = Transactions, 1 = Bills
    private var selectionType = 0 // 0 = transactions, 1 = bills, 2 = unified
    private var isSelectionMode = false

    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var billRepository: BillRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var categoryRepository: CategoryRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Restore tab state before setting up UI
        savedInstanceState?.let {
            currentTab = it.getInt("currentTab", 0)
        }

        setupRecyclerView()
        setupBillsRecyclerView()
        setupSearch()
        setupFilterChips()
        setupSwipeGesture()
        setupFilterButton()
        setupSortButton()
        setupActiveFilterChips()
        setupBackPressHandler()
        observeState()
        observeUnifiedHistory()
        observeFilters()
        observeBillFilters()
        observeBills()
        observePendingFilter()

        // Restore correct chip selection after state restore
        when (currentTab) {
            1 -> binding.chipBills.isChecked = true
            else -> binding.chipAll.isChecked = true
        }

        // Ensure visibility is correct at startup
        updateChipVisibility()
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isSelectionMode) {
                        exitSelectionMode()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun updateChipVisibility() {
        // Exit selection mode when switching tabs
        if (isSelectionMode) {
            exitSelectionMode()
        }

        updateAdaptersForTab()

        when (currentTab) {
            0 -> {
                binding.tvHeader.text = getString(R.string.transactions_title)
                updateUnifiedVisibility()
                updateActiveFilterChips(viewModel.filterParams.value)
            }
            1 -> {
                binding.tvHeader.text = getString(R.string.bills_title)
                updateUnifiedVisibility()
                updateBillActiveFilterChips(viewModel.billFilterParams.value)
            }
        }
    }

    private fun updateAdaptersForTab() {
        concatAdapter.adapters.toList().forEach { concatAdapter.removeAdapter(it) }

        when {
            currentTab == 1 -> {
                // Only bills
                concatAdapter.addAdapter(billsAdapter)
            }
            viewModel.filterParams.value.type != null -> {
                // Only transactions (expenses or income)
                concatAdapter.addAdapter(transactionAdapter)
            }
            else -> {
                // All — unified chronological list
                concatAdapter.addAdapter(historyAdapter)
            }
        }
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(
            onItemClick = { transaction ->
                editTransaction(transaction)
            },
            onItemLongClick = { transaction ->
                selectionType = 0
                enterSelectionMode()
                transactionAdapter.toggleSelection(transaction)
                updateSelectionCount()
            },
            onSelectionChanged = {
                updateSelectionCount()
            }
        )

        billsAdapter = BillsListAdapter(
            onItemClick = { bill ->
                if (!isSelectionMode) {
                    showBillEditDialog(bill)
                }
            },
            onItemLongClick = { bill ->
                selectionType = 1
                enterSelectionMode()
                billsAdapter.toggleSelection(bill)
                updateSelectionCount()
                true
            },
            onSelectionChanged = {
                updateSelectionCount()
            }
        )

        historyAdapter = HistoryAdapter(
            onTransactionClick = { editTransaction(it) },
            onBillClick = { showBillEditDialog(it) },
            onItemLongClick = { item ->
                selectionType = 2
                enterSelectionMode()
                historyAdapter.toggleSelection(item)
                updateSelectionCount()
            },
            onSelectionChanged = { updateSelectionCount() }
        )

        transactionsHeaderAdapter = SectionHeaderAdapter(getString(R.string.transactions_title))
        billsHeaderAdapter = SectionHeaderAdapter(getString(R.string.bills_title))
        concatAdapter = ConcatAdapter(historyAdapter)

        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = concatAdapter
        }
    }

    private fun setupBillsRecyclerView() {
        // Setup selection action buttons
        binding.btnCancelSelection.setOnClickListener {
            exitSelectionMode()
        }

        binding.btnMarkAsPaid.setOnClickListener {
            if (selectionType == 2) {
                markSelectedUnifiedBillsAsPaid()
            } else {
                markSelectedBillsAsPaid()
            }
        }

        binding.btnDeleteSelected.setOnClickListener {
            when (selectionType) {
                0 -> deleteSelectedTransactions()
                1 -> deleteSelectedBills()
                2 -> deleteSelectedUnified()
            }
        }
    }

    private fun showBillEditDialog(bill: BillEntity) {
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
            dialog.show(childFragmentManager, "edit_bill")
        }
    }

    private fun editTransaction(transaction: com.budgetapp.ocr.data.local.entity.TransactionEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val expenseCats = categoryRepository.getCategoriesByType(TransactionType.EXPENSE)
                .first().filter { !it.isSystem }
            val incomeCats = categoryRepository.getCategoriesByType(TransactionType.INCOME)
                .first().filter { !it.isSystem }

            val dialog = EditTransactionDialog.newInstance(
                transaction = transaction,
                onSave = { updatedTransaction ->
                    viewModel.updateTransaction(updatedTransaction)
                    Toast.makeText(requireContext(), getString(R.string.bill_saved), Toast.LENGTH_SHORT).show()
                },
                onDelete = { txToDelete ->
                    deleteTransaction(txToDelete)
                },
                customExpenseCategories = expenseCats,
                customIncomeCategories = incomeCats
            )
            dialog.show(childFragmentManager, "edit_transaction")
        }
    }

    private fun deleteTransaction(transaction: com.budgetapp.ocr.data.local.entity.TransactionEntity) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_transaction))
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteTransaction(transaction)
                Toast.makeText(requireContext(), getString(R.string.transaction_deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            val query = text?.toString() ?: ""
            viewModel.setSearchQuery(query)
            viewModel.setBillSearchQuery(query)
        }
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener {
            currentTab = 0
            viewModel.setFilterType(null)
            updateChipVisibility()
        }
        binding.chipExpenses.setOnClickListener {
            currentTab = 0
            viewModel.setFilterType(TransactionType.EXPENSE)
            updateChipVisibility()
        }
        binding.chipIncome.setOnClickListener {
            currentTab = 0
            viewModel.setFilterType(TransactionType.INCOME)
            updateChipVisibility()
        }
        binding.chipBills.setOnClickListener {
            currentTab = 1
            updateChipVisibility()
        }
    }

    private val chipOrder = listOf(0, 1, 2, 3) // All, Expenses, Income, Bills

    private fun getCurrentChipIndex(): Int = when {
        currentTab == 1 -> 3
        viewModel.filterParams.value.type == TransactionType.EXPENSE -> 1
        viewModel.filterParams.value.type == TransactionType.INCOME -> 2
        else -> 0
    }

    private fun selectChipByIndex(index: Int) {
        when (index) {
            0 -> { binding.chipAll.isChecked = true; currentTab = 0; viewModel.setFilterType(null) }
            1 -> { binding.chipExpenses.isChecked = true; currentTab = 0; viewModel.setFilterType(TransactionType.EXPENSE) }
            2 -> { binding.chipIncome.isChecked = true; currentTab = 0; viewModel.setFilterType(TransactionType.INCOME) }
            3 -> { binding.chipBills.isChecked = true; currentTab = 1 }
        }
        updateChipVisibility()
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGesture() {
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (Math.abs(diffX) <= Math.abs(diffY)) return false
                if (Math.abs(diffX) < SWIPE_THRESHOLD || Math.abs(velocityX) < SWIPE_VELOCITY_THRESHOLD) return false

                val current = getCurrentChipIndex()
                val next = if (diffX < 0) {
                    // Swipe left → next chip
                    (current + 1).coerceAtMost(3)
                } else {
                    // Swipe right → previous chip
                    (current - 1).coerceAtLeast(0)
                }
                if (next != current) {
                    selectChipByIndex(next)
                    return true
                }
                return false
            }
        })

        binding.rvTransactions.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            false // Don't consume — let RV scroll normally
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    transactionAdapter.submitList(state.transactions)
                    updateUnifiedVisibility()
                }
            }
        }
    }

    private fun observeBills() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    billsAdapter.submitList(state.bills)
                    updateUnifiedVisibility()
                }
            }
        }
    }

    private fun observeUnifiedHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.unifiedHistory.collect { items ->
                    historyAdapter.submitList(items)
                    if (currentTab == 0 && viewModel.filterParams.value.type == null) {
                        updateUnifiedVisibility()
                    }
                }
            }
        }
    }

    private fun updateUnifiedVisibility() {
        val state = viewModel.state.value
        val transactions = state.transactions
        val bills = state.bills
        val isLoading = state.isLoading

        val isEmpty = when {
            currentTab == 1 -> bills.isEmpty()
            viewModel.filterParams.value.type != null -> transactions.isEmpty() && !isLoading
            else -> transactions.isEmpty() && bills.isEmpty() && !isLoading // "All" tab
        }

        if (isEmpty) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvTransactions.visibility = View.GONE
            if (currentTab == 1) {
                binding.tvEmptyTitle.text = getString(R.string.no_bills)
                binding.tvEmptySubtitle.text = getString(R.string.add_transactions_hint)
            } else {
                binding.tvEmptyTitle.text = getString(R.string.no_transactions)
                binding.tvEmptySubtitle.text = getString(R.string.scan_first_receipt)
            }
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvTransactions.visibility = View.VISIBLE
        }
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        when (selectionType) {
            0 -> transactionAdapter.isSelectionMode = true
            1 -> billsAdapter.isSelectionMode = true
            2 -> historyAdapter.isSelectionMode = true
        }
        binding.selectionActionBar.visibility = View.VISIBLE
        binding.chipGroup.visibility = View.GONE
        binding.searchLayout.visibility = View.GONE

        // Show/hide Mark as Paid button depending on selection type
        binding.btnMarkAsPaid.visibility = if (selectionType == 1) View.VISIBLE else View.GONE

        updateSelectionCount()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        transactionAdapter.isSelectionMode = false
        transactionAdapter.clearSelection()
        billsAdapter.isSelectionMode = false
        billsAdapter.clearSelection()
        historyAdapter.isSelectionMode = false
        historyAdapter.clearSelection()
        binding.selectionActionBar.visibility = View.GONE
        binding.chipGroup.visibility = View.VISIBLE
        binding.searchLayout.visibility = View.VISIBLE
    }

    private fun updateSelectionCount() {
        val count = when (selectionType) {
            0 -> transactionAdapter.getSelectedCount()
            1 -> billsAdapter.getSelectedCount()
            2 -> historyAdapter.getSelectedCount()
            else -> 0
        }
        binding.tvSelectionCount.text = "$count"

        // In unified mode, show "Mark as Paid" only when all selected items are bills
        if (selectionType == 2) {
            binding.btnMarkAsPaid.visibility =
                if (historyAdapter.hasOnlyBillsSelected()) View.VISIBLE else View.GONE
        }

        // Enable/disable action buttons based on selection
        binding.btnMarkAsPaid.isEnabled = count > 0
        binding.btnDeleteSelected.isEnabled = count > 0
    }

    private fun deleteSelectedTransactions() {
        val selectedTransactions = transactionAdapter.getSelectedTransactions()
        if (selectedTransactions.isEmpty()) return

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_transaction))
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                selectedTransactions.forEach { transaction ->
                    viewModel.deleteTransaction(transaction)
                }
                Toast.makeText(requireContext(), getString(R.string.transaction_deleted), Toast.LENGTH_SHORT).show()
                exitSelectionMode()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteSelectedBills() {
        val selectedBills = billsAdapter.getSelectedBills()
        if (selectedBills.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.no_bills), Toast.LENGTH_SHORT).show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_bill))
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    selectedBills.forEach { bill ->
                        billRepository.delete(bill)
                    }
                    Toast.makeText(requireContext(), getString(R.string.bill_deleted), Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun markSelectedBillsAsPaid() {
        val selectedBills = billsAdapter.getSelectedBills()
        if (selectedBills.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.no_bills), Toast.LENGTH_SHORT).show()
            return
        }

        val unpaidCount = selectedBills.count { it.status != BillStatus.PAID }

        if (unpaidCount == 0) {
            Toast.makeText(requireContext(), getString(R.string.bill_marked_paid), Toast.LENGTH_SHORT).show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.mark_as_paid))
            .setMessage(getString(R.string.confirm))
            .setPositiveButton(getString(R.string.mark_as_paid)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    selectedBills.forEach { bill ->
                        if (bill.status != BillStatus.PAID) {
                            billRepository.markAsPaid(bill)
                        }
                    }
                    Toast.makeText(requireContext(), getString(R.string.bill_marked_paid), Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteSelectedUnified() {
        val selectedTx = historyAdapter.getSelectedTransactions()
        val selectedBills = historyAdapter.getSelectedBills()
        if (selectedTx.isEmpty() && selectedBills.isEmpty()) return

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete))
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    selectedTx.forEach { viewModel.deleteTransaction(it) }
                    selectedBills.forEach { billRepository.delete(it) }
                    Toast.makeText(requireContext(), getString(R.string.transaction_deleted), Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun markSelectedUnifiedBillsAsPaid() {
        val selectedBills = historyAdapter.getSelectedBills()
        if (selectedBills.isEmpty()) return

        val unpaidCount = selectedBills.count { it.status != BillStatus.PAID }
        if (unpaidCount == 0) {
            Toast.makeText(requireContext(), getString(R.string.bill_marked_paid), Toast.LENGTH_SHORT).show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.mark_as_paid))
            .setMessage(getString(R.string.confirm))
            .setPositiveButton(getString(R.string.mark_as_paid)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    selectedBills.forEach { bill ->
                        if (bill.status != BillStatus.PAID) {
                            billRepository.markAsPaid(bill)
                        }
                    }
                    Toast.makeText(requireContext(), getString(R.string.bill_marked_paid), Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun observePendingFilter() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedFilterViewModel.pendingFilter.collect { filter ->
                    if (filter != null) {
                        if (filter.targetTab == 1) {
                            // Switch to Bills chip
                            currentTab = 1
                            binding.chipBills.isChecked = true
                            updateChipVisibility()
                            filter.category?.let { viewModel.setBillFilterCategory(it) }
                            filter.yearMonth?.let { viewModel.setBillSpecificMonth(it) }
                        } else {
                            // Ensure we're on Transactions chips
                            currentTab = 0
                            binding.chipAll.isChecked = true
                            updateChipVisibility()
                            filter.category?.let { viewModel.setFilterCategory(it) }
                            filter.yearMonth?.let { viewModel.setSpecificMonth(it) }
                            filter.type?.let { typeName ->
                                val txType = TransactionType.valueOf(typeName)
                                viewModel.setFilterType(txType)
                                when (txType) {
                                    TransactionType.EXPENSE -> binding.chipExpenses.isChecked = true
                                    TransactionType.INCOME -> binding.chipIncome.isChecked = true
                                    else -> {}
                                }
                            }
                        }
                        sharedFilterViewModel.consume()
                    }
                }
            }
        }
    }

    private fun setupFilterButton() {
        binding.btnFilter.setOnClickListener { showFilterDialog() }
    }

    private fun setupSortButton() {
        binding.btnSort.setOnClickListener { showSortDialog() }
    }

    private fun showFilterDialog() {
        if (currentTab == 1) { showBillFilterDialog(); return }

        viewLifecycleOwner.lifecycleScope.launch {
            val expenseCats = categoryRepository.getCategoriesByType(TransactionType.EXPENSE).first()
            val incomeCats = categoryRepository.getCategoriesByType(TransactionType.INCOME).first()
            val allCats = expenseCats + incomeCats
            val categoryNames = listOf<String?>(null) + allCats.map { it.name }
            val categoryLabels = listOf(getString(R.string.all_tab)) + allCats.map { cat ->
                if (cat.isSystem) CategoryKey.localizedName(requireContext(), cat.name) else cat.name
            }

            val accounts = accountRepository.getAllAccountsList()
            val accountIds = listOf<Long?>(null) + accounts.map { it.id }
            val accountLabels = listOf(getString(R.string.all_tab)) + accounts.map { "${it.icon} ${it.name}" }

            val params = viewModel.filterParams.value
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_filter, null)

            // --- Period ---
            val rgPeriod = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgPeriod)
            val periodRadioId = when (params.dateRange) {
                DateRange.THIS_MONTH    -> R.id.rbPeriodThisMonth
                DateRange.LAST_3_MONTHS -> R.id.rbPeriodLast3
                DateRange.LAST_6_MONTHS -> R.id.rbPeriodLast6
                DateRange.LAST_YEAR     -> R.id.rbPeriodLastYear
                else                    -> R.id.rbPeriodAll
            }
            rgPeriod.check(periodRadioId)

            // --- Category ---
            val spinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerCategory)
            spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryLabels)
                .also { it.setDropDownViewResource(R.layout.item_spinner_dialog) }
            val currentCatIndex = categoryNames.indexOfFirst { it == params.category }.coerceAtLeast(0)
            spinner.setSelection(currentCatIndex)

            // --- Account ---
            dialogView.findViewById<View>(R.id.dividerAccount).visibility = View.VISIBLE
            dialogView.findViewById<View>(R.id.accountHeaderLayout).visibility = View.VISIBLE
            val spinnerAccount = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerAccount)
            spinnerAccount.visibility = View.VISIBLE
            spinnerAccount.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, accountLabels)
                .also { it.setDropDownViewResource(R.layout.item_spinner_dialog) }
            val currentAccIndex = accountIds.indexOfFirst { it == params.accountId }.coerceAtLeast(0)
            spinnerAccount.setSelection(currentAccIndex)

            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.filter_sort))
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val selectedRange = when (rgPeriod.checkedRadioButtonId) {
                        R.id.rbPeriodThisMonth -> DateRange.THIS_MONTH
                        R.id.rbPeriodLast3    -> DateRange.LAST_3_MONTHS
                        R.id.rbPeriodLast6    -> DateRange.LAST_6_MONTHS
                        R.id.rbPeriodLastYear -> DateRange.LAST_YEAR
                        else                  -> DateRange.ALL
                    }
                    val selectedCat = categoryNames[spinner.selectedItemPosition]
                    val selectedAcc = accountIds[spinnerAccount.selectedItemPosition]
                    viewModel.setDateRange(selectedRange)
                    viewModel.setFilterCategory(selectedCat)
                    viewModel.setFilterAccount(selectedAcc)
                    // Propagate date filter to bills for unified "All" view
                    if (viewModel.filterParams.value.type == null) {
                        viewModel.setBillDateRange(selectedRange)
                        viewModel.setBillFilterCategory(selectedCat)
                    }
                }
                .setNeutralButton(getString(R.string.clear_filters)) { _, _ ->
                    viewModel.clearFilters()
                    // Also clear bill filters when in "All" view
                    if (viewModel.filterParams.value.type == null) {
                        viewModel.clearBillFilters()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showBillFilterDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val billCats = categoryRepository.getCategoriesByType(TransactionType.BILL).first()
            val categoryNames = listOf<String?>(null) + billCats.map { it.name }
            val categoryLabels = listOf(getString(R.string.all_tab)) + billCats.map { cat ->
                if (cat.isSystem) CategoryKey.localizedName(requireContext(), cat.name) else cat.name
            }

            val params = viewModel.billFilterParams.value
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_filter, null)

            val rgPeriod = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgPeriod)
            val periodRadioId = when (params.dateRange) {
                DateRange.THIS_MONTH    -> R.id.rbPeriodThisMonth
                DateRange.LAST_3_MONTHS -> R.id.rbPeriodLast3
                DateRange.LAST_6_MONTHS -> R.id.rbPeriodLast6
                DateRange.LAST_YEAR     -> R.id.rbPeriodLastYear
                else                    -> R.id.rbPeriodAll
            }
            rgPeriod.check(periodRadioId)

            val spinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerCategory)
            spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryLabels)
                .also { it.setDropDownViewResource(R.layout.item_spinner_dialog) }
            val currentCatIndex = categoryNames.indexOfFirst { it == params.category }.coerceAtLeast(0)
            spinner.setSelection(currentCatIndex)

            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.filter_sort))
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val selectedRange = when (rgPeriod.checkedRadioButtonId) {
                        R.id.rbPeriodThisMonth -> DateRange.THIS_MONTH
                        R.id.rbPeriodLast3    -> DateRange.LAST_3_MONTHS
                        R.id.rbPeriodLast6    -> DateRange.LAST_6_MONTHS
                        R.id.rbPeriodLastYear -> DateRange.LAST_YEAR
                        else                  -> DateRange.ALL
                    }
                    val selectedCat = categoryNames[spinner.selectedItemPosition]
                    viewModel.setBillDateRange(selectedRange)
                    viewModel.setBillFilterCategory(selectedCat)
                }
                .setNeutralButton(getString(R.string.clear_filters)) { _, _ ->
                    viewModel.clearBillFilters()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showSortDialog() {
        if (currentTab == 1) { showBillSortDialog(); return }

        val params = viewModel.filterParams.value
        val sortLabels = arrayOf(
            getString(R.string.sort_newest),
            getString(R.string.sort_oldest),
            getString(R.string.sort_highest),
            getString(R.string.sort_lowest)
        )
        val sortValues = arrayOf(SortOrder.NEWEST, SortOrder.OLDEST, SortOrder.HIGHEST, SortOrder.LOWEST)
        val currentIndex = sortValues.indexOf(params.sortOrder).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sort_label))
            .setSingleChoiceItems(sortLabels, currentIndex) { dialog, which ->
                viewModel.setSortOrder(sortValues[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showBillSortDialog() {
        val params = viewModel.billFilterParams.value
        val sortLabels = arrayOf(
            getString(R.string.sort_due_soon),
            getString(R.string.sort_due_late),
            getString(R.string.sort_highest),
            getString(R.string.sort_lowest)
        )
        val sortValues = arrayOf(
            BillSortOrder.DUE_SOON, BillSortOrder.DUE_LATE,
            BillSortOrder.AMOUNT_HIGH, BillSortOrder.AMOUNT_LOW
        )
        val currentIndex = sortValues.indexOf(params.sortOrder).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sort_label))
            .setSingleChoiceItems(sortLabels, currentIndex) { dialog, which ->
                viewModel.setBillSortOrder(sortValues[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupActiveFilterChips() {
        binding.chipActiveDateFilter.setOnCloseIconClickListener {
            if (currentTab == 0) {
                viewModel.setDateRange(DateRange.ALL)
                // Also clear bill date filter in "All" view
                if (viewModel.filterParams.value.type == null) {
                    viewModel.setBillDateRange(DateRange.ALL)
                }
            } else {
                viewModel.setBillDateRange(DateRange.ALL)
            }
        }
        binding.chipActiveCategoryFilter.setOnCloseIconClickListener {
            if (currentTab == 0) {
                viewModel.setFilterCategory(null)
                if (viewModel.filterParams.value.type == null) {
                    viewModel.setBillFilterCategory(null)
                }
            } else {
                viewModel.setBillFilterCategory(null)
            }
        }
        binding.chipActiveAccountFilter.setOnCloseIconClickListener {
            viewModel.setFilterAccount(null)
        }
    }

    private fun observeFilters() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filterParams.collect { params ->
                if (currentTab == 0) updateActiveFilterChips(params)
            }
        }
    }

    private fun observeBillFilters() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.billFilterParams.collect { params ->
                if (currentTab == 1) updateBillActiveFilterChips(params)
            }
        }
    }

    private fun updateActiveFilterChips(params: FilterParams) {
        // Date filter chip
        val showDateChip = params.dateRange != DateRange.ALL
        binding.chipActiveDateFilter.isVisible = showDateChip
        if (showDateChip) {
            binding.chipActiveDateFilter.text = when (params.dateRange) {
                DateRange.THIS_MONTH    -> getString(R.string.period_this_month)
                DateRange.LAST_3_MONTHS -> getString(R.string.period_last_3_months)
                DateRange.LAST_6_MONTHS -> getString(R.string.period_last_6_months)
                DateRange.LAST_YEAR     -> getString(R.string.period_last_year)
                DateRange.SPECIFIC_MONTH -> {
                    // Format "yyyy-MM" → localized "Month YYYY"
                    runCatching {
                        val ym = YearMonth.parse(params.specificMonth, DateTimeFormatter.ofPattern("yyyy-MM"))
                        val name = ym.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Locale.getDefault())
                        "${name.replaceFirstChar { it.uppercase() }} ${ym.year}"
                    }.getOrDefault(params.specificMonth)
                }
                else -> ""
            }
        }

        // Category filter chip
        val showCatChip = !params.category.isNullOrBlank()
        binding.chipActiveCategoryFilter.isVisible = showCatChip
        if (showCatChip) {
            val localizedName = CategoryKey.localizedName(requireContext(), params.category!!)
            binding.chipActiveCategoryFilter.text =
                getString(R.string.filtered_by_category, localizedName)
        }

        // Account filter chip
        val showAccChip = params.accountId != null
        binding.chipActiveAccountFilter.isVisible = showAccChip
        if (showAccChip) {
            viewLifecycleOwner.lifecycleScope.launch {
                val account = accountRepository.getAccountById(params.accountId!!)
                binding.chipActiveAccountFilter.text =
                    getString(R.string.filtered_by_account, account?.name ?: "?")
            }
        }

        // Show/hide the whole group
        binding.chipGroupActive.isVisible = showDateChip || showCatChip || showAccChip
    }

    private fun updateBillActiveFilterChips(params: BillFilterParams) {
        val showDateChip = params.dateRange != DateRange.ALL
        binding.chipActiveDateFilter.isVisible = showDateChip
        if (showDateChip) {
            binding.chipActiveDateFilter.text = when (params.dateRange) {
                DateRange.THIS_MONTH    -> getString(R.string.period_this_month)
                DateRange.LAST_3_MONTHS -> getString(R.string.period_last_3_months)
                DateRange.LAST_6_MONTHS -> getString(R.string.period_last_6_months)
                DateRange.LAST_YEAR     -> getString(R.string.period_last_year)
                DateRange.SPECIFIC_MONTH -> {
                    runCatching {
                        val ym = YearMonth.parse(params.specificMonth, DateTimeFormatter.ofPattern("yyyy-MM"))
                        val name = ym.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
                        "${name.replaceFirstChar { it.uppercase() }} ${ym.year}"
                    }.getOrDefault(params.specificMonth)
                }
                else -> ""
            }
        }

        val showCatChip = !params.category.isNullOrBlank()
        binding.chipActiveCategoryFilter.isVisible = showCatChip
        if (showCatChip) {
            val localizedName = CategoryKey.localizedName(requireContext(), params.category!!)
            binding.chipActiveCategoryFilter.text =
                getString(R.string.filtered_by_category, localizedName)
        }

        binding.chipActiveAccountFilter.isVisible = false
        binding.chipGroupActive.isVisible = showDateChip || showCatChip
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            val currency = settingsRepository.getValueByKey(
                SettingsRepository.Keys.DEFAULT_CURRENCY, "PLN"
            )
            transactionAdapter.currency = currency
            billsAdapter.currency = currency
            historyAdapter.currency = currency
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentTab", currentTab)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
