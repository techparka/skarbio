package com.budgetapp.ocr.ui.income

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.AccountEntity
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.databinding.DialogAddIncomeBinding
import com.budgetapp.ocr.ui.shared.DirtyFormDialogFragment
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AddIncomeDialog : DirtyFormDialogFragment() {

    private var _binding: DialogAddIncomeBinding? = null
    private val binding get() = _binding!!

    private var onSaveCallback: ((Double, String, String, String, String, Long) -> Unit)? = null
    private var accounts: List<AccountEntity> = emptyList()
    private var defaultAccountId: Long = 1L

    private val incomeCategoryKeys = CategoryKey.incomeCategories()
    private var customIncomeCategories: List<com.budgetapp.ocr.data.local.entity.CategoryEntity> = emptyList()

    // Dirty form detection
    private var originalAmount: Double = 0.0
    private var originalPayee: String = ""
    private var originalCategory: String = ""
    private var originalDate: String = ""
    private var originalNotes: String = ""
    private var originalAccountId: Long = 1L

    // ── DirtyFormDialogFragment implementation ─────────────────────────────

    override fun isDirty(): Boolean {
        if (_binding == null) return false

        try {
            val currentAmount = binding.etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
            val currentPayee = binding.etPayee.text.toString().trim()
            val currentCategory = binding.spinnerCategory.text.toString()
            val currentDate = binding.etDate.text.toString().trim()
            val currentNotes = binding.etNotes.text.toString().trim()

            val selectedLabel = binding.spinnerAccount.text.toString()
            val selectedIdx = accounts.indexOfFirst { "${it.icon} ${it.name}" == selectedLabel }
            val currentAccountId = if (selectedIdx >= 0) accounts[selectedIdx].id else defaultAccountId

            return currentAmount != originalAmount
                    || currentPayee != originalPayee
                    || currentCategory != originalCategory
                    || currentDate != originalDate
                    || currentNotes != originalNotes
                    || currentAccountId != originalAccountId
        } catch (e: Exception) {
            return false
        }
    }

    override fun onSaveClicked() {
        saveIncome()
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_BudgetOCR)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddIncomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCategoryDropdown()
        setupAccountDropdown()
        setupDatePicker()
        setupButtons()

        // Set default date
        binding.etDate.setText(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))

        // Save original values for dirty form detection
        saveOriginalValues()
    }

    private fun saveOriginalValues() {
        originalAmount = binding.etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
        originalPayee = binding.etPayee.text.toString().trim()
        originalCategory = binding.spinnerCategory.text.toString()
        originalDate = binding.etDate.text.toString().trim()
        originalNotes = binding.etNotes.text.toString().trim()

        val selectedLabel = binding.spinnerAccount.text.toString()
        val selectedIdx = accounts.indexOfFirst { "${it.icon} ${it.name}" == selectedLabel }
        originalAccountId = if (selectedIdx >= 0) accounts[selectedIdx].id else defaultAccountId
    }

    private fun setupCategoryDropdown() {
        val systemLabels = incomeCategoryKeys.map { getString(it.stringResId) }
        val customLabels = customIncomeCategories.map { it.name }
        val labels = systemLabels + customLabels
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            labels
        )
        binding.spinnerCategory.setAdapter(adapter)
        binding.spinnerCategory.setText(labels[0], false)
    }

    private fun setupAccountDropdown() {
        if (accounts.isEmpty()) {
            binding.spinnerAccount.visibility = View.GONE
            return
        }
        val labels = accounts.map { "${it.icon} ${it.name}" }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            labels
        )
        binding.spinnerAccount.setAdapter(adapter)
        // Pre-select default account
        val defaultIdx = accounts.indexOfFirst { it.id == defaultAccountId }.coerceAtLeast(0)
        binding.spinnerAccount.setText(labels[defaultIdx], false)
    }

    private fun setupDatePicker() {
        binding.etDate.setOnClickListener {
            val today = LocalDate.now()
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val date = LocalDate.of(year, month + 1, dayOfMonth)
                    binding.etDate.setText(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                },
                today.year,
                today.monthValue - 1,
                today.dayOfMonth
            ).show()
        }
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            handleDismissRequest()
        }

        binding.btnSave.setOnClickListener {
            saveIncome()
        }
    }

    private fun saveIncome() {
        val amountStr = binding.etAmount.text.toString().trim()
        val amount = amountStr.toDoubleOrNull()

        if (amount == null || amount <= 0 || amount > 999_999.99) {
            Toast.makeText(requireContext(), getString(R.string.enter_valid_amount), Toast.LENGTH_SHORT).show()
            return
        }

        val payee = binding.etPayee.text.toString().trim().ifEmpty { getString(R.string.payer_not_specified) }
        val categoryLabel = binding.spinnerCategory.text.toString()
        // Resolve label back to CategoryKey name
        val systemMatch = incomeCategoryKeys.firstOrNull { getString(it.stringResId) == categoryLabel }
        val category = if (systemMatch != null) {
            systemMatch.name
        } else {
            // Custom category — label IS the category name
            categoryLabel
        }
        val date = binding.etDate.text.toString()
        val notes = binding.etNotes.text.toString().trim()

        // Resolve selected account
        val selectedLabel = binding.spinnerAccount.text.toString()
        val selectedIdx = accounts.indexOfFirst { "${it.icon} ${it.name}" == selectedLabel }
        val accountId = if (selectedIdx >= 0) accounts[selectedIdx].id else defaultAccountId

        onSaveCallback?.invoke(amount, payee, category, date, notes, accountId)
        forceDismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            accounts: List<AccountEntity> = emptyList(),
            defaultAccountId: Long = 1L,
            customIncomeCategories: List<com.budgetapp.ocr.data.local.entity.CategoryEntity> = emptyList(),
            onSave: (amount: Double, payee: String, category: String, date: String, notes: String, accountId: Long) -> Unit
        ): AddIncomeDialog {
            return AddIncomeDialog().apply {
                this.accounts = accounts
                this.defaultAccountId = defaultAccountId
                this.customIncomeCategories = customIncomeCategories
                this.onSaveCallback = onSave
            }
        }
    }
}
