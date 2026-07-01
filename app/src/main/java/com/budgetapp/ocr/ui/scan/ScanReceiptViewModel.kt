package com.budgetapp.ocr.ui.scan

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.ocr.ExpenseCategory
import com.budgetapp.ocr.ReceiptData
import com.budgetapp.ocr.ReceiptTextRecognizer
import com.budgetapp.ocr.data.local.entity.TransactionEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.repository.AccountRepository
import com.budgetapp.ocr.data.repository.MerchantRepository
import com.budgetapp.ocr.data.repository.TransactionRepository
import com.budgetapp.ocr.service.DuplicateCheckResult
import com.budgetapp.ocr.service.DuplicateDetector
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

/**
 * ViewModel for the Scan Receipt screen
 */
@HiltViewModel
class ScanReceiptViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val merchantRepository: MerchantRepository,
    private val receiptTextRecognizer: ReceiptTextRecognizer,
    private val duplicateDetector: DuplicateDetector
) : ViewModel() {

    private val _state = MutableStateFlow(ScanReceiptState())
    val state: StateFlow<ScanReceiptState> = _state.asStateFlow()

    var currentBitmap: Bitmap? = null
        private set
    private var currentDuplicateCheck: DuplicateCheckResult? = null

    fun processReceiptImage(bitmap: Bitmap) {
        val maxWidth = 1080
        val processedBitmap = if (bitmap.width > maxWidth) {
            val scale = maxWidth.toFloat() / bitmap.width
            val scaled = Bitmap.createScaledBitmap(
                bitmap, maxWidth, (bitmap.height * scale).toInt(), true
            )
            bitmap.recycle()
            scaled
        } else bitmap

        currentBitmap = processedBitmap
        _state.update { it.copy(isProcessing = true, error = null) }

        viewModelScope.launch {
            try {
                var receiptData = withContext(Dispatchers.Default) {
                    receiptTextRecognizer.recognizeReceiptData(processedBitmap)
                }

                // Merchant lookup by NIP — but prefer known brand names from OCR
                val nip = receiptData.nip
                val ocrStoreName = receiptData.storeName
                if (nip != null) {
                    try {
                        val merchant = merchantRepository.findByNip(nip)
                        if (merchant != null) {
                            val categoryOverride = merchant.category?.let { catName ->
                                try { ExpenseCategory.valueOf(catName) } catch (_: Exception) { null }
                            }
                            // Prefer OCR brand name if it came from known stores keyword list
                            // (e.g. "Biedronka" is better than "JERONIMO MARTINS POLSKA SA" or garbled OCR)
                            val isGenericName = ocrStoreName == "Unknown" || ocrStoreName.length <= 2
                            val bestName = when {
                                receiptData.isKnownBrand -> ocrStoreName           // keyword match: always clean
                                merchant.source == "api" && !isGenericName -> ocrStoreName  // API: prefer OCR brand
                                else -> merchant.name                               // user-saved or generic: use DB
                            }
                            receiptData = receiptData.copy(
                                storeName = bestName,
                                category = categoryOverride ?: receiptData.category
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("ScanReceiptVM", "Merchant lookup failed: ${e.message}")
                    }
                }

                // Check for duplicates
                val duplicateCheck = duplicateDetector.checkForDuplicates(
                    bitmap = processedBitmap,
                    merchantName = receiptData.storeName,
                    amount = receiptData.totalAmount ?: 0.0
                )
                currentDuplicateCheck = duplicateCheck

                if (duplicateCheck.isDuplicate) {
                    // Show duplicate warning
                    _state.update {
                        it.copy(
                            receiptData = receiptData,
                            isProcessing = false,
                            duplicateWarning = duplicateCheck.reason,
                            duplicateConfidence = duplicateCheck.confidence
                        )
                    }
                } else {
                    // No duplicate - show dialog
                    _state.update {
                        it.copy(
                            receiptData = receiptData,
                            isProcessing = false,
                            showEditDialog = true
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ScanReceiptVM", "Error processing receipt", e)
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun ignoreDuplicateAndContinue() {
        _state.update {
            it.copy(
                duplicateWarning = null,
                duplicateConfidence = 0,
                showEditDialog = true
            )
        }
    }

    fun cancelDuplicateScan() {
        currentBitmap?.recycle()
        currentBitmap = null
        currentDuplicateCheck = null
        _state.update {
            it.copy(
                duplicateWarning = null,
                duplicateConfidence = 0,
                receiptData = null
            )
        }
    }

    fun saveTransaction(
        amount: Double,
        merchantName: String,
        category: String,
        date: String?,
        notes: String,
        rawOcrText: String,
        accountId: Long? = null,
        nip: String? = null
    ) {
        viewModelScope.launch {
            try {
                val resolvedAccountId = accountId ?: accountRepository.getDefaultAccountId()
                val imagePath = ReceiptImageSaver.save(context, currentBitmap)
                val transaction = TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amount = amount,
                    currency = "PLN",
                    category = category,
                    merchantName = merchantName,
                    date = date ?: getCurrentDate(),
                    notes = notes,
                    rawOcrText = rawOcrText,
                    imageHash = currentDuplicateCheck?.imageHash,
                    receiptImagePath = imagePath,
                    accountId = resolvedAccountId
                )

                transactionRepository.insert(transaction)

                // Learn merchant from user edit
                if (!nip.isNullOrBlank()) {
                    try {
                        merchantRepository.saveFromUserEdit(nip, merchantName, category)
                    } catch (e: Exception) {
                        android.util.Log.w("ScanReceiptVM", "Merchant save failed: ${e.message}")
                    }
                }

                // Cleanup
                currentBitmap?.recycle()
                currentBitmap = null
                currentDuplicateCheck = null

                _state.update {
                    it.copy(
                        showEditDialog = false,
                        receiptData = null,
                        saveSuccess = true,
                        duplicateWarning = null,
                        duplicateConfidence = 0,
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
        currentBitmap?.recycle()
        currentBitmap = null
        currentDuplicateCheck = null
        _state.update {
            it.copy(
                showEditDialog = false,
                receiptData = null,
                duplicateWarning = null,
                duplicateConfidence = 0,
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

    suspend fun saveIncome(transaction: TransactionEntity) {
        transactionRepository.insert(transaction)
    }

    /**
     * Processes a receipt from a PDF file (e.g. from Biedronka, Lidl)
     */
    fun processPdfReceipt(receiptData: ReceiptData) {
        viewModelScope.launch {
            var data = receiptData
            // Merchant lookup by NIP for PDF receipts
            val pdfNip = data.nip
            val pdfStoreName = data.storeName
            if (pdfNip != null) {
                try {
                    val merchant = merchantRepository.findByNip(pdfNip)
                    if (merchant != null) {
                        val categoryOverride = merchant.category?.let { catName ->
                            try { ExpenseCategory.valueOf(catName) } catch (_: Exception) { null }
                        }
                        val isGenericName = pdfStoreName == "Unknown" || pdfStoreName.length <= 2
                        val bestName = when {
                            data.isKnownBrand -> pdfStoreName                   // keyword match: always clean
                            merchant.source == "api" && !isGenericName -> pdfStoreName  // API: prefer OCR brand
                            else -> merchant.name                               // user-saved or generic: use DB
                        }
                        data = data.copy(
                            storeName = bestName,
                            category = categoryOverride ?: data.category
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ScanReceiptVM", "PDF merchant lookup failed: ${e.message}")
                }
            }
            _state.update {
                it.copy(
                    receiptData = data,
                    isProcessing = false,
                    showEditDialog = true
                )
            }
        }
    }

    fun resetState() {
        currentBitmap?.recycle()
        currentBitmap = null
        currentDuplicateCheck = null
        _state.update {
            ScanReceiptState()
        }
    }

    private fun getCurrentDate(): String {
        return java.time.LocalDate.now().toString()
    }
}

/**
 * UI State for Scan Receipt
 */
data class ScanReceiptState(
    val receiptData: ReceiptData? = null,
    val isProcessing: Boolean = false,
    val showEditDialog: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val duplicateWarning: String? = null,
    val duplicateConfidence: Int = 0
)
