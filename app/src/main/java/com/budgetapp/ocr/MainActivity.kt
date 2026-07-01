package com.budgetapp.ocr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.budgetapp.ocr.backup.BackupManager
import com.budgetapp.ocr.data.local.entity.BillStatus
import com.budgetapp.ocr.data.local.entity.TransactionEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.repository.AccountRepository
import com.budgetapp.ocr.data.repository.BillRepository
import com.budgetapp.ocr.data.repository.CategoryRepository
import com.budgetapp.ocr.data.repository.SettingsRepository
import com.budgetapp.ocr.data.repository.TransactionRepository
import com.budgetapp.ocr.export.CsvImporter
import com.budgetapp.ocr.ui.settings.SettingsBottomSheetFragment
import com.budgetapp.ocr.ui.settings.SettingsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject
import com.budgetapp.ocr.databinding.ActivityMainNewBinding
import androidx.appcompat.app.AlertDialog
import com.budgetapp.ocr.ui.bills.ScanInvoiceFragment
import com.budgetapp.ocr.ui.income.AddIncomeDialog
import com.budgetapp.ocr.ui.more.MoreBottomSheetFragment
import com.budgetapp.ocr.ui.scan.ScanReceiptFragment
import com.budgetapp.ocr.ui.scan.ScanReceiptViewModel
import com.budgetapp.ocr.widget.ScanReceiptWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainNewBinding
    private lateinit var navController: androidx.navigation.NavController
    private var isSpeedDialOpen = false
    private var isAppUnlocked = false

    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var billRepository: BillRepository
    @Inject lateinit var backupManager: BackupManager
    @Inject lateinit var csvImporter: CsvImporter

    private var pendingBackupPassword = ""

    private val csvExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch { performCsvExport(uri) }
        }
    }

    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val password = pendingBackupPassword
            lifecycleScope.launch { performBackup(uri, password) }
        }
    }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showRestorePasswordDialog(uri)
        }
    }

    private val csvImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch { performCsvImport(uri) }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainNewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        // Connect BottomNav to NavController
        binding.bottomNav.setupWithNavController(navController)

        // Tab selection — intercept "More" and placeholder
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_more -> {
                    showMoreSheet()
                    false // don't select "More" tab
                }
                R.id.nav_placeholder -> false // ignore FAB placeholder
                else -> {
                    // Pop non-tab destinations from backstack before navigating
                    navController.popBackStack(R.id.categoriesFragment, true)
                    navController.popBackStack(R.id.settingsFragment, true)
                    NavigationUI.onNavDestinationSelected(item, navController)
                }
            }
        }

        // Handle reselection for More and placeholder
        binding.bottomNav.setOnItemReselectedListener { item ->
            when (item.itemId) {
                R.id.nav_more -> showMoreSheet()
                R.id.nav_placeholder -> { /* ignore */ }
                else -> { /* optional: scroll to top on reselect */ }
            }
        }

        // FAB click -> toggle speed dial
        binding.fabScan.setOnClickListener {
            toggleSpeedDial()
        }

        // Scrim overlay click -> close speed dial
        binding.scrimOverlay.setOnClickListener {
            closeSpeedDial()
        }

        // Mini FAB: Add expense (scan receipt screen)
        binding.speedDialExpense.setOnClickListener {
            closeSpeedDial()
            navController.navigate(R.id.scanReceiptFragment)
        }
        binding.miniFabExpense.setOnClickListener {
            closeSpeedDial()
            navController.navigate(R.id.scanReceiptFragment)
        }

        // Mini FAB: Add income
        binding.speedDialIncome.setOnClickListener {
            closeSpeedDial()
            showAddIncomeDialog()
        }
        binding.miniFabIncome.setOnClickListener {
            closeSpeedDial()
            showAddIncomeDialog()
        }

        // Mini FAB: Add bill (scan invoice screen)
        binding.speedDialBill.setOnClickListener {
            closeSpeedDial()
            navController.navigate(R.id.scanInvoiceFragment)
        }
        binding.miniFabBill.setOnClickListener {
            closeSpeedDial()
            navController.navigate(R.id.scanInvoiceFragment)
        }

        // Hide/show bottom bar on scan screens
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (isSpeedDialOpen) closeSpeedDial()
            when (destination.id) {
                R.id.scanReceiptFragment, R.id.scanInvoiceFragment -> {
                    binding.bottomNav.visibility = View.GONE
                    binding.fabScan.visibility = View.GONE
                }
                R.id.settingsFragment, R.id.categoriesFragment -> {
                    // FAB is now centered — no longer overlaps content on these screens
                    binding.bottomNav.visibility = View.VISIBLE
                    binding.fabScan.visibility = View.VISIBLE
                }
                else -> {
                    binding.bottomNav.visibility = View.VISIBLE
                    binding.fabScan.visibility = View.VISIBLE
                }
            }
        }

        // Handle incoming share intent only on fresh launch (not on config change)
        if (savedInstanceState == null) {
            if (intent?.action == ScanReceiptWidgetProvider.ACTION_QUICK_SCAN) {
                handleQuickScanIntent()
            } else {
                handleIncomingIntent(intent)
            }
        }

        // Request notification permission (Android 13+)
        requestNotificationPermission()

        // Show consent dialog on first launch
        checkAndShowConsentDialog()

        // Schedule bill reminder worker
        scheduleBillReminderWorker()
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val lockEnabled = settingsRepository.getValueByKey(
                SettingsRepository.Keys.APP_LOCK_ENABLED, "false"
            ) == "true"
            if (lockEnabled && !isAppUnlocked) {
                showBiometricLock()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Reset lock when app goes to background
        lifecycleScope.launch {
            val lockEnabled = settingsRepository.getValueByKey(
                SettingsRepository.Keys.APP_LOCK_ENABLED, "false"
            ) == "true"
            if (lockEnabled) isAppUnlocked = false
        }
    }

    private fun showBiometricLock() {
        val biometricManager = androidx.biometric.BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            isAppUnlocked = true
            return
        }

        val prompt = androidx.biometric.BiometricPrompt(
            this,
            androidx.core.content.ContextCompat.getMainExecutor(this),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: androidx.biometric.BiometricPrompt.AuthenticationResult
                ) {
                    isAppUnlocked = true
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finishAffinity()
                }
                override fun onAuthenticationFailed() { /* prompt stays open */ }
            }
        )

        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_lock_title))
            .setSubtitle(getString(R.string.app_lock_subtitle))
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ScanReceiptWidgetProvider.ACTION_QUICK_SCAN) {
            handleQuickScanIntent()
        } else {
            handleIncomingIntent(intent)
        }
    }

    private fun handleQuickScanIntent() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bundle = Bundle().apply {
            putBoolean("auto_scan", true)
        }
        navController.navigate(R.id.scanReceiptFragment, bundle)
    }

    // ── Speed Dial Animation ────────────────────────────────────────────

    private fun toggleSpeedDial() {
        if (isSpeedDialOpen) closeSpeedDial() else openSpeedDial()
    }

    private fun openSpeedDial() {
        isSpeedDialOpen = true

        // Show scrim
        binding.scrimOverlay.visibility = View.VISIBLE
        binding.scrimOverlay.alpha = 0f
        binding.scrimOverlay.animate().alpha(1f).setDuration(200).start()

        // Rotate main FAB to X
        binding.fabScan.animate()
            .rotation(45f)
            .setDuration(250)
            .setInterpolator(OvershootInterpolator())
            .start()

        // Arc animation — staggered, scale + fade
        val items = listOf(binding.speedDialIncome, binding.speedDialExpense, binding.speedDialBill)
        items.forEachIndexed { index, view ->
            view.visibility = View.VISIBLE
            view.alpha = 0f
            view.scaleX = 0.3f
            view.scaleY = 0.3f

            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay((index * 60).toLong())
                .setDuration(250)
                .setInterpolator(OvershootInterpolator(2f))
                .start()
        }
    }

    private fun closeSpeedDial() {
        isSpeedDialOpen = false

        // Fade out scrim
        binding.scrimOverlay.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction { binding.scrimOverlay.visibility = View.GONE }
            .start()

        // Rotate FAB back
        binding.fabScan.animate()
            .rotation(0f)
            .setDuration(200)
            .start()

        // Animate mini FABs out with scale
        val items = listOf(binding.speedDialBill, binding.speedDialExpense, binding.speedDialIncome)
        items.forEachIndexed { index, view ->
            view.animate()
                .alpha(0f)
                .scaleX(0.3f)
                .scaleY(0.3f)
                .setStartDelay((index * 40).toLong())
                .setDuration(150)
                .withEndAction { view.visibility = View.GONE }
                .start()
        }
    }

    // ── Share Intent Handling (PDF + Images) ─────────────────────────────

    private fun handleIncomingIntent(intent: Intent) {
        val action = intent.action
        val type = intent.type ?: return

        val uri: Uri? = when (action) {
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }

        if (uri == null) {
            android.util.Log.w("MainActivity", "Share intent has no URI. action=$action, type=$type, extras=${intent.extras?.keySet()}")
            // Some apps share text/URL instead of file
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
            }
            return
        }


        // Clear intent data so it won't re-trigger on config changes (theme/language)
        intent.action = null
        intent.type = null
        intent.data = null
        intent.removeExtra(Intent.EXTRA_STREAM)

        when {
            type == "application/pdf" -> {
                showPdfTypeDialog(uri)
            }
            type.startsWith("image/") -> {
                showImageTypeDialog(uri)
            }
            else -> {
                android.util.Log.w("MainActivity", "Unsupported share type: $type")
            }
        }
    }

    private fun showPdfTypeDialog(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pdf_type_dialog_title))
            .setItems(
                arrayOf(
                    getString(R.string.pdf_type_receipt),
                    getString(R.string.pdf_type_invoice)
                )
            ) { _, which ->
                when (which) {
                    0 -> processPdfIntent(uri)
                    1 -> processInvoicePdfIntent(uri)
                }
            }
            .show()
    }

    private fun processPdfIntent(uri: Uri) {
        lifecycleScope.launch {
            try {
                val parser = PdfReceiptParser(this@MainActivity)
                Toast.makeText(this@MainActivity, getString(R.string.analyzing_receipt), Toast.LENGTH_SHORT).show()

                val receiptData = withContext(Dispatchers.IO) {
                    parser.parseReceipt(uri)
                }

                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.navHostFragment) as NavHostFragment
                val navController = navHostFragment.navController

                navController.navigate(R.id.scanReceiptFragment)

                withContext(Dispatchers.Main) {
                    kotlinx.coroutines.delay(300)
                }

                val fragment = navHostFragment.childFragmentManager.fragments
                    .firstOrNull { it is ScanReceiptFragment }
                    as? ScanReceiptFragment

                if (fragment != null) {
                    if (receiptData == null) {
                        val bitmap = withContext(Dispatchers.IO) {
                            parser.renderPagesToBitmap(uri)
                        }
                        if (bitmap != null) {
                            fragment.processPdfBitmap(bitmap)
                        } else {
                            Toast.makeText(this@MainActivity, getString(R.string.receipt_scan_error), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        fragment.processPdfData(receiptData)
                    }
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error parsing PDF", e)
                Toast.makeText(this@MainActivity, getString(R.string.receipt_scan_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processInvoicePdfIntent(uri: Uri) {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        navController.navigate(R.id.scanInvoiceFragment)

        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                kotlinx.coroutines.delay(300)
            }

            val fragment = navHostFragment.childFragmentManager.fragments
                .firstOrNull { it is ScanInvoiceFragment }
                as? ScanInvoiceFragment

            if (fragment != null) {
                fragment.processPdfFile(uri)
            } else {
                Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Image Share Intent Handling ──────────────────────────────────────

    private fun showImageTypeDialog(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pdf_type_dialog_title))
            .setItems(
                arrayOf(
                    getString(R.string.pdf_type_receipt),
                    getString(R.string.pdf_type_invoice)
                )
            ) { _, which ->
                when (which) {
                    0 -> processImageAsReceipt(uri)
                    1 -> processImageAsInvoice(uri)
                }
            }
            .show()
    }

    private fun processImageAsReceipt(uri: Uri) {
        Toast.makeText(this, getString(R.string.analyzing_receipt), Toast.LENGTH_SHORT).show()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        navController.navigate(R.id.scanReceiptFragment)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    kotlinx.coroutines.delay(300)
                }

                val bitmap = withContext(Dispatchers.IO) {
                    uriToBitmap(uri)
                }

                val fragment = navHostFragment.childFragmentManager.fragments
                    .firstOrNull { it is ScanReceiptFragment }
                    as? ScanReceiptFragment

                if (fragment != null) {
                    fragment.processPdfBitmap(bitmap)
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_SHORT).show()
                }
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("MainActivity", "OOM loading image - too large", e)
                Toast.makeText(this@MainActivity, getString(R.string.receipt_scan_error), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error processing image as receipt", e)
                Toast.makeText(this@MainActivity, getString(R.string.receipt_scan_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processImageAsInvoice(uri: Uri) {
        Toast.makeText(this, getString(R.string.analyzing_invoice), Toast.LENGTH_SHORT).show()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        navController.navigate(R.id.scanInvoiceFragment)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    kotlinx.coroutines.delay(300)
                }

                val bitmap = withContext(Dispatchers.IO) {
                    uriToBitmap(uri)
                }

                val fragment = navHostFragment.childFragmentManager.fragments
                    .firstOrNull { it is ScanInvoiceFragment }
                    as? ScanInvoiceFragment

                if (fragment != null) {
                    fragment.processImageBitmap(bitmap)
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error processing image as invoice", e)
                Toast.makeText(this@MainActivity, getString(R.string.invoice_scan_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(contentResolver, uri)
            ) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }

    // ── Notifications & WorkManager ─────────────────────────────────────

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun scheduleBillReminderWorker() {
        val workRequest = PeriodicWorkRequestBuilder<BillReminderWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BillReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    // ── Consent Dialog ────────────────────────────────────────────────

    private fun checkAndShowConsentDialog() {
        lifecycleScope.launch {
            val onboardingDone = settingsRepository.getValueByKey(
                SettingsRepository.Keys.ONBOARDING_COMPLETED
            )
            if (onboardingDone != null) return@launch

            // Show consent dialog
            withContext(Dispatchers.Main) {
                val density = resources.displayMetrics.density
                val checkBox = android.widget.CheckBox(this@MainActivity).apply {
                    setText(R.string.consent_enable)
                    isChecked = true
                }
                val container = android.widget.FrameLayout(this@MainActivity).apply {
                    val lp = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.marginStart = (24 * density).toInt()
                    lp.topMargin = (12 * density).toInt()
                    lp.bottomMargin = (4 * density).toInt()
                    addView(checkBox, lp)
                }

                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(getString(R.string.consent_title))
                    .setMessage(getString(R.string.consent_message))
                    .setView(container)
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.consent_continue)) { _, _ ->
                        lifecycleScope.launch {
                            settingsRepository.setSetting(
                                SettingsRepository.Keys.API_STORE_LOOKUP_ENABLED,
                                checkBox.isChecked.toString()
                            )
                            settingsRepository.setSetting(
                                SettingsRepository.Keys.ONBOARDING_COMPLETED,
                                "true"
                            )
                        }
                    }
                    .show()
            }
        }
    }

    // ── More Bottom Sheet ─────────────────────────────────────────────

    private fun showMoreSheet() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        // NavOptions: pop to start destination to keep backstack clean,
        // singleTop to prevent stacking the same fragment multiple times
        val moreNavOptions = NavOptions.Builder()
            .setPopUpTo(navController.graph.startDestinationId, false)
            .setLaunchSingleTop(true)
            .build()

        val sheet = MoreBottomSheetFragment.newInstance(
            onSettingsClick = {
                SettingsBottomSheetFragment().show(supportFragmentManager, "settings_sheet")
            },
            onCategoriesClick = {
                navController.navigate(R.id.categoriesFragment, null, moreNavOptions)
            },
            onExportClick = {
                val timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(java.time.Instant.now())
                csvExportLauncher.launch("budget_export_$timestamp.csv")
            },
            onImportCsvClick = {
                csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
            },
            onBackupClick = {
                showBackupPasswordDialog()
            },
            onRestoreClick = {
                showRestoreWarningDialog()
            },
            onClearDataClick = {
                showClearDataDialog()
            }
        )
        sheet.show(supportFragmentManager, "more_sheet")
    }

    // ── Data operations (moved from SettingsFragment) ──────────────────

    private suspend fun performCsvExport(uri: Uri) {
        try {
            val transactions = withContext(Dispatchers.IO) {
                transactionRepository.getAllTransactions().first()
            }
            val bills = withContext(Dispatchers.IO) {
                billRepository.getAllBills().first()
            }

            if (transactions.isEmpty() && bills.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_transactions), Toast.LENGTH_SHORT).show()
                return
            }

            val accountsMap = withContext(Dispatchers.IO) {
                accountRepository.getAllAccountsList().associateBy({ it.id }, { it.name })
            }

            val currency = settingsRepository.getValueByKey(
                SettingsRepository.Keys.DEFAULT_CURRENCY, "PLN"
            )
            val billTypeLabel = getString(R.string.bill_type)
            val csv = buildString {
                appendLine("Typ,Kwota,Waluta,Kategoria,Sklep/Dostawca,Data,Notatki,Status,Konto")

                for (tx in transactions) {
                    val type = if (tx.type.name == "EXPENSE")
                        getString(R.string.expense_type) else getString(R.string.income_type)
                    val amount   = String.format("%.2f", tx.amount)
                    val name     = escapeCsv(tx.merchantName ?: tx.payee ?: "")
                    val notes    = escapeCsv(tx.notes)
                    val category = escapeCsv(tx.category)
                    val account  = escapeCsv(accountsMap[tx.accountId] ?: "")
                    appendLine("$type,$amount,${tx.currency},$category,$name,${tx.date},$notes,,$account")
                }

                for (bill in bills) {
                    val amount      = String.format("%.2f", bill.amount)
                    val vendor      = escapeCsv(bill.vendorName)
                    val invoiceNum  = escapeCsv(bill.invoiceNumber)
                    val status = when (bill.status) {
                        BillStatus.PAID    -> getString(R.string.status_paid)
                        BillStatus.OVERDUE -> getString(R.string.status_overdue)
                        else               -> getString(R.string.status_unpaid)
                    }
                    val account = escapeCsv(accountsMap[bill.accountId] ?: "")
                    appendLine("$billTypeLabel,$amount,$currency,${bill.category},$vendor,${bill.dueDate},$invoiceNum,$status,$account")
                }
            }

            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    stream.write(csv.toByteArray(Charsets.UTF_8))
                }
            }

            Toast.makeText(this, getString(R.string.export_success), Toast.LENGTH_SHORT).show()

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_data)))

        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_error), Toast.LENGTH_LONG).show()
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun showBackupPasswordDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.backup_password_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 24, 48, 24)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.backup_title))
            .setView(input)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                pendingBackupPassword = input.text.toString()
                val timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(java.time.Instant.now())
                backupLauncher.launch("backup_$timestamp.skb")
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private suspend fun performBackup(uri: Uri, password: String) {
        try {
            withContext(Dispatchers.IO) {
                backupManager.backup(uri, password)
            }
            Toast.makeText(this, getString(R.string.backup_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.backup_error), Toast.LENGTH_LONG).show()
        }
    }

    private fun showRestoreWarningDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.restore_title))
            .setMessage(getString(R.string.restore_warning))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showRestorePasswordDialog(uri: Uri) {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.backup_password_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 24, 48, 24)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.restore_title))
            .setView(input)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val password = input.text.toString()
                lifecycleScope.launch { performRestore(uri, password) }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private suspend fun performRestore(uri: Uri, password: String) {
        try {
            Toast.makeText(this, getString(R.string.restore_success), Toast.LENGTH_SHORT).show()
            withContext(Dispatchers.IO) {
                backupManager.restore(uri, password)
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.restore_error_wrong_password), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.restore_error), Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun performCsvImport(uri: Uri) {
        try {
            val result = withContext(Dispatchers.IO) {
                csvImporter.import(uri, this@MainActivity)
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_csv_title))
                .setMessage(getString(R.string.import_success, result.imported, result.skipped))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.import_error), Toast.LENGTH_LONG).show()
        }
    }

    private fun showClearDataDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_all_data))
            .setMessage(getString(R.string.clear_data_confirm))
            .setPositiveButton(getString(R.string.clear_data_continue)) { _, _ ->
                showClearDataFinalConfirmDialog()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showClearDataFinalConfirmDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.clear_data_type_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setPadding(48, 24, 48, 24)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_data_final_title))
            .setMessage(getString(R.string.clear_data_final_message))
            .setView(input)
            .setPositiveButton(getString(R.string.clear_data_confirm_button), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.isEnabled = false
            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val typed = s?.toString()?.trim() ?: ""
                    btn.isEnabled = typed == getString(R.string.clear_data_keyword)
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            btn.setOnClickListener {
                dialog.dismiss()
                val settingsVm = ViewModelProvider(this@MainActivity)[SettingsViewModel::class.java]
                settingsVm.clearAllData {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.data_cleared),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        dialog.show()
    }

    // ── Expense Dialog ──────────────────────────────────────────────────

    private fun showAddExpenseDialog() {
        lifecycleScope.launch {
            val accounts = accountRepository.getAllAccountsList()
            val defaultAccountId = accountRepository.getDefaultAccountId()
            val customExpenseCats = categoryRepository.getCategoriesByType(TransactionType.EXPENSE)
                .first().filter { !it.isSystem }
            val currency = settingsRepository.getValueByKey(
                SettingsRepository.Keys.DEFAULT_CURRENCY, "PLN"
            )
            val emptyReceiptData = ReceiptData(
                rawText = "",
                storeName = "",
                totalAmount = null,
                date = java.time.LocalDate.now().toString(),
                category = ExpenseCategory.OTHER
            )
            val dialog = EditExpenseDialog.newInstance(
                receiptData = emptyReceiptData,
                onSave = { amount, merchantName, category, date, notes, accountId, _ ->
                    lifecycleScope.launch {
                        try {
                            val transaction = TransactionEntity(
                                type = TransactionType.EXPENSE,
                                amount = amount,
                                currency = currency,
                                category = category,
                                merchantName = merchantName,
                                date = date ?: java.time.LocalDate.now().toString(),
                                notes = notes,
                                accountId = accountId
                            )
                            transactionRepository.insert(transaction)
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.receipt_saved),
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.error),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onScanReceipt = {
                    navController.navigate(R.id.scanReceiptFragment)
                },
                accounts = accounts,
                defaultAccountId = defaultAccountId,
                customExpenseCategories = customExpenseCats
            )
            dialog.show(supportFragmentManager, "add_expense")
        }
    }

    // ── Income Dialog ───────────────────────────────────────────────────

    private fun showAddIncomeDialog() {
        lifecycleScope.launch {
            val accounts = accountRepository.getAllAccountsList()
            val defaultAccountId = accountRepository.getDefaultAccountId()
            val customIncomeCats = categoryRepository.getCategoriesByType(TransactionType.INCOME)
                .first().filter { !it.isSystem }

            val dialog = AddIncomeDialog.newInstance(
                accounts = accounts,
                defaultAccountId = defaultAccountId,
                customIncomeCategories = customIncomeCats
            ) { amount, payee, category, date, notes, accountId ->
                val viewModel = ViewModelProvider(this@MainActivity)[ScanReceiptViewModel::class.java]

                lifecycleScope.launch {
                    try {
                        val transaction = TransactionEntity(
                            type = TransactionType.INCOME,
                            amount = amount,
                            currency = "PLN",
                            category = category,
                            payee = payee,
                            date = date,
                            notes = notes,
                            accountId = accountId
                        )
                        viewModel.saveIncome(transaction)
                        Toast.makeText(this@MainActivity, getString(R.string.receipt_saved), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            dialog.show(supportFragmentManager, "add_income")
        }
    }
}
