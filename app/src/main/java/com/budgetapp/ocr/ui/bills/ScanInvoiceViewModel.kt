package com.budgetapp.ocr.ui.bills

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.ocr.InvoiceData
import com.budgetapp.ocr.InvoiceParser
import com.budgetapp.ocr.ReceiptTextRecognizer
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.data.local.entity.BillEntity
import com.budgetapp.ocr.data.local.entity.BillRecurrence
import com.budgetapp.ocr.data.local.entity.BillStatus
import com.budgetapp.ocr.data.local.entity.toMonths
import com.budgetapp.ocr.data.repository.BillRepository
import com.budgetapp.ocr.util.ReceiptImageSaver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ScanInvoiceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billRepository: BillRepository,
    private val receiptTextRecognizer: ReceiptTextRecognizer
) : ViewModel() {

    private val _state = MutableStateFlow(ScanInvoiceState())
    val state: StateFlow<ScanInvoiceState> = _state.asStateFlow()

    var currentBitmap: Bitmap? = null
        private set

    var currentPdfBitmap: Bitmap? = null
        private set

    fun setPdfBitmap(bitmap: Bitmap?) {
        currentPdfBitmap = bitmap
    }

    private fun cleanupBitmaps() {
        currentBitmap?.recycle()
        currentBitmap = null
        currentPdfBitmap?.recycle()
        currentPdfBitmap = null
    }

    fun processInvoiceText(invoiceData: InvoiceData) {
        _state.update {
            it.copy(
                invoiceData = invoiceData,
                isProcessing = false,
                showEditDialog = true
            )
        }
    }

    fun processInvoiceImage(bitmap: Bitmap) {
        currentBitmap = bitmap
        _state.update { it.copy(isProcessing = true, error = null) }

        viewModelScope.launch {
            try {
                val ocrText = withContext(Dispatchers.Default) {
                    receiptTextRecognizer.recognizeTextFromBitmap(bitmap)
                }

                val parser = InvoiceParser(context)
                val invoiceData = parser.parseInvoiceFromText(ocrText ?: "") ?: InvoiceData(
                    vendorName = "",
                    invoiceNumber = "",
                    amount = null,
                    dueDate = null,
                    issueDate = null,
                    bankAccount = null,
                    nip = null,
                    category = CategoryKey.OTHER_BILL.name,
                    rawText = ocrText ?: ""
                )

                _state.update {
                    it.copy(
                        invoiceData = invoiceData,
                        isProcessing = false,
                        showEditDialog = true
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun saveBill(
        vendorName: String,
        invoiceNumber: String,
        amount: Double,
        dueDate: String,
        issueDate: String,
        category: String,
        recurrence: BillRecurrence,
        reminderEnabled: Boolean,
        reminderDaysBefore: Int,
        bankAccount: String?,
        notes: String,
        rawOcrText: String,
        accountId: Long = 1L
    ) {
        viewModelScope.launch {
            try {
                val imagePath = ReceiptImageSaver.save(context, currentBitmap ?: currentPdfBitmap)
                val bill = BillEntity(
                    vendorName = vendorName,
                    invoiceNumber = invoiceNumber,
                    amount = amount,
                    category = category,
                    issueDate = issueDate,
                    dueDate = dueDate,
                    status = BillStatus.UNPAID,
                    bankAccount = bankAccount,
                    recurrence = recurrence,
                    notes = notes,
                    rawOcrText = rawOcrText,
                    receiptImagePath = imagePath,
                    reminderEnabled = reminderEnabled,
                    reminderDaysBefore = reminderDaysBefore,
                    accountId = accountId
                )

                billRepository.insert(bill)

                cleanupBitmaps()

                _state.update {
                    it.copy(
                        showEditDialog = false,
                        invoiceData = null,
                        saveSuccess = true,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Error: ${e.message}")
                }
            }
        }
    }

    fun saveBillAsPaid(
        vendorName: String,
        invoiceNumber: String,
        amount: Double,
        dueDate: String,
        issueDate: String,
        category: String,
        paymentDate: String,
        bankAccount: String?,
        notes: String,
        rawOcrText: String,
        accountId: Long = 1L
    ) {
        viewModelScope.launch {
            try {
                val imagePath = ReceiptImageSaver.save(context, currentBitmap ?: currentPdfBitmap)
                val bill = BillEntity(
                    vendorName = vendorName,
                    invoiceNumber = invoiceNumber,
                    amount = amount,
                    category = category,
                    issueDate = issueDate,
                    dueDate = dueDate,
                    status = BillStatus.PAID,
                    paymentDate = paymentDate,
                    bankAccount = bankAccount,
                    recurrence = BillRecurrence.NONE,
                    notes = notes,
                    rawOcrText = rawOcrText,
                    receiptImagePath = imagePath,
                    reminderEnabled = false,
                    reminderDaysBefore = 0,
                    accountId = accountId
                )

                billRepository.insert(bill)

                cleanupBitmaps()

                _state.update {
                    it.copy(
                        showEditDialog = false,
                        invoiceData = null,
                        saveSuccess = true,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Error: ${e.message}")
                }
            }
        }
    }

    fun saveForecastBills(forecasts: List<com.budgetapp.ocr.ForecastEntry>, vendorName: String, category: String) {
        viewModelScope.launch {
            try {
                val bills = forecasts.map { forecast ->
                    BillEntity(
                        vendorName = vendorName,
                        invoiceNumber = "",
                        amount = forecast.amount,
                        category = category,
                        issueDate = java.time.LocalDate.now().toString(),
                        dueDate = forecast.dueDate,
                        status = BillStatus.UNPAID,
                        recurrence = BillRecurrence.NONE,
                        notes = forecast.description,
                        reminderEnabled = true,
                        reminderDaysBefore = 3
                    )
                }
                billRepository.insertAll(bills)
            } catch (e: Exception) {
                android.util.Log.e("ScanInvoiceVM", "Error saving forecasts", e)
            }
        }
    }

    fun saveRecurringBills(
        vendorName: String,
        invoiceNumber: String,
        amount: Double,
        issueDate: String,
        category: String,
        frequency: BillRecurrence,
        durationMonths: Int,
        paymentDay: Int,
        startMonth: java.time.LocalDate,
        reminderEnabled: Boolean,
        reminderDaysBefore: Int,
        bankAccount: String?,
        notes: String,
        rawOcrText: String = "",
        accountId: Long = 1L
    ) {
        viewModelScope.launch {
            try {
                val imagePath = ReceiptImageSaver.save(context, currentBitmap ?: currentPdfBitmap)
                val template = BillEntity(
                    vendorName = vendorName,
                    invoiceNumber = invoiceNumber,
                    amount = amount,
                    category = category,
                    issueDate = issueDate,
                    dueDate = "", // will be set per bill
                    recurrence = frequency,
                    bankAccount = bankAccount,
                    notes = notes,
                    rawOcrText = rawOcrText,
                    receiptImagePath = imagePath,
                    reminderEnabled = reminderEnabled,
                    reminderDaysBefore = reminderDaysBefore,
                    accountId = accountId
                )
                val frequencyMonths = frequency.toMonths()
                billRepository.generateAndInsertRecurringBills(
                    template, frequencyMonths, durationMonths, paymentDay, startMonth
                )

                cleanupBitmaps()

                _state.update {
                    it.copy(
                        showEditDialog = false,
                        invoiceData = null,
                        saveSuccess = true,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Error: ${e.message}")
                }
            }
        }
    }

    fun dismissDialog() {
        cleanupBitmaps()
        _state.update {
            it.copy(
                showEditDialog = false,
                invoiceData = null,
                error = null
            )
        }
    }

    fun clearSuccessMessage() {
        _state.update { it.copy(saveSuccess = false) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun resetState() {
        cleanupBitmaps()
        _state.update { ScanInvoiceState() }
    }
}

data class ScanInvoiceState(
    val invoiceData: InvoiceData? = null,
    val isProcessing: Boolean = false,
    val showEditDialog: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)
