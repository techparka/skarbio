package com.budgetapp.ocr.ui.transactions

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.TransactionEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.databinding.DialogEditTransactionBinding
import com.budgetapp.ocr.ui.shared.DirtyFormDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class EditTransactionDialog : DirtyFormDialogFragment() {

    private var _binding: DialogEditTransactionBinding? = null
    private val binding get() = _binding!!

    private var transaction: TransactionEntity? = null
    private var onSaveCallback: ((TransactionEntity) -> Unit)? = null
    private var onDeleteCallback: ((TransactionEntity) -> Unit)? = null
    private var customExpenseCategories: List<com.budgetapp.ocr.data.local.entity.CategoryEntity> = emptyList()
    private var customIncomeCategories: List<com.budgetapp.ocr.data.local.entity.CategoryEntity> = emptyList()

    // Dirty form detection
    private var originalType: TransactionType = TransactionType.EXPENSE
    private var originalAmount: Double = 0.0
    private var originalName: String = ""
    private var originalCategory: String = ""
    private var originalDate: String = ""
    private var originalNotes: String = ""

    private fun getExpenseCategoryLabels(): List<String> {
        val systemLabels = CategoryKey.expenseCategories().map { key ->
            "${key.emoji} ${getString(key.stringResId)}"
        }
        val customLabels = customExpenseCategories.map { "${it.icon} ${it.name}" }
        return systemLabels + customLabels
    }

    private fun getIncomeCategoryLabels(): List<String> {
        val systemLabels = CategoryKey.incomeCategories().map { key ->
            "${key.emoji} ${getString(key.stringResId)}"
        }
        val customLabels = customIncomeCategories.map { "${it.icon} ${it.name}" }
        return systemLabels + customLabels
    }

    // ── DirtyFormDialogFragment implementation ─────────────────────────────

    override fun isDirty(): Boolean {
        if (_binding == null) return false

        try {
            val currentType = if (binding.chipExpense.isChecked) TransactionType.EXPENSE else TransactionType.INCOME
            val currentAmount = binding.etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
            val currentName = binding.etMerchantName.text.toString().trim()
            val currentCategory = binding.spinnerCategory.text.toString()
            val currentDate = binding.etDate.text.toString().trim()
            val currentNotes = binding.etNotes.text.toString().trim()

            return currentType != originalType
                    || currentAmount != originalAmount
                    || currentName != originalName
                    || currentCategory != originalCategory
                    || currentDate != originalDate
                    || currentNotes != originalNotes
        } catch (e: Exception) {
            return false
        }
    }

    override fun onSaveClicked() {
        saveTransaction()
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
        _binding = DialogEditTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTypeChips()
        setupCategorySpinner()
        setupDatePicker()
        populateFields()
        setupButtons()
        setupReceiptPreview()
    }

    private fun setupTypeChips() {
        val isExpense = transaction?.type == TransactionType.EXPENSE

        binding.chipExpense.isChecked = isExpense
        binding.chipIncome.isChecked = !isExpense

        binding.chipGroupType.setOnCheckedStateChangeListener { _, _ ->
            updateCategorySpinner()
            updateMerchantNameHint()
        }
    }

    private fun updateMerchantNameHint() {
        val isExpense = binding.chipExpense.isChecked
        binding.tilMerchantName.hint = if (isExpense) getString(R.string.store_name_label) else getString(R.string.income_source_label)
    }

    private fun setupCategorySpinner() {
        updateCategorySpinner()
    }

    private fun updateCategorySpinner() {
        val isExpense = binding.chipExpense.isChecked
        val categories = if (isExpense) getExpenseCategoryLabels() else getIncomeCategoryLabels()

        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_category_item,
            categories
        )
        binding.spinnerCategory.setAdapter(adapter)

        // Set selected category if exists
        transaction?.let { trans ->
            val categoryWithIcon = categories.firstOrNull { it.contains(trans.category) }
                ?: run {
                    // Try resolving CategoryKey name to localized label
                    val key = CategoryKey.fromName(trans.category)
                    categories.firstOrNull { it.contains(getString(key.stringResId)) }
                }
            if (categoryWithIcon != null) {
                binding.spinnerCategory.setText(categoryWithIcon, false)
            }
        }
    }

    private fun setupDatePicker() {
        binding.etDate.setOnClickListener {
            val dateStr = transaction?.date ?: LocalDate.now().toString()
            val date = try {
                LocalDate.parse(dateStr)
            } catch (e: Exception) {
                LocalDate.now()
            }

            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                    binding.etDate.setText(selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth
            ).show()
        }
    }

    private fun populateFields() {
        transaction?.let { trans ->
            binding.etAmount.setText(trans.amount.toString())
            binding.etMerchantName.setText(
                if (trans.type == TransactionType.EXPENSE) trans.merchantName else trans.payee
            )
            binding.etDate.setText(trans.date)
            binding.etNotes.setText(trans.notes)
        }

        // Save original values for dirty form detection
        originalType = if (binding.chipExpense.isChecked) TransactionType.EXPENSE else TransactionType.INCOME
        originalAmount = binding.etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
        originalName = binding.etMerchantName.text.toString().trim()
        originalCategory = binding.spinnerCategory.text.toString()
        originalDate = binding.etDate.text.toString().trim()
        originalNotes = binding.etNotes.text.toString().trim()
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            handleDismissRequest()
        }

        binding.btnSave.setOnClickListener {
            saveTransaction()
        }

        binding.btnDelete.setOnClickListener {
            transaction?.let { trans ->
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.delete_transaction))
                    .setMessage(getString(R.string.confirm_delete))
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        onDeleteCallback?.invoke(trans)
                        forceDismiss()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }
    }

    private var isReceiptVisible = false

    private fun setupReceiptPreview() {
        val imagePath = transaction?.receiptImagePath
        if (imagePath.isNullOrBlank() || !java.io.File(imagePath).exists()) {
            binding.btnViewReceipt.visibility = View.GONE
            return
        }
        binding.btnViewReceipt.visibility = View.VISIBLE
        binding.btnViewReceipt.setOnClickListener {
            isReceiptVisible = !isReceiptVisible
            if (isReceiptVisible) {
                binding.cardReceiptPreview.visibility = View.VISIBLE
                binding.btnViewReceipt.setText(R.string.hide_receipt_preview)
                lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(imagePath)
                    }
                    if (bitmap != null && _binding != null) {
                        binding.ivReceiptPreview.setImageBitmap(bitmap)
                    }
                }
            } else {
                binding.cardReceiptPreview.visibility = View.GONE
                binding.btnViewReceipt.setText(R.string.view_receipt)
                binding.ivReceiptPreview.setImageBitmap(null)
            }
        }
    }

    private fun saveTransaction() {
        val amountStr = binding.etAmount.text.toString().trim()
        val amount = amountStr.toDoubleOrNull()

        if (amount == null || amount <= 0 || amount > 999_999.99) {
            Toast.makeText(requireContext(), getString(R.string.enter_valid_amount), Toast.LENGTH_SHORT).show()
            return
        }

        val name = binding.etMerchantName.text.toString().trim()
        val date = binding.etDate.text.toString()
        val notes = binding.etNotes.text.toString().trim()
        val categoryWithIcon = binding.spinnerCategory.text.toString()
        val isExpense = binding.chipExpense.isChecked

        if (categoryWithIcon.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.select_category), Toast.LENGTH_SHORT).show()
            return
        }

        // Resolve display label back to CategoryKey name
        val categoryLabel = categoryWithIcon.substringAfter(" ")
        val keys = if (isExpense) CategoryKey.expenseCategories() else CategoryKey.incomeCategories()
        val systemMatch = keys.firstOrNull { getString(it.stringResId) == categoryLabel }
        val category = if (systemMatch != null) {
            systemMatch.name
        } else {
            // Custom category — label IS the category name
            categoryLabel
        }
        val type = if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME

        val updatedTransaction = transaction?.copy(
            type = type,
            amount = amount,
            category = category,
            merchantName = if (isExpense) name else null,
            payee = if (!isExpense) name else null,
            date = date,
            notes = notes
        ) ?: return

        onSaveCallback?.invoke(updatedTransaction)
        forceDismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            transaction: TransactionEntity,
            onSave: (TransactionEntity) -> Unit,
            onDelete: ((TransactionEntity) -> Unit)? = null,
            customExpenseCategories: List<com.budgetapp.ocr.data.local.entity.CategoryEntity> = emptyList(),
            customIncomeCategories: List<com.budgetapp.ocr.data.local.entity.CategoryEntity> = emptyList()
        ): EditTransactionDialog {
            return EditTransactionDialog().apply {
                this.transaction = transaction
                this.onSaveCallback = onSave
                this.onDeleteCallback = onDelete
                this.customExpenseCategories = customExpenseCategories
                this.customIncomeCategories = customIncomeCategories
            }
        }
    }
}
