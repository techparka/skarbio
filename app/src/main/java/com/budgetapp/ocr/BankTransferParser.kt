package com.budgetapp.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.util.LocaleDataLoader

/**
 * Parser for bank transfer confirmations (any Polish bank).
 * Extracts recipient, amount, date, title, reference, and recipient account
 * from PDF text and maps them to InvoiceData fields.
 */
class BankTransferParser(private val context: Context) {

    private val pdfParser = PdfReceiptParser(context)

    fun parseTransfer(uri: Uri): InvoiceData? {
        val text = pdfParser.extractText(uri)
        if (text == null || text.trim().length < 30) return null


        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        val recipient = extractRecipient(lines)
        val amount = extractAmount(lines)
        val executionDate = extractExecutionDate(lines)
        val title = extractTitle(lines)
        val reference = extractReference(lines)
        val recipientAccount = extractRecipientAccount(lines, text)
        val category = categorizeByTitle(title, recipient)

        // Use reference as invoiceNumber if available, otherwise title
        val invoiceNumber = reference.ifBlank { title }

        return InvoiceData(
            vendorName = recipient,
            invoiceNumber = invoiceNumber,
            amount = amount,
            dueDate = executionDate,
            issueDate = executionDate,
            bankAccount = recipientAccount,
            nip = null,
            category = category,
            rawText = text
        )
    }

    fun renderPagesToBitmap(uri: Uri): Bitmap? {
        return pdfParser.renderPagesToBitmap(uri)
    }

    // -- Recipient extraction --

    private fun extractRecipient(lines: List<String>): String {
        val keywords = listOf(
            "nazwa odbiorcy", "odbiorca", "beneficjent", "na rzecz",
            "odbiorca przelewu", "dane odbiorcy"
        )
        extractValueAfterKeyword(lines, keywords)?.let { return it }
        return context.getString(R.string.unknown_vendor)
    }

    // -- Amount extraction --

    private fun extractAmount(lines: List<String>): Double? {
        val keywords = listOf(
            "kwota przelewu", "kwota operacji", "kwota transakcji",
            "kwota", "wartość", "wartosc", "amount"
        )

        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            for (keyword in keywords) {
                if (lineLower.contains(keyword)) {
                    // Try same line first
                    extractAmountFromLine(lines[i])?.let { return it }
                    // Try next line
                    if (i + 1 < lines.size) {
                        extractAmountFromLine(lines[i + 1])?.let { return it }
                    }
                }
            }
        }

        return null
    }

    private fun extractAmountFromLine(line: String): Double? {
        // Remove dates before amount extraction
        val cleanedLine = line
            .replace("""\d{2}\.\d{2}\.\d{4}""".toRegex(), " ")
            .replace("""\d{4}-\d{2}-\d{2}""".toRegex(), " ")

        val regex = """(\d{1,3}(?:[\s ]?\d{3})*[,\.]\d{2})""".toRegex()
        val matches = regex.findAll(cleanedLine).toList()
        return matches.lastOrNull()?.let {
            it.value.replace(" ", "").replace(",", ".").toDoubleOrNull()
        }
    }

    // -- Execution date extraction --

    private fun extractExecutionDate(lines: List<String>): String? {
        val keywords = listOf(
            "data operacji", "data wykonania", "data realizacji",
            "data przelewu", "data transakcji", "data księgowania",
            "data ksiegowania", "data zlecenia"
        )

        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            for (keyword in keywords) {
                if (lineLower.contains(keyword)) {
                    extractDateFromLine(lines[i])?.let { return it }
                    if (i + 1 < lines.size) {
                        extractDateFromLine(lines[i + 1])?.let { return it }
                    }
                }
            }
        }

        // Fallback: first date found in the document
        for (line in lines) {
            extractDateFromLine(line)?.let { return it }
        }

        return null
    }

    private fun extractDateFromLine(line: String): String? {
        // DD.MM.YYYY or DD-MM-YYYY or DD/MM/YYYY
        val ddmmyyyy = """(\d{2})[-./](\d{2})[-./](\d{4})""".toRegex()
        ddmmyyyy.find(line)?.let { match ->
            val (d, m, y) = match.destructured
            val day = d.toIntOrNull() ?: return@let
            val month = m.toIntOrNull() ?: return@let
            val year = y.toIntOrNull() ?: return@let
            if (day in 1..31 && month in 1..12 && year in 2020..2030) {
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }

        // YYYY-MM-DD
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

    // -- Transfer title extraction --

    private fun extractTitle(lines: List<String>): String {
        val keywords = listOf(
            "tytuł przelewu", "tytul przelewu",
            "tytuł", "tytul", "tytułem", "tytulem",
            "opis operacji", "szczegóły", "szczegoly"
        )

        // Keywords that signal a DIFFERENT section (stop collecting title lines)
        val stopKeywords = listOf(
            "numer referencyjny", "nr ref", "identyfikator",
            "data", "kwota", "odbiorca", "nadawca", "rachunek",
            "saldo", "prowizja", "opłata"
        )

        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            for (keyword in keywords) {
                if (lineLower.contains(keyword)) {
                    // Value after colon on the same line
                    val afterColon = extractAfterColon(lines[i])
                    if (afterColon.isNotBlank()) {
                        // Concatenate up to 2 more lines
                        val parts = mutableListOf(afterColon)
                        for (offset in 1..2) {
                            if (i + offset < lines.size) {
                                val nextLower = lines[i + offset].lowercase()
                                if (stopKeywords.any { nextLower.contains(it) }) break
                                if (lines[i + offset].contains(":")) break
                                parts.add(lines[i + offset])
                            }
                        }
                        return parts.joinToString(" ").trim()
                    }

                    // Try next line
                    if (i + 1 < lines.size) {
                        val parts = mutableListOf(lines[i + 1])
                        for (offset in 2..3) {
                            if (i + offset < lines.size) {
                                val nextLower = lines[i + offset].lowercase()
                                if (stopKeywords.any { nextLower.contains(it) }) break
                                if (lines[i + offset].contains(":")) break
                                parts.add(lines[i + offset])
                            }
                        }
                        return parts.joinToString(" ").trim()
                    }
                }
            }
        }

        return ""
    }

    // -- Reference number extraction --

    private fun extractReference(lines: List<String>): String {
        val keywords = listOf(
            "numer referencyjny", "nr referencyjny", "nr ref",
            "identyfikator", "id transakcji", "id operacji"
        )
        extractValueAfterKeyword(lines, keywords)?.let { return it }
        return ""
    }

    // -- Recipient account extraction (IBAN) --

    private fun extractRecipientAccount(lines: List<String>, fullText: String): String? {
        val ibanRegex = """(\d{2}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4})""".toRegex()

        // Prefer IBAN near "rachunek odbiorcy" / "konto odbiorcy"
        val recipientKeywords = listOf("rachunek odbiorcy", "konto odbiorcy", "nr rachunku odbiorcy")
        val senderKeywords = listOf("rachunek nadawcy", "konto nadawcy", "rachunek zleceniodawcy")

        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            if (recipientKeywords.any { lineLower.contains(it) }) {
                // Check same line
                ibanRegex.find(lines[i])?.let { match ->
                    val cleaned = match.value.replace("""[\s-]""".toRegex(), "")
                    if (cleaned.length == 26) return cleaned
                }
                // Check next line
                if (i + 1 < lines.size) {
                    ibanRegex.find(lines[i + 1])?.let { match ->
                        val cleaned = match.value.replace("""[\s-]""".toRegex(), "")
                        if (cleaned.length == 26) return cleaned
                    }
                }
            }
        }

        // Fallback: find all IBANs, exclude those near sender keywords
        val allIbans = mutableListOf<String>()
        val senderIbans = mutableSetOf<String>()

        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            val isSender = senderKeywords.any { lineLower.contains(it) }

            ibanRegex.find(lines[i])?.let { match ->
                val cleaned = match.value.replace("""[\s-]""".toRegex(), "")
                if (cleaned.length == 26) {
                    if (isSender) senderIbans.add(cleaned) else allIbans.add(cleaned)
                }
            }
            // Also check next line for sender context
            if (isSender && i + 1 < lines.size) {
                ibanRegex.find(lines[i + 1])?.let { match ->
                    val cleaned = match.value.replace("""[\s-]""".toRegex(), "")
                    if (cleaned.length == 26) senderIbans.add(cleaned)
                }
            }
        }

        // Return first non-sender IBAN, or fallback to any IBAN from full text
        val recipientIban = allIbans.firstOrNull { it !in senderIbans }
        if (recipientIban != null) return recipientIban

        // Last resort: first IBAN in full text
        ibanRegex.find(fullText)?.let { match ->
            val cleaned = match.value.replace("""[\s-]""".toRegex(), "")
            if (cleaned.length == 26) return cleaned
        }

        return null
    }

    // -- Category detection by title/recipient --

    private fun categorizeByTitle(title: String, recipient: String): String {
        val combined = "$title $recipient".lowercase()

        // Check against known invoice vendors
        for ((key, name) in LocaleDataLoader.loadInvoiceKeywords(context)) {
            val pattern = """\b${Regex.escape(key)}\b""".toRegex()
            if (pattern.containsMatchIn(combined)) {
                return categorizeVendorName(name)
            }
        }

        // Check against known OCR store keywords
        for ((key, _) in LocaleDataLoader.loadOcrKeywords(context)) {
            val pattern = """\b${Regex.escape(key)}\b""".toRegex()
            if (pattern.containsMatchIn(combined)) {
                return CategoryKey.OTHER_BILL.name
            }
        }

        return CategoryKey.OTHER_BILL.name
    }

    private fun categorizeVendorName(vendorName: String): String {
        return when (vendorName) {
            "PLAY", "Orange", "T-Mobile", "Plus", "Cyfrowy Polsat", "Canal+" -> CategoryKey.TELECOM.name
            "TAURON", "PGE", "Enea", "Energa", "Innogy", "E.ON" -> CategoryKey.ENERGY.name
            "PGNiG" -> CategoryKey.GAS.name
            "MPWiK", "Wodociągi" -> CategoryKey.WATER.name
            "Vectra", "UPC", "Netia", "Inea", "Multimedia" -> CategoryKey.INTERNET.name
            else -> CategoryKey.OTHER_BILL.name
        }
    }

    // -- Helpers --

    /** Extracts value after the first matching keyword, looking after colon or on next line. */
    private fun extractValueAfterKeyword(lines: List<String>, keywords: List<String>): String? {
        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            for (keyword in keywords) {
                if (lineLower.contains(keyword)) {
                    val afterColon = extractAfterColon(lines[i])
                    if (afterColon.isNotBlank()) return afterColon.trim()
                    if (i + 1 < lines.size && lines[i + 1].isNotBlank()) {
                        return lines[i + 1].trim()
                    }
                }
            }
        }
        return null
    }

    private fun extractAfterColon(line: String): String {
        val colonIndex = line.indexOf(':')
        return if (colonIndex >= 0 && colonIndex + 1 < line.length) {
            line.substring(colonIndex + 1).trim()
        } else ""
    }
}
