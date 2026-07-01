package com.budgetapp.ocr.ui.scan

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.budgetapp.ocr.EditExpenseDialog
import com.budgetapp.ocr.MLKitDocumentScanner
import com.budgetapp.ocr.PdfReceiptParser
import com.budgetapp.ocr.ReceiptData
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.repository.AccountRepository
import com.budgetapp.ocr.data.repository.CategoryRepository
import com.budgetapp.ocr.databinding.FragmentScanReceiptBinding
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ScanReceiptFragment : Fragment() {

    private var _binding: FragmentScanReceiptBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScanReceiptViewModel by viewModels()
    private var isDialogShowing = false
    private var isDuplicateWarningShowing = false

    @Inject lateinit var documentScanner: MLKitDocumentScanner
    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var categoryRepository: CategoryRepository

    private val documentScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.let { pages ->
                if (pages.isNotEmpty()) {
                    val imageUri = pages.first().imageUri
                    processScanResult(imageUri)
                }
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val bitmap = result.data?.extras?.getParcelable<Bitmap>("data")
            if (bitmap != null) {
                viewModel.processReceiptImage(bitmap)
            }
        }
    }

    private val pdfLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                processPdfFile(uri)
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val bitmap = uriToBitmap(uri)
                    viewModel.processReceiptImage(bitmap)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.receipt_scan_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanReceiptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isDialogShowing = false
        isDuplicateWarningShowing = false
        viewModel.resetState()
        setupClickListeners()
        observeState()

        // Auto-launch document scanner when opened from widget
        if (arguments?.getBoolean("auto_scan") == true) {
            arguments?.remove("auto_scan")
            launchDocumentScanner()
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnDocumentScan.setOnClickListener {
            launchDocumentScanner()
        }

        binding.btnCamera.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(intent)
        }

        binding.btnGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(intent)
        }

        binding.btnImportPdf.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }
            pdfLauncher.launch(intent)
        }

        binding.btnManualEntry.setOnClickListener {
            showEditDialog(null)
        }
    }

    private fun processPdfFile(uri: Uri) {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.tvProcessing.visibility = View.VISIBLE
        binding.tvProcessing.text = getString(R.string.analyzing_receipt)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val parser = PdfReceiptParser(requireContext())

                // Try native PDF text extraction first
                var receiptData = withContext(Dispatchers.IO) {
                    parser.parseReceipt(uri)
                }

                // If PDF has no text (scanned image), render to bitmap and use OCR
                if (receiptData == null) {
                    binding.tvProcessing.text = getString(R.string.analyzing_receipt)

                    val bitmap = withContext(Dispatchers.IO) {
                        parser.renderFirstPageToBitmap(uri)
                    }

                    if (bitmap != null) {
                        // Use OCR on the rendered bitmap
                        viewModel.processReceiptImage(bitmap)
                        binding.progressIndicator.visibility = View.GONE
                        binding.tvProcessing.visibility = View.GONE
                        return@launch
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.receipt_scan_error), Toast.LENGTH_LONG).show()
                    }
                }

                binding.progressIndicator.visibility = View.GONE
                binding.tvProcessing.visibility = View.GONE

                if (receiptData != null) {
                    viewModel.processPdfReceipt(receiptData)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.receipt_scan_error), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressIndicator.visibility = View.GONE
                binding.tvProcessing.visibility = View.GONE
                android.util.Log.e("ScanReceipt", "Error parsing PDF", e)
                Toast.makeText(requireContext(), getString(R.string.receipt_scan_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun launchDocumentScanner() {
        documentScanner.getScanner()
            .getStartScanIntent(requireActivity())
            .addOnSuccessListener { intentSender ->
                documentScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { _ ->
                Toast.makeText(
                    requireContext(),
                    getString(R.string.receipt_scan_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun processScanResult(imageUri: Uri) {
        try {
            val bitmap = uriToBitmap(imageUri)
            viewModel.processReceiptImage(bitmap)
        } catch (e: Exception) {
            android.util.Log.e("ScanReceipt", "Error loading bitmap", e)
            Toast.makeText(requireContext(), getString(R.string.receipt_scan_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->

                // Processing indicator
                binding.progressIndicator.visibility = if (state.isProcessing) View.VISIBLE else View.GONE
                binding.tvProcessing.visibility = if (state.isProcessing) View.VISIBLE else View.GONE

                // Duplicate warning
                if (state.duplicateWarning != null && !isDuplicateWarningShowing) {
                    isDuplicateWarningShowing = true
                    showDuplicateWarning(state.duplicateWarning, state.duplicateConfidence)
                }

                // Show dialog
                if (state.showEditDialog && state.receiptData != null && !isDialogShowing) {
                    showEditDialog(state.receiptData)
                }

                // Success message
                if (state.saveSuccess) {
                    Toast.makeText(requireContext(), getString(R.string.receipt_saved), Toast.LENGTH_SHORT).show()
                    viewModel.clearSuccessMessage()
                    findNavController().navigateUp()
                }

                // Error
                state.error?.let { error ->
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun showDuplicateWarning(warning: String, confidence: Int) {
        val dialog = DuplicateWarningDialog.newInstance(
            warning = warning,
            confidence = confidence,
            onAddAnyway = {
                isDuplicateWarningShowing = false
                viewModel.ignoreDuplicateAndContinue()
            },
            onCancel = {
                isDuplicateWarningShowing = false
                viewModel.cancelDuplicateScan()
            }
        )
        dialog.show(childFragmentManager, "duplicate_warning")
    }

    private fun showEditDialog(receiptData: ReceiptData?) {
        isDialogShowing = true

        val data = receiptData ?: com.budgetapp.ocr.ReceiptData(
            rawText = "",
            storeName = "",
            totalAmount = null,
            date = java.time.LocalDate.now().toString(),
            category = com.budgetapp.ocr.ExpenseCategory.OTHER
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val accounts = accountRepository.getAllAccountsList()
            val defaultAccountId = accountRepository.getDefaultAccountId()
            val customExpenseCats = categoryRepository.getCategoriesByType(TransactionType.EXPENSE)
                .first().filter { !it.isSystem }

            val dialog = EditExpenseDialog.newInstance(
                receiptData = data,
                onSave = { amount, merchantName, category, date, notes, accountId, nip ->
                    viewModel.saveTransaction(
                        amount = amount,
                        merchantName = merchantName,
                        category = category,
                        date = date,
                        notes = notes,
                        rawOcrText = receiptData?.rawText ?: "",
                        accountId = accountId,
                        nip = nip
                    )
                },
                onDismiss = {
                    isDialogShowing = false
                    if (receiptData != null) {
                        viewModel.dismissDialog()
                    }
                },
                bitmap = viewModel.currentBitmap,
                accounts = accounts,
                defaultAccountId = defaultAccountId,
                customExpenseCategories = customExpenseCats
            )
            dialog.show(childFragmentManager, "edit_expense")
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(requireContext().contentResolver, uri)
            ) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                if (info.size.width > 1440) {
                    val scale = 1440.0 / info.size.width
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt(),
                        (info.size.height * scale).toInt()
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val raw = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
            if (raw.width > 1440) {
                val scale = 1440.0f / raw.width
                val scaled = Bitmap.createScaledBitmap(raw, 1440, (raw.height * scale).toInt(), true)
                raw.recycle()
                scaled
            } else raw
        }

    override fun onDestroyView() {
        super.onDestroyView()
        isDialogShowing = false
        isDuplicateWarningShowing = false
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        isDialogShowing = false
        isDuplicateWarningShowing = false
    }

    /**
     * Publiczne API dla MainActivity - przetwarzanie PDF z tekstem
     */
    fun processPdfData(receiptData: ReceiptData) {
        viewModel.processPdfReceipt(receiptData)
    }

    /**
     * Publiczne API dla MainActivity - przetwarzanie PDF jako obrazu
     */
    fun processPdfBitmap(bitmap: Bitmap) {
        viewModel.processReceiptImage(bitmap)
    }
}
