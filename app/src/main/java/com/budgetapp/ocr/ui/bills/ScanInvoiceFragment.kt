package com.budgetapp.ocr.ui.bills

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
import com.budgetapp.ocr.BankTransferParser
import com.budgetapp.ocr.InvoiceData
import com.budgetapp.ocr.InvoiceParser
import com.budgetapp.ocr.MLKitDocumentScanner
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.repository.AccountRepository
import com.budgetapp.ocr.data.repository.CategoryRepository
import com.budgetapp.ocr.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first

import com.budgetapp.ocr.databinding.FragmentScanInvoiceBinding
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ScanInvoiceFragment : Fragment() {

    private var _binding: FragmentScanInvoiceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScanInvoiceViewModel by viewModels()
    private var isDialogShowing = false

    @Inject lateinit var documentScanner: MLKitDocumentScanner
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var categoryRepository: CategoryRepository
    private var currentCurrency = "PLN"

    private val documentScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.let { pages ->
                if (pages.isNotEmpty()) {
                    val imageUri = pages.first().imageUri
                    processInvoiceScanResult(imageUri)
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
                viewModel.processInvoiceImage(bitmap)
            }
        }
    }

    private val bankTransferLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> processBankTransferPdf(uri) }
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
                    viewModel.processInvoiceImage(bitmap)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.invoice_scan_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanInvoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isDialogShowing = false
        viewModel.resetState()
        setupClickListeners()
        observeState()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.cardImportArea.setOnClickListener {
            launchDocumentScanner()
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

        binding.btnImportBankTransfer.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }
            bankTransferLauncher.launch(intent)
        }

        binding.btnManualEntry.setOnClickListener {
            showEditDialog(null)
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
                    getString(R.string.invoice_scan_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun processInvoiceScanResult(imageUri: Uri) {
        try {
            val bitmap = uriToBitmap(imageUri)
            viewModel.processInvoiceImage(bitmap)
        } catch (e: Exception) {
            android.util.Log.e("ScanInvoice", "Error loading bitmap", e)
            Toast.makeText(requireContext(), getString(R.string.invoice_scan_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(requireContext().contentResolver, uri)
            ) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
        }

    fun processPdfFile(uri: Uri) {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.tvProcessing.visibility = View.VISIBLE
        binding.tvProcessing.text = getString(R.string.analyzing_invoice)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val parser = InvoiceParser(requireContext())

                // Render PDF pages to bitmap for preview in edit dialog
                val pdfBitmap = withContext(Dispatchers.IO) {
                    parser.renderPagesToBitmap(uri)
                }
                viewModel.setPdfBitmap(pdfBitmap)

                var invoiceData = withContext(Dispatchers.IO) {
                    parser.parseInvoice(uri)
                }

                if (invoiceData == null) {
                    binding.tvProcessing.text = getString(R.string.analyzing_invoice)

                    if (pdfBitmap != null) {
                        viewModel.processInvoiceImage(pdfBitmap)
                        binding.progressIndicator.visibility = View.GONE
                        binding.tvProcessing.visibility = View.GONE
                        return@launch
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.invoice_scan_error), Toast.LENGTH_LONG).show()
                    }
                }

                binding.progressIndicator.visibility = View.GONE
                binding.tvProcessing.visibility = View.GONE

                if (invoiceData != null) {

                    // CORRECTED FLOW: First forecasts (with editing), then the main invoice
                    if (invoiceData.forecasts.isNotEmpty()) {
                        showForecastsSelectionDialog(invoiceData)
                    } else {
                        // No forecasts - check for a warning, then show the main invoice dialog
                        if (invoiceData.warningMessage != null) {
                            showOldInvoiceWarning(invoiceData) {
                                viewModel.processInvoiceText(invoiceData)
                            }
                        } else {
                            viewModel.processInvoiceText(invoiceData)
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), getString(R.string.invoice_scan_error), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressIndicator.visibility = View.GONE
                binding.tvProcessing.visibility = View.GONE
                android.util.Log.e("ScanInvoice", "Error parsing PDF", e)
                Toast.makeText(requireContext(), getString(R.string.invoice_scan_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * New dialog with the ability to edit/delete forecasts BEFORE saving
     */
    private fun showForecastsSelectionDialog(invoiceData: InvoiceData) {
        // First check if there is a warning about an old invoice
        if (invoiceData.warningMessage != null) {
            showOldInvoiceWarning(invoiceData) {
                // User accepted - continue with forecasts
                showForecastsSelectionDialogInternal(invoiceData)
            }
        } else {
            showForecastsSelectionDialogInternal(invoiceData)
        }
    }

    private fun showForecastsSelectionDialogInternal(invoiceData: InvoiceData) {
        val dialog = ForecastsSelectionDialog.newInstance(
            forecasts = invoiceData.forecasts,
            currency = currentCurrency,
            onAdd = { selectedForecasts ->
                // Save the selected forecasts
                viewModel.saveForecastBills(
                    selectedForecasts,
                    invoiceData.vendorName,
                    invoiceData.category
                )
                Toast.makeText(requireContext(), getString(R.string.bill_saved), Toast.LENGTH_SHORT).show()

                // CHANGE: Do not show the main invoice - forecasts are sufficient!
                // User can edit in Transactions > Bills
                findNavController().navigateUp()  // Return to previous screen
            },
            onCancel = {
                // Forecasts skipped - show the main invoice
                viewModel.processInvoiceText(invoiceData)
            }
        )
        dialog.show(childFragmentManager, "forecasts_selection")
    }

    /**
     * Warning dialog for an old invoice
     */
    private fun showOldInvoiceWarning(invoiceData: InvoiceData, onConfirm: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.warning))
            .setMessage(invoiceData.warningMessage ?: "This invoice may be outdated.")
            .setPositiveButton(getString(R.string.save_anyway)) { _, _ ->
                onConfirm()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                // Return to previous screen
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Publiczne API dla MainActivity - przetwarzanie obrazu z Galerii jako faktury
     */
    fun processImageBitmap(bitmap: Bitmap) {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.tvProcessing.visibility = View.VISIBLE
        binding.tvProcessing.text = getString(R.string.analyzing_invoice)
        viewModel.processInvoiceImage(bitmap)
    }

    private fun processBankTransferPdf(uri: Uri) {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.tvProcessing.visibility = View.VISIBLE
        binding.tvProcessing.text = getString(R.string.analyzing_bank_transfer)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val parser = BankTransferParser(requireContext())

                val pdfBitmap = withContext(Dispatchers.IO) {
                    parser.renderPagesToBitmap(uri)
                }
                viewModel.setPdfBitmap(pdfBitmap)

                val invoiceData = withContext(Dispatchers.IO) {
                    parser.parseTransfer(uri)
                }

                binding.progressIndicator.visibility = View.GONE
                binding.tvProcessing.visibility = View.GONE

                if (invoiceData != null) {
                    showEditDialogForBankTransfer(invoiceData)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.invoice_scan_error), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressIndicator.visibility = View.GONE
                binding.tvProcessing.visibility = View.GONE
                android.util.Log.e("ScanInvoice", "Error parsing bank transfer PDF", e)
                Toast.makeText(requireContext(), getString(R.string.invoice_scan_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showEditDialogForBankTransfer(invoiceData: InvoiceData) {
        isDialogShowing = true

        viewLifecycleOwner.lifecycleScope.launch {
            val accounts = accountRepository.getAllAccountsList()
            val defaultAccountId = accountRepository.getDefaultAccountId()
            val customBillCats = categoryRepository.getCategoriesByType(TransactionType.BILL)
                .first().filter { !it.isSystem }

            val previewBitmap = viewModel.currentPdfBitmap ?: viewModel.currentBitmap

            val dialog = EditBillDialog.newInstance(
                invoiceData = invoiceData,
                accounts = accounts,
                defaultAccountId = defaultAccountId,
                customBillCategories = customBillCats,
                bitmap = previewBitmap,
                onSave = { vendorName, invoiceNumber, amount, dueDate, issueDate, category,
                           _, _, _, bankAccount, notes, accountId ->
                    viewModel.saveBillAsPaid(
                        vendorName = vendorName,
                        invoiceNumber = invoiceNumber,
                        amount = amount,
                        dueDate = dueDate,
                        issueDate = issueDate,
                        category = category,
                        paymentDate = dueDate,
                        bankAccount = bankAccount,
                        notes = notes,
                        rawOcrText = invoiceData.rawText,
                        accountId = accountId
                    )
                },
                onSaveRecurring = { vendorName, invoiceNumber, amount, issueDate, category,
                                    frequency, durationMonths, paymentDay, startMonth,
                                    reminderEnabled, reminderDays, bankAccount, notes, accountId ->
                    viewModel.saveRecurringBills(
                        vendorName = vendorName,
                        invoiceNumber = invoiceNumber,
                        amount = amount,
                        issueDate = issueDate,
                        category = category,
                        frequency = frequency,
                        durationMonths = durationMonths,
                        paymentDay = paymentDay,
                        startMonth = startMonth,
                        reminderEnabled = reminderEnabled,
                        reminderDaysBefore = reminderDays,
                        bankAccount = bankAccount,
                        notes = notes,
                        rawOcrText = invoiceData.rawText,
                        accountId = accountId
                    )
                },
                onDismiss = {
                    isDialogShowing = false
                    viewModel.dismissDialog()
                }
            )

            dialog.show(childFragmentManager, "edit_bill_bank_transfer")
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                binding.progressIndicator.visibility = if (state.isProcessing) View.VISIBLE else View.GONE
                binding.tvProcessing.visibility = if (state.isProcessing) View.VISIBLE else View.GONE

                if (state.showEditDialog && !isDialogShowing) {
                    showEditDialog(state.invoiceData)
                }

                if (state.saveSuccess) {
                    Toast.makeText(requireContext(), getString(R.string.bill_saved), Toast.LENGTH_SHORT).show()
                    viewModel.clearSuccessMessage()
                    findNavController().navigateUp()
                }

                state.error?.let { error ->
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun showEditDialog(invoiceData: InvoiceData?) {
        isDialogShowing = true

        viewLifecycleOwner.lifecycleScope.launch {
            val accounts = accountRepository.getAllAccountsList()
            val defaultAccountId = accountRepository.getDefaultAccountId()
            val customBillCats = categoryRepository.getCategoriesByType(TransactionType.BILL)
                .first().filter { !it.isSystem }

            val previewBitmap = viewModel.currentPdfBitmap ?: viewModel.currentBitmap

            val dialog = EditBillDialog.newInstance(
                invoiceData = invoiceData,
                accounts = accounts,
                defaultAccountId = defaultAccountId,
                customBillCategories = customBillCats,
                bitmap = previewBitmap,
                onSave = { vendorName, invoiceNumber, amount, dueDate, issueDate, category,
                           recurrence, reminderEnabled, reminderDays, bankAccount, notes, accountId ->
                    viewModel.saveBill(
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
                        rawOcrText = invoiceData?.rawText ?: "",
                        accountId = accountId
                    )
                },
                onSaveRecurring = { vendorName, invoiceNumber, amount, issueDate, category,
                                    frequency, durationMonths, paymentDay, startMonth,
                                    reminderEnabled, reminderDays, bankAccount, notes, accountId ->
                    viewModel.saveRecurringBills(
                        vendorName = vendorName,
                        invoiceNumber = invoiceNumber,
                        amount = amount,
                        issueDate = issueDate,
                        category = category,
                        frequency = frequency,
                        durationMonths = durationMonths,
                        paymentDay = paymentDay,
                        startMonth = startMonth,
                        reminderEnabled = reminderEnabled,
                        reminderDaysBefore = reminderDays,
                        bankAccount = bankAccount,
                        notes = notes,
                        rawOcrText = invoiceData?.rawText ?: "",
                        accountId = accountId
                    )
                },
                onDismiss = {
                    isDialogShowing = false
                    if (invoiceData != null) {
                        viewModel.dismissDialog()
                    }
                }
            )

            dialog.show(childFragmentManager, "edit_bill")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isDialogShowing = false
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        isDialogShowing = false
        viewLifecycleOwner.lifecycleScope.launch {
            currentCurrency = settingsRepository.getValueByKey(
                SettingsRepository.Keys.DEFAULT_CURRENCY, "PLN"
            )
        }
    }
}
