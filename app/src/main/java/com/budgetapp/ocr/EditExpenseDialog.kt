package com.budgetapp.ocr

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import com.budgetapp.ocr.data.local.entity.AccountEntity
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.databinding.DialogEditExpenseBinding
import com.budgetapp.ocr.ui.shared.DirtyFormDialogFragment
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Dialog for editing an expense after receipt scanning.
 * Detects unsaved changes and asks user before closing.
 */
class EditExpenseDialog : DirtyFormDialogFragment() {

    private var _binding: DialogEditExpenseBinding? = null
    private val binding get() = _binding!!

    private var receiptData: ReceiptData? = null
    private var receiptBitmap: Bitmap? = null
    private var receiptNip: String? = null
    private var isPreviewVisible = false
    private var isPreviewExpanded = false
    private var onSaveCallback: ((Double, String, String, String?, String, Long, String?) -> Unit)? = null
    private var onDismissCallback: (() -> Unit)? = null
    private var onScanReceiptCallback: (() -> Unit)? = null
    private var accounts: List<AccountEntity> = emptyList()
    private var defaultAccountId: Long = 1L

    private val expenseCategoryKeys = CategoryKey.expenseCategories()
    private var customExpenseCategories: List<com.budgetapp.ocr.data.local.entity.CategoryEntity> = emptyList()

    private val allExpenseCategoryNames: List<String>
        get() = expenseCategoryKeys.map { it.name } + customExpenseCategories.map { it.name }

    // Dirty form detection — przechowuj oryginalne wartości
    private var originalAmount: Double = 0.0
    private var originalStoreName: String = ""
    private var originalCategory: Int = 0
    private var originalDate: String = ""
    private var originalNotes: String = ""
    private var originalAccountId: Long = 1L

    // ── DirtyFormDialogFragment implementation ─────────────────────────────

    override fun isDirty(): Boolean {
        // Jeśli binding nie jest inicjalizowany, nie ma co porównywać
        if (_binding == null) return false

        try {
            val currentAmount = binding.etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
            val currentStoreName = binding.etStoreName.text.toString().trim()
            val currentCategory = binding.spinnerCategory.selectedItemPosition
            val currentDate = binding.etDate.text.toString().trim()
            val currentNotes = binding.etNotes.text.toString().trim()

            val selectedLabel = binding.spinnerAccount.text.toString()
            val selectedIdx = accounts.indexOfFirst { "${it.icon} ${it.name}" == selectedLabel }
            val currentAccountId = if (selectedIdx >= 0) accounts[selectedIdx].id else defaultAccountId

            return currentAmount != originalAmount
                    || currentStoreName != originalStoreName
                    || currentCategory != originalCategory
                    || currentDate != originalDate
                    || currentNotes != originalNotes
                    || currentAccountId != originalAccountId
        } catch (e: Exception) {
            // Jeśli coś pójdzie nie tak przy dostępie do widgetów, assume not dirty
            return false
        }
    }

    override fun onSaveClicked() {
        saveExpense()
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCategorySpinner()
        setupAccountSpinner()
        setupDatePicker()
        setupPreviewToggle()
        populateFields()
        setupClickListeners()
    }

    private fun setupCategorySpinner() {
        val systemLabels = expenseCategoryKeys.map { key ->
            "${key.emoji} ${getString(key.stringResId)}"
        }
        val customLabels = customExpenseCategories.map { "${it.icon} ${it.name}" }
        val labels = systemLabels + customLabels
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item_category,
            labels
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_category)
        binding.spinnerCategory.adapter = adapter
    }

    private fun setupAccountSpinner() {
        if (accounts.isEmpty()) return
        val labels = accounts.map { "${it.icon} ${it.name}" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        binding.spinnerAccount.setAdapter(adapter)
        val defaultIdx = accounts.indexOfFirst { it.id == defaultAccountId }.coerceAtLeast(0)
        binding.spinnerAccount.setText(labels[defaultIdx], false)
    }

    private fun setupDatePicker() {
        binding.etDate.setOnClickListener {
            val dateStr = binding.etDate.text.toString().trim()
            val date = try {
                if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
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

    private fun setupPreviewToggle() {
        if (receiptBitmap != null) {
            binding.btnTogglePreview.visibility = View.VISIBLE
            binding.ivReceiptPreview.setImageBitmap(receiptBitmap)

            binding.btnTogglePreview.setOnClickListener {
                isPreviewVisible = !isPreviewVisible
                if (isPreviewVisible) {
                    binding.cardPreview.visibility = View.VISIBLE
                    binding.btnExpandPreview.visibility = View.VISIBLE
                    binding.btnTogglePreview.text = getString(R.string.hide_receipt)
                } else {
                    binding.cardPreview.visibility = View.GONE
                    binding.btnExpandPreview.visibility = View.GONE
                    binding.btnTogglePreview.text = getString(R.string.show_receipt)
                    if (isPreviewExpanded) {
                        isPreviewExpanded = false
                        collapsePreview()
                    }
                }
            }

            binding.btnExpandPreview.setOnClickListener {
                isPreviewExpanded = !isPreviewExpanded
                if (isPreviewExpanded) {
                    expandPreview()
                    binding.btnExpandPreview.text = getString(R.string.shrink_preview)
                } else {
                    collapsePreview()
                    binding.btnExpandPreview.text = getString(R.string.expand_preview)
                }
            }
        }
    }

    private fun expandPreview() {
        val params = binding.cardPreview.layoutParams
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        binding.cardPreview.layoutParams = params
    }

    private fun collapsePreview() {
        val params = binding.cardPreview.layoutParams
        params.height = (300 * resources.displayMetrics.density).toInt()
        binding.cardPreview.layoutParams = params
    }

    private fun populateFields() {
        receiptData?.let { data ->
            binding.etStoreName.setText(data.storeName)
            binding.etAmount.setText(data.totalAmount?.toString() ?: "")
            binding.etDate.setText(data.date ?: "")

            // Find category index in combined list
            val categoryKey = data.category.categoryKey
            val systemIndex = expenseCategoryKeys.indexOf(categoryKey)
            if (systemIndex >= 0) {
                binding.spinnerCategory.setSelection(systemIndex)
            } else {
                // Check custom categories
                val customIndex = customExpenseCategories.indexOfFirst { it.name == categoryKey.name }
                if (customIndex >= 0) {
                    binding.spinnerCategory.setSelection(expenseCategoryKeys.size + customIndex)
                }
            }
        }

        // Save original values for dirty form detection
        originalAmount = binding.etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
        originalStoreName = binding.etStoreName.text.toString().trim()
        originalCategory = binding.spinnerCategory.selectedItemPosition
        originalDate = binding.etDate.text.toString().trim()
        originalNotes = binding.etNotes.text.toString().trim()

        val selectedLabel = binding.spinnerAccount.text.toString()
        val selectedIdx = accounts.indexOfFirst { "${it.icon} ${it.name}" == selectedLabel }
        originalAccountId = if (selectedIdx >= 0) accounts[selectedIdx].id else defaultAccountId
    }

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener {
            handleDismissRequest()
        }

        binding.btnSave.setOnClickListener {
            saveExpense()
        }

        // Show "Scan instead" only when no bitmap (manual entry mode)
        if (receiptBitmap == null) {
            binding.btnScanInstead.visibility = View.VISIBLE
            binding.btnScanInstead.setOnClickListener {
                forceDismiss()
                onScanReceiptCallback?.invoke()
            }
        }
    }

    private fun saveExpense() {
        val amountStr = binding.etAmount.text.toString().trim()
        val amount = amountStr.toDoubleOrNull()

        if (amount == null || amount <= 0) {
            Toast.makeText(requireContext(), getString(R.string.enter_valid_amount), Toast.LENGTH_SHORT).show()
            return
        }

        val merchantName = binding.etStoreName.text.toString().trim().ifEmpty { getString(R.string.unknown_store) }
        val date = binding.etDate.text.toString().trim().ifEmpty { null }
        val notes = binding.etNotes.text.toString().trim()
        val categoryIndex = binding.spinnerCategory.selectedItemPosition
        val category = allExpenseCategoryNames[categoryIndex]

        val selectedLabel = binding.spinnerAccount.text.toString()
        val selectedIdx = accounts.indexOfFirst { "${it.icon} ${it.name}" == selectedLabel }
        val accountId = if (selectedIdx >= 0) accounts[selectedIdx].id else defaultAccountId

        onSaveCallback?.invoke(amount, merchantName, category, date, notes, accountId, receiptNip)
        forceDismiss()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            receiptData: ReceiptData,
            onSave: (amount: Double, merchantName: String, category: String, date: String?, notes: String, accountId: Long, nip: String?) -> Unit,
            onDismiss: (() -> Unit)? = null,
            onScanReceipt: (() -> Unit)? = null,
            bitmap: Bitmap? = null,
            accounts: List<AccountEntity> = emptyList(),
            defaultAccountId: Long = 1L,
            customExpenseCategories: List<com.budgetapp.ocr.data.local.entity.CategoryEntity> = emptyList()
        ): EditExpenseDialog {
            return EditExpenseDialog().apply {
                this.receiptData = receiptData
                this.receiptBitmap = bitmap
                this.receiptNip = receiptData.nip
                this.onSaveCallback = onSave
                this.onDismissCallback = onDismiss
                this.onScanReceiptCallback = onScanReceipt
                this.accounts = accounts
                this.defaultAccountId = defaultAccountId
                this.customExpenseCategories = customExpenseCategories
            }
        }
    }
}
