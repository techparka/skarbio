package com.budgetapp.ocr.ui.bills

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.budgetapp.ocr.InvoiceData
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.AccountEntity
import com.budgetapp.ocr.data.local.entity.BillEntity
import com.budgetapp.ocr.data.local.entity.BillRecurrence
import com.budgetapp.ocr.data.local.entity.BillStatus
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.databinding.DialogEditBillBinding
import com.budgetapp.ocr.ui.shared.DirtyFormDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class EditBillDialog : DirtyFormDialogFragment() {

    private var _binding: DialogEditBillBinding? = null
    private val binding get() = _binding!!

    private var invoiceData: InvoiceData? = null
    private var invoiceBitmap: Bitmap? = null
    private var isPreviewVisible = false
    private var isPreviewExpanded = false
    private var existingBill: BillEntity? = null
    private var accounts: List<AccountEntity> = emptyList()
    private var defaultAccountId: Long = 1L

    private var onSaveCallback: ((
        vendorName: String, invoiceNumber: String, amount: Double,
        dueDate: String, issueDate: String, category: String,
        recurrence: BillRecurrence, reminderEnabled: Boolean,
        reminderDaysBefore: Int, bankAccount: String?, notes: String,
        accountId: Long
    ) -> Unit)? = null

    private var onSaveRecurringCallback: ((
        vendorName: String, invoiceNumber: String, amount: Double,
        issueDate: String, category: String,
        frequency: BillRecurrence, durationMonths: Int, paymentDay: Int,
        startMonth: LocalDate, reminderEnabled: Boolean, reminderDaysBefore: Int,
        bankAccount: String?, notes: String, accountId: Long
    ) -> Unit)? = null

    private var onMarkAsPaidCallback: ((BillEntity) -> Unit)? = null
    private var onMarkAsUnpaidCallback: ((BillEntity) -> Unit)? = null
    private var onDeleteCallback: ((BillEntity) -> Unit)? = null
    private var onDeleteSeriesCallback: ((String) -> Unit)? = null
    private var onDismissCallback: (() -> Unit)? = null

    // Dirty form detection
    private var originalVendorName: String = ""
    private var originalInvoiceNumber: String = ""
    private var originalAmount: Double = 0.0
    private var originalDueDate: String = ""
    private var originalIssueDate: String = ""
    private var originalCategory: Int = 0
    private var originalReminderEnabled: Boolean = false
    private var originalReminderDays: Int = 0
    private var originalBankAccount: String = ""
    private var originalNotes: String = ""
    private var originalAccountId: Long = 1L
    private var originalRecurringChecked: Boolean = false

    private val billCategoryKeys = CategoryKey.billCategories()
    private var customBillCategories: List<com.budgetapp.ocr.data.local.entity.CategoryEntity> = emptyList()

    // Recurring section data
    private val frequencyValues = listOf(
        BillRecurrence.MONTHLY,
        BillRecurrence.BIMONTHLY,
        BillRecurrence.QUARTERLY,
        BillRecurrence.SEMIANNUAL,
        BillRecurrence.ANNUAL
    )

    private var selectedStartMonth: YearMonth = YearMonth.now()
    private var selectedPaymentDay: Int = 0

    private fun getFrequencyLabels(): List<String> = listOf(
        getString(R.string.recurrence_monthly),
        getString(R.string.recurrence_bimonthly),
        getString(R.string.recurrence_quarterly),
        getString(R.string.recurrence_semiannual),
        getString(R.string.recurrence_annual)
    )

    // ── DirtyFormDialogFragment implementation ─────────────────────────────

    override fun isDirty(): Boolean {
        if (_binding == null) return false

        try {
            val currentVendorName = binding.etVendorName.text.toString().trim()
            val currentInvoiceNumber = binding.etInvoiceNumber.text.toString().trim()
            val currentAmount = binding.etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
            val currentDueDate = binding.etDueDate.text.toString().trim()
            val currentIssueDate = binding.etIssueDate.text.toString().trim()
            val currentCategory = binding.spinnerCategory.selectedItemPosition
            val currentReminderEnabled = binding.switchReminder.isChecked
            val currentReminderDays = binding.etReminderDays.text.toString().toIntOrNull() ?: 0
            val currentBankAccount = binding.etBankAccount.text.toString().trim()
            val currentNotes = binding.etNotes.text.toString().trim()
            val currentRecurringChecked = binding.switchRecurring.isChecked

            val selectedLabel = binding.spinnerAccount.text.toString()
            val selectedIdx = accounts.indexOfFirst { "${it.icon} ${it.name}" == selectedLabel }
            val currentAccountId = if (selectedIdx >= 0) accounts[selectedIdx].id else defaultAccountId

            return currentVendorName != originalVendorName
                    || currentInvoiceNumber != originalInvoiceNumber
                    || currentAmount != originalAmount
                    || currentDueDate != originalDueDate
                    || currentIssueDate != originalIssueDate
                    || currentCategory != originalCategory
                    || currentReminderEnabled != originalReminderEnabled
                    || currentReminderDays != originalReminderDays
                    || currentBankAccount != originalBankAccount
                    || currentNotes != originalNotes
                    || currentAccountId != originalAccountId
                    || currentRecurringChecked != originalRecurringChecked
        } catch (e: Exception) {
            return false
        }
    }

    override fun onSaveClicked() {
        saveBill()
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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditBillBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSpinners()
        setupAccountSpinner()
        setupDatePickers()
        setupReminderToggle()
        setupRecurringToggle()
        setupFrequencySpinner()
        setupDurationChips()
        setupStartMonthPicker()
        setupPaymentDayPicker()
        setupPreviewToggle()
        populateFields()
        setupClickListeners()
    }

    /** Combined category names: system CategoryKey names + custom entity names */
    private val allBillCategoryNames: List<String>
        get() = billCategoryKeys.map { it.name } + customBillCategories.map { it.name }

    private fun setupSpinners() {
        val systemLabels = billCategoryKeys.map { getString(it.stringResId) }
        val customLabels = customBillCategories.map { it.name }
        val categoryLabels = systemLabels + customLabels
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item,
            categoryLabels
        )
        categoryAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.spinnerCategory.adapter = categoryAdapter
    }

    private fun setupAccountSpinner() {
        if (accounts.isEmpty()) return
        val labels = accounts.map { "${it.icon} ${it.name}" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        binding.spinnerAccount.setAdapter(adapter)
        val preselectId = existingBill?.accountId ?: defaultAccountId
        val idx = accounts.indexOfFirst { it.id == preselectId }.coerceAtLeast(0)
        binding.spinnerAccount.setText(labels[idx], false)
    }

    private fun setupDatePickers() {
        binding.etDueDate.setOnClickListener { showDatePicker(binding.etDueDate) }
        binding.etIssueDate.setOnClickListener { showDatePicker(binding.etIssueDate) }
    }

    private fun showDatePicker(editText: com.google.android.material.textfield.TextInputEditText) {
        val dateStr = editText.text.toString().trim()
        val date = try {
            if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
        } catch (e: Exception) {
            LocalDate.now()
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selected = LocalDate.of(year, month + 1, dayOfMonth)
                editText.setText(selected.format(DateTimeFormatter.ISO_LOCAL_DATE))
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth
        ).show()
    }

    private fun setupReminderToggle() {
        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            binding.tilReminderDays.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    // ── Recurring section ────────────────────────────────────────────────────

    private fun setupRecurringToggle() {
        binding.switchRecurring.setOnCheckedChangeListener { _, isChecked ->
            binding.recurringSection.visibility = if (isChecked) View.VISIBLE else View.GONE
            // Hide due date when recurring is on (due dates are auto-generated)
            val dueDateParent = binding.etDueDate.parent?.parent as? View
            dueDateParent?.visibility = if (isChecked) View.GONE else View.VISIBLE
            if (isChecked) {
                // Default payment day from due date field
                if (binding.etPaymentDay.text.isNullOrEmpty()) {
                    val dueDateStr = binding.etDueDate.text.toString().trim()
                    val defaultDay = try {
                        if (dueDateStr.isNotEmpty()) LocalDate.parse(dueDateStr).dayOfMonth else 10
                    } catch (e: Exception) { 10 }
                    selectedPaymentDay = defaultDay.coerceAtMost(28)
                    binding.etPaymentDay.setText(selectedPaymentDay.toString())
                }
                updateRecurringPreview()
            }
        }
    }

    private fun setupFrequencySpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item,
            getFrequencyLabels()
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.spinnerFrequency.adapter = adapter

        binding.spinnerFrequency.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateRecurringPreview()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupDurationChips() {
        binding.chipGroupDuration.setOnCheckedStateChangeListener { _, checkedIds ->
            val isCustom = checkedIds.contains(R.id.chipCustom)
            binding.tilCustomDuration.visibility = if (isCustom) View.VISIBLE else View.GONE
            updateRecurringPreview()
        }

        binding.etCustomDuration.addTextChangedListener { updateRecurringPreview() }
    }

    private fun setupStartMonthPicker() {
        selectedStartMonth = YearMonth.now()
        updateStartMonthDisplay()

        binding.etStartMonth.setOnClickListener {
            val current = selectedStartMonth
            DatePickerDialog(
                requireContext(),
                { _, year, month, _ ->
                    selectedStartMonth = YearMonth.of(year, month + 1)
                    updateStartMonthDisplay()
                    updateRecurringPreview()
                },
                current.year,
                current.monthValue - 1,
                1
            ).show()
        }
    }

    private fun updateStartMonthDisplay() {
        val name = selectedStartMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
        binding.etStartMonth.setText("${name.replaceFirstChar { it.uppercase() }} ${selectedStartMonth.year}")
    }

    private fun setupPaymentDayPicker() {
        binding.etPaymentDay.setOnClickListener {
            val now = LocalDate.now()
            val initDay = selectedPaymentDay.takeIf { it in 1..28 } ?: now.dayOfMonth.coerceAtMost(28)
            DatePickerDialog(
                requireContext(),
                { _, _, _, dayOfMonth ->
                    selectedPaymentDay = dayOfMonth.coerceAtMost(28)
                    binding.etPaymentDay.setText(selectedPaymentDay.toString())
                    updateRecurringPreview()
                },
                now.year,
                now.monthValue - 1,
                initDay
            ).show()
        }
    }

    private fun getSelectedDurationMonths(): Int {
        val checkedId = binding.chipGroupDuration.checkedChipId
        return when (checkedId) {
            R.id.chip6 -> 6
            R.id.chip12 -> 12
            R.id.chip24 -> 24
            R.id.chipCustom -> binding.etCustomDuration.text.toString().toIntOrNull() ?: 0
            else -> 12
        }
    }

    private fun getSelectedFrequency(): BillRecurrence {
        val pos = binding.spinnerFrequency.selectedItemPosition
        return if (pos in frequencyValues.indices) frequencyValues[pos] else BillRecurrence.MONTHLY
    }

    private fun getSelectedStartMonth(): YearMonth = selectedStartMonth

    private fun updateRecurringPreview() {
        if (_binding == null) return
        val frequency = getSelectedFrequency()
        val freqMonths = when (frequency) {
            BillRecurrence.MONTHLY -> 1
            BillRecurrence.BIMONTHLY -> 2
            BillRecurrence.QUARTERLY -> 3
            BillRecurrence.SEMIANNUAL -> 6
            BillRecurrence.ANNUAL -> 12
            BillRecurrence.NONE -> 0
        }
        val durationMonths = getSelectedDurationMonths()
        if (freqMonths <= 0 || durationMonths <= 0) {
            binding.tvRecurringPreview.text = ""
            return
        }

        val startYm = getSelectedStartMonth()
        val count = (durationMonths / freqMonths) + (if (durationMonths % freqMonths > 0) 1 else 0)
        val endYm = startYm.plusMonths((count.coerceAtLeast(1) - 1).toLong() * freqMonths)

        val locale = Locale.getDefault()
        val startLabel = startYm.month.getDisplayName(TextStyle.SHORT, locale)
            .replaceFirstChar { it.lowercase() } + ". ${startYm.year}"
        val endLabel = endYm.month.getDisplayName(TextStyle.SHORT, locale)
            .replaceFirstChar { it.lowercase() } + ". ${endYm.year}"

        binding.tvRecurringPreview.text = getString(
            R.string.recurring_preview,
            count,
            startLabel,
            endLabel
        )
    }

    // ── Preview toggle ──────────────────────────────────────────────────────

    private fun setupPreviewToggle() {
        if (invoiceBitmap != null) {
            binding.btnTogglePreview.visibility = View.VISIBLE
            binding.ivInvoicePreview.setImageBitmap(invoiceBitmap)

            binding.btnTogglePreview.setOnClickListener {
                isPreviewVisible = !isPreviewVisible
                if (isPreviewVisible) {
                    binding.cardPreview.visibility = View.VISIBLE
                    binding.btnExpandPreview.visibility = View.VISIBLE
                    binding.btnTogglePreview.text = getString(R.string.hide_invoice)
                } else {
                    binding.cardPreview.visibility = View.GONE
                    binding.btnExpandPreview.visibility = View.GONE
                    binding.btnTogglePreview.text = getString(R.string.show_invoice)
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
        } else {
            // Load receipt image from file for existing bills
            val imagePath = existingBill?.receiptImagePath
            if (!imagePath.isNullOrBlank() && java.io.File(imagePath).exists()) {
                binding.btnTogglePreview.visibility = View.VISIBLE
                binding.btnTogglePreview.text = getString(R.string.view_receipt)
                binding.btnTogglePreview.setOnClickListener {
                    isPreviewVisible = !isPreviewVisible
                    if (isPreviewVisible) {
                        binding.cardPreview.visibility = View.VISIBLE
                        binding.btnTogglePreview.text = getString(R.string.hide_invoice)
                        lifecycleScope.launch {
                            val bitmap = withContext(Dispatchers.IO) {
                                BitmapFactory.decodeFile(imagePath)
                            }
                            if (bitmap != null && _binding != null) {
                                binding.ivInvoicePreview.setImageBitmap(bitmap)
                            }
                        }
                    } else {
                        binding.cardPreview.visibility = View.GONE
                        binding.btnTogglePreview.text = getString(R.string.view_receipt)
                        binding.ivInvoicePreview.setImageBitmap(null)
                    }
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
        params.height = (280 * resources.displayMetrics.density).toInt()
        binding.cardPreview.layoutParams = params
    }

    private fun populateFields() {
        invoiceData?.let { data ->
            binding.etVendorName.setText(data.vendorName)
            binding.etInvoiceNumber.setText(data.invoiceNumber)
            binding.etAmount.setText(data.amount?.toString() ?: "")
            binding.etDueDate.setText(data.dueDate ?: "")
            binding.etIssueDate.setText(data.issueDate ?: "")
            binding.etBankAccount.setText(data.bankAccount ?: "")

            val catIndex = allBillCategoryNames.indexOfFirst { it == data.category }
            if (catIndex >= 0) binding.spinnerCategory.setSelection(catIndex)
        }

        existingBill?.let { bill ->
            binding.tvTitle.text = getString(R.string.edit_bill)
            // Hide recurring switch when editing existing bill (to avoid creating duplicate series)
            binding.switchRecurring.visibility = View.GONE
            binding.etVendorName.setText(bill.vendorName)
            binding.etInvoiceNumber.setText(bill.invoiceNumber)
            binding.etAmount.setText(bill.amount.toString())
            binding.etDueDate.setText(bill.dueDate)
            binding.etIssueDate.setText(bill.issueDate)
            binding.etBankAccount.setText(bill.bankAccount ?: "")
            binding.etNotes.setText(bill.notes)
            binding.switchReminder.isChecked = bill.reminderEnabled
            binding.etReminderDays.setText(bill.reminderDaysBefore.toString())

            val catIndex = allBillCategoryNames.indexOfFirst { it == bill.category }
            if (catIndex >= 0) binding.spinnerCategory.setSelection(catIndex)

            if (bill.status == BillStatus.UNPAID || bill.status == BillStatus.OVERDUE) {
                binding.btnMarkAsPaid.visibility = View.VISIBLE
                binding.btnMarkAsPaid.text = getString(R.string.mark_as_paid)
                binding.btnMarkAsPaid.setOnClickListener {
                    onMarkAsPaidCallback?.invoke(bill)
                    forceDismiss()
                }
            } else if (bill.status == BillStatus.PAID) {
                binding.btnMarkAsPaid.visibility = View.VISIBLE
                binding.btnMarkAsPaid.text = getString(R.string.mark_as_unpaid)
                binding.btnMarkAsPaid.setOnClickListener {
                    onMarkAsUnpaidCallback?.invoke(bill)
                    forceDismiss()
                }
            }

            binding.btnDelete.visibility = View.VISIBLE
            binding.btnDelete.setOnClickListener {
                val groupId = bill.recurringGroupId
                if (groupId != null && onDeleteSeriesCallback != null) {
                    showDeleteRecurringDialog(bill, groupId)
                } else {
                    onDeleteCallback?.invoke(bill)
                    forceDismiss()
                }
            }
        }

        // Save original values for dirty form detection
        originalVendorName = binding.etVendorName.text.toString().trim()
        originalInvoiceNumber = binding.etInvoiceNumber.text.toString().trim()
        originalAmount = binding.etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
        originalDueDate = binding.etDueDate.text.toString().trim()
        originalIssueDate = binding.etIssueDate.text.toString().trim()
        originalCategory = binding.spinnerCategory.selectedItemPosition
        originalReminderEnabled = binding.switchReminder.isChecked
        originalReminderDays = binding.etReminderDays.text.toString().toIntOrNull() ?: 0
        originalBankAccount = binding.etBankAccount.text.toString().trim()
        originalNotes = binding.etNotes.text.toString().trim()
        originalRecurringChecked = binding.switchRecurring.isChecked

        val selectedLabel = binding.spinnerAccount.text.toString()
        val selectedIdx = accounts.indexOfFirst { "${it.icon} ${it.name}" == selectedLabel }
        originalAccountId = if (selectedIdx >= 0) accounts[selectedIdx].id else defaultAccountId
    }

    private fun showDeleteRecurringDialog(bill: BillEntity, groupId: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_recurring_title))
            .setItems(arrayOf(
                getString(R.string.delete_only_this),
                getString(R.string.delete_entire_series)
            )) { _, which ->
                when (which) {
                    0 -> {
                        onDeleteCallback?.invoke(bill)
                        forceDismiss()
                    }
                    1 -> {
                        onDeleteSeriesCallback?.invoke(groupId)
                        forceDismiss()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener { handleDismissRequest() }
        binding.btnSave.setOnClickListener { saveBill() }
    }

    private fun saveBill() {
        val amountStr = binding.etAmount.text.toString().trim()
        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0 || amount > 999_999.99) {
            Toast.makeText(requireContext(), getString(R.string.enter_valid_amount), Toast.LENGTH_SHORT).show()
            return
        }

        val vendorName = binding.etVendorName.text.toString().trim().ifEmpty { getString(R.string.unknown_vendor) }
        val invoiceNumber = binding.etInvoiceNumber.text.toString().trim()
        val issueDate = binding.etIssueDate.text.toString().trim().ifEmpty {
            LocalDate.now().toString()
        }
        val category = allBillCategoryNames[binding.spinnerCategory.selectedItemPosition]
        val reminderEnabled = binding.switchReminder.isChecked
        val reminderDays = (binding.etReminderDays.text.toString().toIntOrNull() ?: 3).coerceAtLeast(0)
        val bankAccount = binding.etBankAccount.text.toString().trim().ifEmpty { null }
        val notes = binding.etNotes.text.toString().trim()

        val selectedLabel = binding.spinnerAccount.text.toString()
        val selectedIdx = accounts.indexOfFirst { "${it.icon} ${it.name}" == selectedLabel }
        val accountId = if (selectedIdx >= 0) accounts[selectedIdx].id else defaultAccountId

        if (binding.switchRecurring.isChecked) {
            // Validate payment day
            val paymentDay = selectedPaymentDay
            if (paymentDay < 1 || paymentDay > 28) {
                Toast.makeText(requireContext(), getString(R.string.payment_day_error), Toast.LENGTH_SHORT).show()
                return
            }

            val frequency = getSelectedFrequency()
            val durationMonths = getSelectedDurationMonths()
            if (durationMonths <= 0) return

            val startYm = getSelectedStartMonth()
            val startDate = startYm.atDay(1)

            onSaveRecurringCallback?.invoke(
                vendorName, invoiceNumber, amount, issueDate, category,
                frequency, durationMonths, paymentDay, startDate,
                reminderEnabled, reminderDays, bankAccount, notes, accountId
            )
            forceDismiss()
        } else {
            val dueDate = binding.etDueDate.text.toString().trim().ifEmpty {
                LocalDate.now().plusDays(14).toString()
            }
            val recurrence = BillRecurrence.NONE

            onSaveCallback?.invoke(
                vendorName, invoiceNumber, amount, dueDate, issueDate,
                category, recurrence, reminderEnabled, reminderDays, bankAccount, notes, accountId
            )
            forceDismiss()
        }
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
            invoiceData: InvoiceData? = null,
            existingBill: BillEntity? = null,
            accounts: List<AccountEntity> = emptyList(),
            defaultAccountId: Long = 1L,
            customBillCategories: List<com.budgetapp.ocr.data.local.entity.CategoryEntity> = emptyList(),
            bitmap: Bitmap? = null,
            onSave: ((String, String, Double, String, String, String, BillRecurrence, Boolean, Int, String?, String, Long) -> Unit)? = null,
            onSaveRecurring: ((
                vendorName: String, invoiceNumber: String, amount: Double,
                issueDate: String, category: String,
                frequency: BillRecurrence, durationMonths: Int, paymentDay: Int,
                startMonth: LocalDate, reminderEnabled: Boolean, reminderDaysBefore: Int,
                bankAccount: String?, notes: String, accountId: Long
            ) -> Unit)? = null,
            onMarkAsPaid: ((BillEntity) -> Unit)? = null,
            onMarkAsUnpaid: ((BillEntity) -> Unit)? = null,
            onDelete: ((BillEntity) -> Unit)? = null,
            onDeleteSeries: ((String) -> Unit)? = null,
            onDismiss: (() -> Unit)? = null
        ): EditBillDialog {
            return EditBillDialog().apply {
                this.invoiceData = invoiceData
                this.invoiceBitmap = bitmap
                this.existingBill = existingBill
                this.accounts = accounts
                this.defaultAccountId = defaultAccountId
                this.customBillCategories = customBillCategories
                this.onSaveCallback = onSave
                this.onSaveRecurringCallback = onSaveRecurring
                this.onMarkAsPaidCallback = onMarkAsPaid
                this.onMarkAsUnpaidCallback = onMarkAsUnpaid
                this.onDeleteCallback = onDelete
                this.onDeleteSeriesCallback = onDeleteSeries
                this.onDismissCallback = onDismiss
            }
        }
    }
}
