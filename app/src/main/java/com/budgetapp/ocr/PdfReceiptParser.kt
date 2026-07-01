package com.budgetapp.ocr

import android.content.Context
import android.graphics.Bitmap
import com.budgetapp.ocr.R
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

/**
 * Parser for PDF receipts from store apps (Biedronka, Lidl, IKEA, etc.)
 * Extracts text from PDF and parses receipt data.
 */
class PdfReceiptParser(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Extracts text from a PDF file provided as a Uri
     */
    fun extractText(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                android.util.Log.e("PdfReceiptParser", "Failed to open input stream for URI: $uri")
                return null
            }

            inputStream.use { stream ->
                extractTextFromStream(stream)
            }
        } catch (e: SecurityException) {
            android.util.Log.e("PdfReceiptParser", "Security exception - no permission to read URI: $uri", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("PdfReceiptParser", "Error extracting text from PDF: ${e.javaClass.simpleName} - ${e.message}", e)
            null
        }
    }

    private fun extractTextFromStream(inputStream: InputStream): String {
        // PDFBox may have trouble with streams from content:// URIs
        // Copy to a temporary file
        val tempFile = java.io.File(context.cacheDir, "temp_receipt.pdf")
        try {
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }


            val document = PDDocument.load(tempFile)
            return try {
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                text
            } finally {
                document.close()
            }
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Parses PDF text into ReceiptData
     */
    fun parseReceipt(uri: Uri): ReceiptData? {
        val text = extractText(uri) ?: return null

        // If PDF has fewer than 50 characters, it is likely an image (not native PDF text)
        if (text.trim().length < 50) {
            android.util.Log.w("PdfReceiptParser", "PDF contains too little text (${text.length} chars) - likely a scanned image, not native PDF text")
            return null
        }

        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        val (storeName, isKnownBrand) = detectStoreName(lines, text)
        val totalAmount = extractTotalAmount(lines)
        val date = extractDate(text)
        val nip = extractNip(text)
        val category = CategoryManager.categorize(storeName, text, context)

        return ReceiptData(
            rawText = text,
            storeName = storeName,
            totalAmount = totalAmount,
            date = date,
            category = category,
            nip = nip,
            isKnownBrand = isKnownBrand
        )
    }

    /**
     * Renders all PDF pages into a single combined Bitmap
     * Biedronka receipts have 2 pages - the total is on the second
     */
    fun renderPagesToBitmap(uri: Uri): Bitmap? {
        return try {
            val tempFile = java.io.File(context.cacheDir, "temp_pdf_render.pdf")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val fileDescriptor = ParcelFileDescriptor.open(
                    tempFile,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )

                val renderer = PdfRenderer(fileDescriptor)
                val pageCount = renderer.pageCount


                // Render each page — scale to at least 720px wide for OCR quality,
                // but cap at 1080px to save memory on multi-page PDFs
                val pageBitmaps = mutableListOf<Bitmap>()
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val targetWidth = if (pageCount > 1) 720 else 1080
                    val renderScale = maxOf(2, (targetWidth + page.width - 1) / page.width)
                    val bitmap = Bitmap.createBitmap(
                        page.width * renderScale,
                        page.height * renderScale,
                        Bitmap.Config.ARGB_8888
                    )
                    // White background (receipt)
                    android.graphics.Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pageBitmaps.add(bitmap)
                    page.close()
                }
                renderer.close()
                fileDescriptor.close()

                if (pageBitmaps.isEmpty()) return null

                // If single page, return it
                if (pageBitmaps.size == 1) {
                    return pageBitmaps[0]
                }

                // Combine pages vertically
                val totalHeight = pageBitmaps.sumOf { it.height }
                val maxWidth = pageBitmaps.maxOf { it.width }
                val combined = Bitmap.createBitmap(maxWidth, totalHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(combined)
                canvas.drawColor(android.graphics.Color.WHITE)

                var yOffset = 0f
                for (page in pageBitmaps) {
                    canvas.drawBitmap(page, 0f, yOffset, null)
                    yOffset += page.height
                    page.recycle()
                }

                combined
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfReceiptParser", "Error rendering PDF to bitmap", e)
            null
        }
    }

    /**
     * Backward compatibility
     */
    fun renderFirstPageToBitmap(uri: Uri): Bitmap? = renderPagesToBitmap(uri)

    /**
     * Detects the store name from PDF text
     */
    /** Returns (storeName, isKnownBrand). */
    private fun detectStoreName(lines: List<String>, fullText: String): Pair<String, Boolean> {
        val textLower = fullText.lowercase()

        // Known stores — loaded from locale-specific JSON asset
        val knownStores = com.budgetapp.ocr.util.LocaleDataLoader.loadOcrKeywords(context)

        for ((key, name) in knownStores) {
            val pattern = """\b${Regex.escape(key)}\b""".toRegex()
            if (pattern.containsMatchIn(textLower)) return name to true
        }

        // Fallback: first non-empty line
        return (lines.firstOrNull() ?: context.getString(R.string.unknown_store)) to false
    }

    /**
     * Extracts the SUMA/RAZEM/DO ZAPŁATY total amount from PDF text
     */
    private fun extractTotalAmount(lines: List<String>): Double? {
        // PDFs have clean text, so we search for standard patterns
        val keywords = listOf(
            "do zapłaty", "do zaplaty",
            "suma pln", "suma",
            "razem", "łącznie", "lacznie",
            "total", "amount",
            "wartość sprzedaży", "wartosc sprzedazy",
            "należność", "naleznosc"
        )

        // Search for lines with keyword + amount
        for (line in lines) {
            val lineLower = line.lowercase()
            for (keyword in keywords) {
                if (lineLower.contains(keyword)) {
                    val amount = extractNumberFromLine(line)
                    if (amount != null && amount in 0.01..99999.99) {
                        return amount
                    }
                }
            }
        }

        // Search in the next line after the keyword
        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            for (keyword in keywords) {
                if (lineLower.contains(keyword) && i + 1 < lines.size) {
                    val amount = extractNumberFromLine(lines[i + 1])
                    if (amount != null && amount in 0.01..99999.99) {
                        return amount
                    }
                }
            }
        }

        // Fallback: largest amount in the text
        return lines.mapNotNull { extractNumberFromLine(it) }
            .filter { it in 10.0..99999.99 }
            .maxOrNull()
    }

    private fun extractNumberFromLine(line: String): Double? {
        // Patterns: 123,45 | 123.45 | 1 234,56 | 1234.56
        val regex = """(\d{1,3}(?:[\s ]?\d{3})*[,\.]\d{2})""".toRegex()
        val matches = regex.findAll(line).toList()

        // Take the last number in the line (the amount is usually on the right)
        return matches.lastOrNull()?.let {
            it.value.replace(" ", "").replace(",", ".").toDoubleOrNull()
        }
    }

    /**
     * Extracts a NIP (tax ID) from PDF text.
     */
    private fun extractNip(text: String): String? {
        // Keyword + formatted or continuous 10 digits
        val nipRegex = """NIP[:\s.-]*(?:PL)?[:\s]*(\d{3}[-\s.]*\d{2,3}[-\s.]*\d{2,3}[-\s.]*\d{2,3})""".toRegex(RegexOption.IGNORE_CASE)
        nipRegex.find(text)?.let { match ->
            val nip = match.groupValues[1].replace("""[-\s.]""".toRegex(), "")
            if (nip.length == 10) return nip
        }
        // Continuous 10 digits after NIP keyword
        val nipContinuous = """NIP[:\s.-]*(?:PL)?[:\s]*(\d{10})""".toRegex(RegexOption.IGNORE_CASE)
        nipContinuous.find(text)?.let { return it.groupValues[1] }
        return null
    }

    /**
     * Extracts the date and normalizes it to YYYY-MM-DD
     */
    private fun extractDate(text: String): String? {
        // Skip tax IDs (NIP)
        val cleanText = text.replace("""NIP\s*:?\s*[\d-]+""".toRegex(RegexOption.IGNORE_CASE), "")

        // DD.MM.YYYY HH:MM lub DD.MM.YYYY
        val ddmmyyyy = """(\d{2})[-./](\d{2})[-./](\d{4})(?:\s+\d{2}:\d{2})?""".toRegex()
        ddmmyyyy.find(cleanText)?.let { match ->
            val (d, m, y) = match.destructured
            val day = d.toIntOrNull() ?: return@let
            val month = m.toIntOrNull() ?: return@let
            val year = y.toIntOrNull() ?: return@let
            if (day in 1..31 && month in 1..12 && year in 2020..2030) {
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }

        // YYYY-MM-DD (ISO)
        val iso = """(\d{4})[-./](\d{2})[-./](\d{2})""".toRegex()
        iso.find(cleanText)?.let { match ->
            val (y, m, d) = match.destructured
            val year = y.toIntOrNull() ?: return@let
            val month = m.toIntOrNull() ?: return@let
            val day = d.toIntOrNull() ?: return@let
            if (day in 1..31 && month in 1..12 && year in 2020..2030) {
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }

        // DD.MM.YY (short year)
        val ddmmyy = """(\d{2})[-./](\d{2})[-./](\d{2})(?:\s|$)""".toRegex()
        ddmmyy.find(cleanText)?.let { match ->
            val (d, m, y) = match.destructured
            val day = d.toIntOrNull() ?: return@let
            val month = m.toIntOrNull() ?: return@let
            val shortYear = y.toIntOrNull() ?: return@let
            val year = 2000 + shortYear
            if (day in 1..31 && month in 1..12 && year in 2020..2030) {
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }

        return null
    }
}
