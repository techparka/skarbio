package com.budgetapp.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.budgetapp.ocr.util.LocaleDataLoader
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.IOException
import android.util.Log
import kotlin.math.abs

private const val TAG = "ReceiptParser"

/**
 * Recognizes text from receipts using ML Kit OCR.
 */
class ReceiptTextRecognizer(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Recognizes text from a Bitmap image
     * @param bitmap Image to analyze
     * @return Recognized text or null on error
     */
    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            result.text.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Recognizes text from a Uri image
     * @param uri Image URI
     * @param context Application context (needed to read the Uri)
     * @return Recognized text or null on error
     */
    suspend fun recognizeTextFromUri(
        uri: Uri,
        context: android.content.Context
    ): String? {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val result = recognizer.process(image).await()
            result.text.takeIf { it.isNotEmpty() }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Maximum image height for a single ML Kit pass.
     * Very tall images (long receipts) are split into segments to avoid
     * ML Kit silently returning partial/empty results on lower-end devices.
     */
    private val maxSegmentHeight = 3000
    private val segmentHeight = 2000
    private val overlapHeight = 200

    /**
     * Recognizes text and additionally parses receipt information.
     * For very tall images (long receipts like Auchan e-tickets), automatically
     * splits into segments and combines results.
     *
     * @param bitmap Image to analyze
     * @return ReceiptData object with parsed data
     */
    suspend fun recognizeReceiptData(bitmap: Bitmap): ReceiptData {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val receiptData = parseReceiptDataStructured(result)

            // Check if ML Kit managed to read enough text
            // For tall images, if rawText is very short, ML Kit failed → fallback to top/bottom
            if (bitmap.height > maxSegmentHeight && receiptData.rawText.length < 100) {
                recognizeLongReceiptFull(bitmap)
            } else {
                receiptData
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (bitmap.height > maxSegmentHeight) {
                recognizeLongReceiptFull(bitmap)
            } else {
                val text = recognizeTextFromBitmap(bitmap) ?: ""
                parseReceiptData(text)
            }
        }
    }

    /**
     * Processes very tall receipts using a sliding window strategy.
     * Splits the bitmap into overlapping segments covering the entire receipt,
     * then merges results with deduplication of overlapping lines.
     */
    private suspend fun recognizeLongReceiptFull(bitmap: Bitmap): ReceiptData {
        return try {
            val stride = segmentHeight - overlapHeight  // 1800px
            val segmentTexts = mutableListOf<String>()
            var y = 0

            while (y < bitmap.height) {
                val h = minOf(segmentHeight, bitmap.height - y)
                if (h < 100) break
                val text = ocrSegment(bitmap, y, h)
                segmentTexts.add(text)
                y += stride
            }

            val fullText = mergeOverlappingTexts(segmentTexts)
            parseReceiptData(fullText)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM in sliding window, trying 50% scale fallback", e)
            val scaled = Bitmap.createScaledBitmap(
                bitmap, bitmap.width / 2, bitmap.height / 2, true
            )
            try {
                val text = recognizeTextFromBitmap(scaled) ?: ""
                parseReceiptData(text)
            } finally {
                scaled.recycle()
            }
        }
    }

    /**
     * Merges overlapping text segments by deduplicating lines in the overlap zone.
     * Compares the tail of the previous segment with the head of the next segment
     * and removes duplicated lines.
     */
    private fun mergeOverlappingTexts(segments: List<String>): String {
        if (segments.isEmpty()) return ""
        if (segments.size == 1) return segments[0]

        val result = StringBuilder(segments[0])

        for (i in 1 until segments.size) {
            val prevLines = result.toString().split("\n").map { it.trim().lowercase() }
            val currLines = segments[i].split("\n")
            val currLinesNorm = currLines.map { it.trim().lowercase() }

            val maxOverlap = minOf(8, prevLines.size, currLinesNorm.size)
            var bestMatch = 0

            // Find the longest suffix-prefix overlap
            for (overlapLen in maxOverlap downTo 1) {
                val prevTail = prevLines.takeLast(overlapLen)
                val currHead = currLinesNorm.take(overlapLen)
                if (prevTail == currHead) {
                    bestMatch = overlapLen
                    break
                }
            }

            // Fallback: find first line of curr that matches any of the last 8 lines of prev
            if (bestMatch == 0) {
                val prevTail = prevLines.takeLast(maxOverlap).toSet()
                for (j in 0 until minOf(maxOverlap, currLinesNorm.size)) {
                    if (currLinesNorm[j] in prevTail && currLinesNorm[j].isNotBlank()) {
                        bestMatch = 1
                        // Skip up to this line (inclusive)
                        val skipped = currLines.drop(j + 1).joinToString("\n")
                        result.append("\n").append(skipped)
                        break
                    }
                }
                if (bestMatch == 0) {
                    result.append("\n").append(segments[i])
                }
            } else {
                val remaining = currLines.drop(bestMatch).joinToString("\n")
                if (remaining.isNotEmpty()) {
                    result.append("\n").append(remaining)
                }
            }
        }

        return result.toString()
    }

    /**
     * Extracts total amount from the bottom segment of a long receipt.
     * Strategy: find the last numeric amount before "XXX,XX pln" payment lines.
     * On Polish receipts, payment lines (gotówka/karta) have "pln" suffix,
     * and the total appears just before them.
     * Falls back to standard extractTotalAmount if no payment lines found.
     */
    private fun extractTotalFromBottomSegment(text: String): Double? {
        val lines = text.split("\n").map { it.trim() }
        val textLower = text.lowercase()

        // Priority 1: "DO ZAPŁATY" — same as standard, most explicit
        val doZaplatyIndex = textLower.lastIndexOf("do zapła")
        if (doZaplatyIndex != -1) {
            val after = textLower.substring(doZaplatyIndex)
            val amounts = extractAllNumbers(after)
            val valid = amounts.filter { it in 0.01..9999.99 }.firstOrNull()
            if (valid != null) {
                return valid
            }
        }

        // Priority 2: Find payment lines ("XXX,XX pln") and take the last amount before them
        // E.g.: "498,52" (total) → "500,00 pln" (cash) → "1,48 pln" (change)
        val plnPattern = """(\d+[.,]\d{2})\s*pln""".toRegex(RegexOption.IGNORE_CASE)
        val plnMatches = plnPattern.findAll(text).toList()

        if (plnMatches.isNotEmpty()) {
            val firstPlnIndex = plnMatches.first().range.first
            val textBefore = text.substring(0, firstPlnIndex)
            val amountRegex = """(\d{1,3}(?:[.,]\d{3})*[.,]\d{2})""".toRegex()
            val allAmounts = amountRegex.findAll(textBefore).toList()
            val lastAmount = allAmounts.lastOrNull()

            if (lastAmount != null) {
                val value = lastAmount.value.replace(",", ".").replace(" ", "").toDoubleOrNull()
                if (value != null && value in 1.0..9999.99) {
                    return value
                }
            }
        }

        // Priority 3: Standard extraction as fallback
        return extractTotalAmount(lines)
    }

    /**
     * OCR a horizontal segment of the bitmap.
     */
    private suspend fun ocrSegment(bitmap: Bitmap, y: Int, height: Int): String {
        val segment = Bitmap.createBitmap(bitmap, 0, y, bitmap.width, height)
        val text = recognizeTextFromBitmap(segment) ?: ""
        segment.recycle()
        return text
    }

    /**
     * Parses receipt text and extracts key information
     */
    private fun parseReceiptData(text: String): ReceiptData {
        val lines = text.split("\n").map { it.trim() }

        // Extract store name - smartly, skipping dates
        val (storeName, isKnownBrand) = detectStoreName(lines, text)

        // Extract amount (looking for "SUMA", "RAZEM", "DO ZAPŁATY", etc.)
        val totalAmount = extractTotalAmount(lines)

        // Extract date
        val date = extractDate(text)

        // Extract NIP
        val nip = extractNip(text)

        // Detect category using CategoryManager
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
     * Smart detection of the store name from OCR
     * Skips dates, tax IDs, addresses and searches for the real name
     */
    /** Returns (storeName, isKnownBrand). */
    private fun detectStoreName(lines: List<String>, fullText: String): Pair<String, Boolean> {
        val textLower = fullText.lowercase()

        // Known stores — loaded from locale-specific JSON asset
        val knownStores = LocaleDataLoader.loadOcrKeywords(context)

        for ((key, name) in knownStores) {
            val pattern = """\b${Regex.escape(key)}\b""".toRegex()
            if (pattern.containsMatchIn(textLower)) return name to true
        }

        // Search for the first non-empty line that is NOT:
        // - a date (DD-MM-YYYY, DD.MM.YYYY)
        // - a tax ID (NIP: 123-456-78-90)
        // - an address (contains "ul.", "al.", building number)
        // - a time (HH:MM)
        val datePattern = """(\d{2}[-./]\d{2}[-./]\d{2,4})""".toRegex()
        val timePattern = """(\d{2}:\d{2})""".toRegex()
        val nipPattern = """(nip|regon)""".toRegex(RegexOption.IGNORE_CASE)
        val addressPattern = """(ul\.|al\.|os\.|\d+/\d+)""".toRegex(RegexOption.IGNORE_CASE)

        for (line in lines) {
            if (line.isBlank()) continue
            if (line.length < 3) continue // Too short

            val lineLower = line.lowercase()

            // Skip lines with a date
            if (datePattern.containsMatchIn(line)) continue

            // Skip lines with a time
            if (timePattern.containsMatchIn(line)) continue

            // Skip NIP/REGON lines
            if (nipPattern.containsMatchIn(lineLower)) continue

            // Skip address lines
            if (addressPattern.containsMatchIn(lineLower)) continue

            // Skip lines containing only digits
            if (line.all { it.isDigit() || it.isWhitespace() || it in ".-/" }) continue

            // This line looks like a store name
            return line to false
        }

        return "Unknown" to false
    }

    /**
     * Extracts the total amount from receipt lines.
     * Handles deposit refunds (kaucja) by preferring the LAST "Razem" on the receipt,
     * which is the final amount after deposit deductions.
     */
    private fun extractTotalAmount(lines: List<String>): Double? {
        val text = lines.joinToString("\n").lowercase()


        // Priority 1: "DO ZAPŁATY" — most explicit final amount
        val doZaplatyIndex = text.lastIndexOf("do zapła")
        if (doZaplatyIndex != -1) {
            val zaplaconeIndex = text.indexOf("zapłacono", doZaplatyIndex)
            val searchRange = if (zaplaconeIndex != -1) {
                text.substring(doZaplatyIndex, zaplaconeIndex)
            } else {
                text.substring(doZaplatyIndex)
            }

            val amounts = extractAllNumbers(searchRange)
            val validAmount = amounts.filter { it in 0.01..9999.99 }.firstOrNull()
            if (validAmount != null) {
                return validAmount
            }
        }

        // Priority 2: "SUMA PLN" or just "SUMA" (on its own line)
        val sumaIndex = text.lastIndexOf("suma pln").let { idx ->
            if (idx != -1) idx else text.lastIndexOf("suma")
        }
        if (sumaIndex != -1) {
            val zaplaconeIndex = text.indexOf("zapłacono", sumaIndex)
            // Limit search to ~80 chars after keyword to avoid picking up unrelated numbers
            val endIndex = when {
                zaplaconeIndex != -1 -> zaplaconeIndex
                else -> minOf(text.length, sumaIndex + 80)
            }
            val searchRange = text.substring(sumaIndex, endIndex)

            val amounts = extractAllNumbers(searchRange)
            val validAmount = amounts.filter { it in 0.01..9999.99 }.firstOrNull()
            if (validAmount != null) {
                return validAmount
            }
        }

        // Priority 3: "RAZEM" — use the LAST occurrence (after deposit deductions)
        val lastRazemIndex = text.lastIndexOf("razem")
        if (lastRazemIndex != -1) {
            // Limit search to ~80 chars after "razem" to avoid picking up unrelated numbers
            val zaplaconeIndex = text.indexOf("zapłacono", lastRazemIndex)
            val endIndex = when {
                zaplaconeIndex != -1 -> zaplaconeIndex
                else -> minOf(text.length, lastRazemIndex + 80)
            }
            val searchRange = text.substring(lastRazemIndex, endIndex)

            val amounts = extractAllNumbers(searchRange)
            val validAmount = amounts.filter { it in 0.01..9999.99 }.firstOrNull()
            if (validAmount != null) {
                return validAmount
            }
        }

        // Fallback: take the largest amount before "ZAPŁACONO" (skip PTU!)
        val zaplaconeIdx = text.indexOf("zapłacono")
        val searchText = if (zaplaconeIdx != -1) {
            text.substring(0, zaplaconeIdx)
        } else {
            text
        }

        // Filter: ignore PTU (usually small numbers < 10)
        val fallback = extractAllNumbers(searchText)
            .filter { it in 10.00..9999.99 }  // Price must be >= 10 PLN (skip PTU)
            .maxOrNull()
        return fallback
    }

    /**
     * Extracts ALL numbers from text (not just the first one)
     */
    private fun extractAllNumbers(text: String): List<Double> {
        val regex = """(\d{1,3}(?:[\s.,]?\d{3})*[\s.,]\d{2})""".toRegex()
        return regex.findAll(text).mapNotNull { match ->
            val numberStr = match.value
                .replace(" ", "")
                .replace(",", ".")
            numberStr.toDoubleOrNull()
        }.toList()
    }

    /**
     * Extracts a number from a text line (handles formats: 12.34, 12,34, 1 234.56)
     */
    private fun extractNumberFromLine(line: String): Double? {
        // Regex to find a number (e.g. 123.45 or 123,45)
        val regex = """(\d{1,3}(?:[\s.,]?\d{3})*[\s.,]\d{2})""".toRegex()
        val match = regex.find(line) ?: return null

        val numberStr = match.value
            .replace(" ", "")
            .replace(",", ".")

        return numberStr.toDoubleOrNull()
    }

    /**
     * Extracts the date from receipt text and normalizes it to YYYY-MM-DD
     */
    private fun extractDate(text: String): String? {
        // Skip tax IDs (NIP) and phone numbers
        val cleanText = text.replace("""NIP\s*:?\s*[\d-]+""".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("""REGON\s*:?\s*[\d-]+""".toRegex(RegexOption.IGNORE_CASE), "")

        // DD.MM.YYYY HH:MM lub DD.MM.YYYY (np. "11.02.2026 15:56")
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
        val ddmmyy = """(\d{2})[-./](\d{2})[-./](\d{2})(?:\s|$|\s+\d{2}:\d{2})""".toRegex()
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

    /**
     * Detects category based on store name or the full receipt text
     */
    /**
     * Parses data using structured data from ML Kit
     * Uses text positions to find amounts on the same line as keywords
     */
    private fun parseReceiptDataStructured(result: Text): ReceiptData {
        val rawText = result.text
        val lines = rawText.split("\n").map { it.trim() }

        // Extract store name - smartly from the full text
        val (storeName, isKnownBrand) = detectStoreName(lines, rawText)

        // Search for amount using Y position (vertical alignment)
        val totalAmount = extractAmountUsingPosition(result)

        // Extract date
        val date = extractDate(rawText)

        // Detect category using CategoryManager
        val category = CategoryManager.categorize(storeName, rawText, context)

        // Extract NIP
        val nip = extractNip(rawText)

        // Create highlighted text (shows structure)
        val highlightedText = createHighlightedText(result, totalAmount, date)

        return ReceiptData(
            rawText = rawText,
            storeName = storeName,
            totalAmount = totalAmount,
            date = date,
            category = category,
            highlightedText = highlightedText,
            nip = nip,
            isKnownBrand = isKnownBrand
        )
    }

    /**
     * Creates a text with markers (highlights) showing the receipt structure
     */
    private fun createHighlightedText(result: Text, foundAmount: Double?, foundDate: String?): String {
        val builder = StringBuilder()

        for (block in result.textBlocks) {
            for (line in block.lines) {
                val lineY = line.boundingBox?.centerY() ?: 0
                val lineText = line.text
                val lineLower = lineText.lowercase()

                // Check if it is a keyword line
                val isKeyword = lineLower.contains("do zapł") ||
                               lineLower.contains("suma pln") ||
                               lineLower.contains("razem")

                // Check if it contains the found amount
                val containsAmount = foundAmount?.let {
                    lineText.contains("%.2f".format(it)) || lineText.contains(it.toString())
                } ?: false

                // Check if it contains the date
                val containsDate = foundDate?.let { lineText.contains(it) } ?: false

                // Format line with prefix
                when {
                    isKeyword && containsAmount -> {
                        builder.append("💰 [KWOTA+KEY] ")
                        builder.append(lineText)
                        builder.append(" (Y=$lineY)")
                    }
                    isKeyword -> {
                        builder.append("🔑 [KEYWORD]   ")
                        builder.append(lineText)
                        builder.append(" (Y=$lineY)")
                    }
                    containsAmount -> {
                        builder.append("💵 [AMOUNT]    ")
                        builder.append(lineText)
                        builder.append(" (Y=$lineY)")
                    }
                    containsDate -> {
                        builder.append("📅 [DATE]      ")
                        builder.append(lineText)
                    }
                    else -> {
                        builder.append("   ")
                        builder.append(lineText)
                    }
                }
                builder.appendLine()
            }
        }

        return builder.toString()
    }

    /**
     * Extracts the amount using Y and X positions (right-aligned amounts) + confidence scoring
     */
    private fun extractAmountUsingPosition(result: Text): Double? {
        val imageWidth = result.textBlocks.maxOfOrNull {
            it.boundingBox?.right ?: 0
        } ?: 1000

        // Find all elements with numbers + their positions
        data class NumberCandidate(
            val element: Text.Element,
            val amount: Double,
            val x: Int,
            val y: Int,
            val isRightAligned: Boolean
        )

        val numberElements = mutableListOf<NumberCandidate>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val number = extractNumberFromLine(element.text)
                    if (number != null && number in 0.01..9999.99) {
                        val box = element.boundingBox
                        if (box != null) {
                            val x = box.centerX()
                            val y = box.centerY()
                            // Right-aligned = on the right side (>60% of the width)
                            val isRightAligned = x > (imageWidth * 0.6)

                            numberElements.add(
                                NumberCandidate(element, number, x, y, isRightAligned)
                            )
                        }
                    }
                }
            }
        }

        // Find "DO ZAPŁATY", "SUMA PLN", "RAZEM" keywords with their Y positions.
        // For "RAZEM" prefer the LAST (lowest on page = highest Y) occurrence
        // to handle deposit refunds (kaucja) where the final total appears after deductions.
        val highPriorityKeywords = listOf("do zapł", "suma pln")
        val yTolerance = 100

        data class KeywordHit(val keyword: String, val y: Int, val priority: Int)

        data class AmountMatch(
            val amount: Double,
            val confidence: Int  // 0-100
        )

        val keywordHits = mutableListOf<KeywordHit>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.lowercase()
                val lineY = line.boundingBox?.centerY() ?: continue

                for (kw in highPriorityKeywords) {
                    if (lineText.contains(kw)) {
                        keywordHits.add(KeywordHit(kw, lineY, priority = 2))
                    }
                }
                if (lineText.contains("razem")) {
                    keywordHits.add(KeywordHit("razem", lineY, priority = 1))
                }
            }
        }

        // For "razem" keep only the last (highest Y) occurrence
        val lastRazemY = keywordHits.filter { it.keyword == "razem" }.maxByOrNull { it.y }
        val filteredHits = keywordHits.filter { it.keyword != "razem" } +
                listOfNotNull(lastRazemY?.copy(priority = 1))


        val matches = mutableListOf<AmountMatch>()

        for (hit in filteredHits) {
            for (candidate in numberElements) {
                val yDiff = abs(candidate.y - hit.y)

                if (yDiff < yTolerance) {
                    var confidence = 0

                    // +40 for right-aligned
                    if (candidate.isRightAligned) confidence += 40

                    // +30 for small Y difference
                    val yScore = ((yTolerance - yDiff) * 30 / yTolerance).toInt()
                    confidence += yScore

                    // +20 for reasonable amount
                    if (candidate.amount in 10.0..1000.0) confidence += 20

                    // +10 bonus for high-priority keywords (DO ZAPŁATY > RAZEM)
                    if (hit.priority == 2) confidence += 10

                    matches.add(AmountMatch(candidate.amount, confidence))
                }
            }
        }

        // Take the amount with the highest confidence
        val bestMatch = matches.maxByOrNull { it.confidence }
        if (bestMatch != null && bestMatch.confidence >= 50) {
            return bestMatch.amount
        }

        // Fallback: take the largest right-aligned amount
        val fallbackAmount = numberElements
            .filter { it.isRightAligned && it.amount >= 10.0 }
            .maxByOrNull { it.amount }

        if (fallbackAmount != null) {
            return fallbackAmount.amount
        }

        // Last resort: plain parsing
        return extractTotalAmount(result.text.split("\n").map { it.trim() })
    }

    /**
     * Extracts a NIP (tax ID) from receipt text.
     */
    private fun extractNip(text: String): String? {
        // Normalize common OCR misreads before matching
        val normalized = text
            .replace(Regex("""(?<=[0-9])O(?=[0-9])"""), "0")  // O→0 between digits
            .replace(Regex("""(?<=[0-9])[lI](?=[0-9])"""), "1") // l/I→1 between digits

        // 1. Standard: NIP keyword followed by digits (handles OCR variants like NlP, N1P)
        val nipKeywordRegex = """N[Il1i][Pp][:\s.-]*(?:PL)?[:\s]*(\d{3}[-\s.]*\d{2,3}[-\s.]*\d{2,3}[-\s.]*\d{2,3})""".toRegex(RegexOption.IGNORE_CASE)
        nipKeywordRegex.find(normalized)?.let { match ->
            val nip = match.groupValues[1].replace("""[-\s.]""".toRegex(), "")
            if (nip.length == 10) {
                return nip
            }
        }

        // 2. Continuous 10 digits after NIP keyword
        val nipContinuousRegex = """N[Il1i][Pp][:\s.-]*(?:PL)?[:\s]*(\d{10})""".toRegex(RegexOption.IGNORE_CASE)
        nipContinuousRegex.find(normalized)?.let { match ->
            val nip = match.groupValues[1]
            return nip
        }

        // 3. Fallback: 10-digit sequence in XXX-XXX-XX-XX or XXX-XX-XX-XXX format (without NIP keyword)
        val nipFormatRegex = """(\d{3}[-]\d{3}[-]\d{2}[-]\d{2}|\d{3}[-]\d{2}[-]\d{2}[-]\d{3})""".toRegex()
        nipFormatRegex.find(normalized)?.let { match ->
            val nip = match.groupValues[1].replace("-", "")
            if (nip.length == 10 && isValidNip(nip)) {
                return nip
            }
        }

        // 4. Last resort: any standalone 10-digit number validated by NIP checksum
        val standaloneDigitsRegex = """(?<!\d)(\d{10})(?!\d)""".toRegex()
        for (match in standaloneDigitsRegex.findAll(normalized)) {
            val candidate = match.groupValues[1]
            if (isValidNip(candidate)) {
                return candidate
            }
        }

        // Log context around "nip" if present but no match
        val nipIdx = text.lowercase().indexOf("nip")
        if (nipIdx != -1) {
            val context = text.substring(nipIdx, minOf(text.length, nipIdx + 40))
        } else {
        }
        return null
    }

    /**
     * Validates NIP checksum. Weights: 6,5,7,2,3,4,5,6,7.
     * Sum of (digit[i] * weight[i]) mod 11 must equal digit[9].
     */
    private fun isValidNip(nip: String): Boolean {
        if (nip.length != 10 || !nip.all { it.isDigit() }) return false
        val weights = intArrayOf(6, 5, 7, 2, 3, 4, 5, 6, 7)
        val sum = (0 until 9).sumOf { nip[it].digitToInt() * weights[it] }
        return sum % 11 == nip[9].digitToInt()
    }

    /**
     * Releases ML Kit resources
     */
    fun close() {
        recognizer.close()
    }
}

/**
 * Data class storing parsed receipt data
 */
data class ReceiptData(
    val rawText: String,
    val storeName: String,
    val totalAmount: Double?,
    val date: String?,
    val category: ExpenseCategory,
    val highlightedText: String = rawText,
    val nip: String? = null,
    val isKnownBrand: Boolean = false
)
