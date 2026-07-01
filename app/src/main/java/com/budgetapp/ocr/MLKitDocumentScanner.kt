package com.budgetapp.ocr

import android.content.Context
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

/**
 * Class for scanning documents using ML Kit Document Scanner
 * Does not require additional apps - works with Google Play Services
 */
class MLKitDocumentScanner(context: Context) {

    private val scanner: GmsDocumentScanner = GmsDocumentScanning.getClient(
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)  // Allow import from gallery
            .setPageLimit(10)               // Max 10 pages
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .build()
    )

    /**
     * Get the scanner activity launcher
     */
    fun getScanner(): GmsDocumentScanner = scanner
}
