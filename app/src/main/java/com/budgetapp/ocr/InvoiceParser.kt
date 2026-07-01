package com.budgetapp.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.util.LocaleDataLoader

/**
 * Structured warning codes for invoice age checks.
 * Resolved to localized text at the UI layer via string resources.
 */
sealed class InvoiceWarning {
    data class OlderThanYear(val daysOld: Long) : InvoiceWarning()
    data class OlderThanMonth(val daysOld: Long) : InvoiceWarning()
    data class RecentlyOld(val dateStr: String, val daysOld: Long) : InvoiceWarning()
    data class DueDatePassed(val daysOld: Long) : InvoiceWarning()

    fun toLocalizedString(context: Context): String = when (this) {
        is OlderThanYear -> context.getString(R.string.warning_invoice_older_than_year, daysOld)
        is OlderThanMonth -> context.getString(R.string.warning_invoice_older_than_month, daysOld)
        is RecentlyOld -> context.getString(R.string.warning_invoice_old, dateStr, daysOld)
        is DueDatePassed -> context.getString(R.string.warning_due_date_passed, daysOld)
    }
}

/**
 * Data extracted from an invoice.
 */
data class InvoiceData(
    val vendorName: String,
    val invoiceNumber: String,
    val amount: Double?,
    val dueDate: String?,
    val issueDate: String?,
    val bankAccount: String?,
    val nip: String?,
    val category: String,
    val rawText: String,
    val forecasts: List<ForecastEntry> = emptyList(),
    val warningMessage: String? = null,
    val invoiceWarning: InvoiceWarning? = null
)

data class ForecastEntry(
    val amount: Double,
    val dueDate: String,
    val description: String = ""
)

/**
 * Parser for PDF invoices (PLAY, TAURON, PGE, Orange, etc.)
 * Extracts payment amount, due date, invoice number, vendor, bank account.
 */
class InvoiceParser(private val context: Context) {

    private val pdfParser = PdfReceiptParser(context)

    /** Lines containing these words are historical, not current — skip them in amount extraction */
    private val excludePatterns = listOf("poprzedni")

    fun parseInvoice(uri: Uri): InvoiceData? {
        val text = pdfParser.extractText(uri)
        if (text == null || text.trim().length < 50) {
            return null
        }


        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        val vendorName = detectVendor(text)
        val invoiceNumber = extractInvoiceNumber(lines, text)
        val amount = extractPaymentAmount(lines)
        val dueDate = extractDueDate(lines, text)
        val issueDate = extractIssueDate(lines, text)
        val bankAccount = extractBankAccount(text)
        val nip = extractNip(text)
        val category = categorizeVendor(vendorName)
        val forecasts = extractForecasts(lines, text, vendorName)

        // Check if invoice is old (>30 days)
        val invoiceWarning = checkOldInvoice(issueDate, dueDate)
        val warningMessage = invoiceWarning?.toLocalizedString(context)

        return InvoiceData(
            vendorName = vendorName,
            invoiceNumber = invoiceNumber,
            amount = amount,
            dueDate = dueDate,
            issueDate = issueDate,
            bankAccount = bankAccount,
            nip = nip,
            category = category,
            rawText = text,
            forecasts = forecasts,
            warningMessage = warningMessage,
            invoiceWarning = invoiceWarning
        )
    }

    fun parseInvoiceFromText(text: String): InvoiceData? {
        if (text.trim().length < 20) return null

        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        val vendorName = detectVendor(text)
        val invoiceNumber = extractInvoiceNumber(lines, text)
        val amount = extractPaymentAmount(lines)
        val dueDate = extractDueDate(lines, text)
        val issueDate = extractIssueDate(lines, text)
        val bankAccount = extractBankAccount(text)
        val nip = extractNip(text)
        val category = categorizeVendor(vendorName)
        val forecasts = extractForecasts(lines, text, vendorName)

        val invoiceWarning = checkOldInvoice(issueDate, dueDate)
        val warningMessage = invoiceWarning?.toLocalizedString(context)

        return InvoiceData(
            vendorName = vendorName,
            invoiceNumber = invoiceNumber,
            amount = amount,
            dueDate = dueDate,
            issueDate = issueDate,
            bankAccount = bankAccount,
            nip = nip,
            category = category,
            rawText = text,
            forecasts = forecasts,
            warningMessage = warningMessage,
            invoiceWarning = invoiceWarning
        )
    }

    fun renderPagesToBitmap(uri: Uri): Bitmap? {
        return pdfParser.renderPagesToBitmap(uri)
    }

    // -- Old invoice check --

    private fun checkOldInvoice(issueDate: String?, dueDate: String?): InvoiceWarning? {
        val now = java.time.LocalDate.now()
        val warningThresholdDays = 30L

        // Check issue date
        issueDate?.let { dateStr ->
            try {
                val date = java.time.LocalDate.parse(dateStr)
                val daysOld = java.time.temporal.ChronoUnit.DAYS.between(date, now)

                if (daysOld > warningThresholdDays) {
                    val monthsOld = daysOld / 30
                    return when {
                        monthsOld > 12 -> InvoiceWarning.OlderThanYear(daysOld)
                        monthsOld > 1 -> InvoiceWarning.OlderThanMonth(daysOld)
                        else -> InvoiceWarning.RecentlyOld(dateStr, daysOld)
                    }
                }
            } catch (_: Exception) { }
        }

        // Check due date (fallback if no issue date)
        if (issueDate == null && dueDate != null) {
            try {
                val date = java.time.LocalDate.parse(dueDate)
                val daysOld = java.time.temporal.ChronoUnit.DAYS.between(date, now)

                if (daysOld > warningThresholdDays) {
                    return InvoiceWarning.DueDatePassed(daysOld)
                }
            } catch (_: Exception) { }
        }

        return null
    }

    // -- Vendor detection --

    private fun detectVendor(text: String): String {
        val textLower = text.lowercase()
        for ((key, name) in LocaleDataLoader.loadInvoiceKeywords(context)) {
            val pattern = """\b${Regex.escape(key)}\b""".toRegex()
            if (pattern.containsMatchIn(textLower)) {
                return name
            }
        }
        return context.getString(R.string.unknown_vendor)
    }

    // -- Invoice number extraction --

    private fun extractInvoiceNumber(lines: List<String>, fullText: String): String {
        val keywords = listOf(
            "numer faktury", "nr faktury", "faktura nr", "faktura",
            "nr dokumentu", "numer dokumentu", "dokument nr",
            "numer", "nr."
        )

        for (line in lines) {
            val lineLower = line.lowercase()
            for (keyword in keywords) {
                if (lineLower.contains(keyword)) {
                    val afterKeyword = substringAfterIgnoreCase(line, keyword).trim()

                    val fvPattern = """[A-Z]{1,3}/[\d/\-]+""".toRegex()
                    fvPattern.find(afterKeyword)?.let { return it.value }

                    val longNumberPattern = """\d{9,}""".toRegex()
                    longNumberPattern.find(afterKeyword)?.let { return it.value }

                    val invoiceRegex = """[A-Z0-9/\-]{5,}""".toRegex()
                    invoiceRegex.find(afterKeyword)?.let { return it.value }
                    invoiceRegex.find(line)?.let { return it.value }
                }
            }
        }

        // Fallback: search patterns in full text
        val invoicePattern = """[A-Z]/[A-Z0-9/]+/\d{2}/\d{2}""".toRegex()
        invoicePattern.find(fullText)?.let { return it.value }

        val fvPattern = """FV[A-Z]*/[\d/]+""".toRegex()
        fvPattern.find(fullText)?.let { return it.value }

        val fsPattern = """FS/[\d/]+""".toRegex()
        fsPattern.find(fullText)?.let { return it.value }

        val simplePattern = """[A-Z]{1,3}/\w+/\d+""".toRegex()
        simplePattern.find(fullText)?.let { return it.value }

        return ""
    }

    private fun substringAfterIgnoreCase(text: String, delimiter: String): String {
        val index = text.lowercase().indexOf(delimiter.lowercase())
        return if (index >= 0) text.substring(index + delimiter.length) else text
    }

    // -- Payment amount extraction --

    private fun extractPaymentAmount(lines: List<String>): Double? {
        val highPriorityKeywords = listOf(
            "kwota do zapłaty z uwzględnieniem", "kwota do zaplaty z uwzglednieniem",
            "razem do zapłaty", "razem do zaplaty",
            "suma do zapłaty", "suma do zaplaty",
            "łącznie do zapłaty", "lacznie do zaplaty",
            "należność całkowita", "naleznosc calkowita"
        )

        // More specific keywords first — "kwota do zapłaty" before generic "wartość faktury"
        val mediumPriorityKeywords = listOf(
            "kwota do zapłaty", "kwota do zaplaty",
            "do zapłaty", "do zaplaty",
            "prosimy o wpłatę", "prosimy o wplate",
            "wartość faktury", "wartosc faktury",
            "należność", "naleznosc"
        )

        // HIGH priority: take the maximum (these keywords always denote the grand total)
        val highPriorityAmounts = mutableListOf<Pair<Double, String>>()
        for (line in lines) {
            val lineLower = line.lowercase()
            if (excludePatterns.any { lineLower.contains(it) }) continue
            for (keyword in highPriorityKeywords) {
                if (lineLower.contains(keyword)) {
                    val amount = extractAmountFromLine(line)
                    if (amount != null && amount in 0.01..999999.99) {
                        highPriorityAmounts.add(amount to keyword)
                    }
                }
            }
        }

        if (highPriorityAmounts.isNotEmpty()) {
            val maxAmount = highPriorityAmounts.maxByOrNull { it.first }!!
            return maxAmount.first
        }

        // MEDIUM priority: take the FIRST occurrence (top-to-bottom, keyword priority order).
        // Multi-period invoices (PGE, some TAURON) list each period's "kwota do zapłaty"
        // separately; the first one is always the current billing period.
        for (line in lines) {
            val lineLower = line.lowercase()
            if (excludePatterns.any { lineLower.contains(it) }) continue
            for (keyword in mediumPriorityKeywords) {
                if (lineLower.contains(keyword)) {
                    val amount = extractAmountFromLine(line)
                    if (amount != null && amount in 0.01..999999.99) {
                        return amount
                    }
                }
            }
        }

        // Search next line after keyword
        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            if (excludePatterns.any { lineLower.contains(it) }) continue
            for (keyword in highPriorityKeywords + mediumPriorityKeywords) {
                if (lineLower.contains(keyword) && i + 1 < lines.size) {
                    val amount = extractAmountFromLine(lines[i + 1])
                    if (amount != null && amount in 0.01..999999.99) {
                        return amount
                    }
                }
            }
        }

        // FALLBACK: largest amount in entire invoice
        val allAmounts = lines.mapNotNull { extractAmountFromLine(it) }
            .filter { it in 10.0..999999.99 }

        if (allAmounts.isNotEmpty()) {
            val maxAmount = allAmounts.maxOrNull()!!
            return maxAmount
        }

        return null
    }

    private fun extractAmountFromLine(line: String): Double? {
        // Remove dates from line BEFORE amount extraction
        val cleanedLine = line
            .replace("""\d{2}/\d{2}/\d{4}""".toRegex(), " ")   // DD/MM/YYYY
            .replace("""\d{2}\.\d{2}\.\d{4}""".toRegex(), " ") // DD.MM.YYYY
            .replace("""\d{2}-\d{2}-\d{4}""".toRegex(), " ")   // DD-MM-YYYY
            .replace("""\d{4}-\d{2}-\d{2}""".toRegex(), " ")   // YYYY-MM-DD
            .replace("""\d{4}\.\d{2}\.\d{2}""".toRegex(), " ") // YYYY.MM.DD (PGE: 2025.08.29)

        val regex = """(\d{1,3}(?:[\s ]?\d{3})*[,\.]\d{2})""".toRegex()
        val matches = regex.findAll(cleanedLine).toList()
        return matches.lastOrNull()?.let {
            it.value.replace(" ", "").replace(",", ".").toDoubleOrNull()
        }
    }

    // -- Due date extraction --

    private fun extractDueDate(lines: List<String>, @Suppress("UNUSED_PARAMETER") fullText: String): String? {
        val keywords = listOf(
            "termin płatności", "termin platnosci",
            "termin płatn", "termin platn",
            "płatne do", "platne do",
            "zapłacić do", "zaplacic do",
            "data płatności", "data platnosci"
        )

        for (line in lines) {
            val lineLower = line.lowercase()
            for (keyword in keywords) {
                if (lineLower.contains(keyword)) {
                    val date = extractDateFromLine(line)
                    if (date != null) return date
                }
            }
        }

        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            for (keyword in keywords) {
                if (lineLower.contains(keyword) && i + 1 < lines.size) {
                    val date = extractDateFromLine(lines[i + 1])
                    if (date != null) return date
                }
            }
        }

        return null
    }

    // -- Issue date extraction --

    private fun extractIssueDate(lines: List<String>, @Suppress("UNUSED_PARAMETER") fullText: String): String? {
        val keywords = listOf(
            "data wystawienia", "wystawion",
            "data sprzedaży", "data sprzedazy"
        )

        for (line in lines) {
            val lineLower = line.lowercase()
            for (keyword in keywords) {
                if (lineLower.contains(keyword)) {
                    val date = extractDateFromLine(line)
                    if (date != null) return date
                }
            }
        }

        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            for (keyword in keywords) {
                if (lineLower.contains(keyword) && i + 1 < lines.size) {
                    val date = extractDateFromLine(lines[i + 1])
                    if (date != null) return date
                }
            }
        }

        return null
    }

    private fun extractDateFromLine(line: String): String? {
        val ddmmyyyyPattern = """(\d{2})[-./](\d{2})[-./](\d{4})""".toRegex()
        ddmmyyyyPattern.find(line)?.let { match ->
            val (d, m, y) = match.destructured
            val day = d.toIntOrNull() ?: return@let
            val month = m.toIntOrNull() ?: return@let
            val year = y.toIntOrNull() ?: return@let
            if (day in 1..31 && month in 1..12 && year in 2020..2030) {
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }

        val iso = """(\d{4})[-./](\d{2})[-./](\d{2})""".toRegex()
        iso.find(line)?.let { match ->
            val (y, m, d) = match.destructured
            val year = y.toIntOrNull() ?: return@let
            val month = m.toIntOrNull() ?: return@let
            val day = d.toIntOrNull() ?: return@let
            if (day in 1..31 && month in 1..12 && year in 2020..2030) {
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }

        return null
    }

    // -- Bank account extraction --

    private fun extractBankAccount(text: String): String? {
        val ibanRegex = """(\d{2}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4})""".toRegex()
        ibanRegex.find(text)?.let { match ->
            val cleaned = match.value.replace("""[\s-]""".toRegex(), "")
            if (cleaned.length == 26) {
                return cleaned
            }
        }
        return null
    }

    // -- NIP extraction --

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

    // -- Vendor categorization (returns CategoryKey name) --

    private fun categorizeVendor(vendorName: String): String {
        return when (vendorName) {
            "PLAY", "Orange", "T-Mobile", "Plus", "Cyfrowy Polsat", "Canal+" -> CategoryKey.TELECOM.name
            "TAURON", "PGE", "Enea", "Energa", "Innogy", "E.ON" -> CategoryKey.ENERGY.name
            "PGNiG" -> CategoryKey.GAS.name
            "MPWiK", "Wodociągi" -> CategoryKey.WATER.name
            "Vectra", "UPC", "Netia", "Inea", "Multimedia" -> CategoryKey.INTERNET.name
            else -> CategoryKey.OTHER_BILL.name
        }
    }

    // -- Forecast extraction (TAURON, PGE, Enea) --

    private fun extractForecasts(lines: List<String>, fullText: String, vendorName: String): List<ForecastEntry> {

        val supportedVendors = listOf("tauron", "pge", "enea", "energa")
        val vendorLower = vendorName.lowercase()
        val isSupported = supportedVendors.any { vendorLower.contains(it) }

        if (!isSupported) {
            return emptyList()
        }

        val forecasts = mutableListOf<ForecastEntry>()

        val forecastKeywords = listOf(
            "prognoza", "rata", "płatność", "platnosc",
            "harmonogram", "plan płatności", "plan platnosci",
            "kolejne", "następne", "nastepne",
            "terminy", "raty"
        )
        val textLower = fullText.lowercase()

        val foundKeywords = forecastKeywords.filter { textLower.contains(it) }

        if (foundKeywords.isEmpty()) {
            return emptyList()
        }

        var inForecastSection = false
        val sectionKeywords = listOf(
            "prognoza", "kolejn", "harmonogram",
            "plan płatności", "plan platnosci",
            "terminy płatności", "terminy platnosci",
            "następne", "nastepne", "raty"
        )

        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()

            if (!inForecastSection && sectionKeywords.any { lineLower.contains(it) }) {
                if (com.budgetapp.ocr.BuildConfig.DEBUG) {
                    for (j in i+1 until minOf(i+51, lines.size)) {
                    }
                }
                inForecastSection = true
                continue
            }

            if (inForecastSection) {
                val endKeywords = listOf(
                    "łączna kwota", "lacznie", "łącznie",
                    "sposob", "zapłać",
                    "masz niedopłat", "masz nadpłat",
                    "możesz zapłacić", "adresat",
                    "data nadania", "podsumowanie faktury",
                    // PGE: detailed metering section headers
                    "szczegółowe rozliczenie",
                    "identyfikator miejsca poboru",
                    "data odczytu",
                    "liczydło",
                    "poprzedni rozliczeniowy"
                )
                if (endKeywords.any { lineLower.contains(it) }) {
                    break
                }

                val headerPatterns = listOf(
                    "na okres",
                    "od \\d{2}[/.-]\\d{2}[/.-]\\d{4} do",      // DD.MM.YYYY range
                    "od \\d{4}[/.-]\\d{2}[/.-]\\d{2} do",      // YYYY.MM.DD range (PGE)
                    "harmonogram płatności od", "szczegółowy harmonogram",
                    "lp\\.", "pozycja", "termin płatności", "kwota do zapłaty",
                    "wybierz metod", "zapłać przelewem", "numer konta",
                    "tytuł płatności",
                    "składowe faktury", "wartość netto"           // PGE table headers
                )
                if (headerPatterns.any { pattern -> pattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(lineLower) }) {
                    continue
                }

                var amount = extractAmountFromLine(lines[i])
                var date = extractDateFromLine(lines[i])

                if ((amount != null && date == null) || (amount == null && date != null)) {
                    for (offset in 1..3) {
                        if (i + offset < lines.size) {
                            if (date == null) {
                                date = extractDateFromLine(lines[i + offset])
                            }
                            if (amount == null) {
                                amount = extractAmountFromLine(lines[i + offset])
                            }
                            if (amount != null && date != null) break
                        }
                    }
                }

                if (amount != null || date != null) {
                }

                if (amount != null && date != null && amount >= 50.0 && amount <= 99999.99) {
                    val isDuplicate = forecasts.any {
                        it.amount == amount && it.dueDate == date
                    }
                    if (isDuplicate) {
                    } else {
                        forecasts.add(
                            ForecastEntry(
                                amount = amount,
                                dueDate = date,
                                description = context.getString(R.string.forecast_label)
                            )
                        )
                    }
                }

                if (forecasts.size >= 6) break
            }
        }

        return forecasts
    }
}
