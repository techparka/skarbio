package com.budgetapp.ocr.export

import android.content.Context
import android.net.Uri
import com.budgetapp.ocr.data.local.entity.BillEntity
import com.budgetapp.ocr.data.local.entity.BillStatus
import com.budgetapp.ocr.data.local.entity.TransactionEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.repository.AccountRepository
import com.budgetapp.ocr.data.repository.BillRepository
import com.budgetapp.ocr.data.repository.TransactionRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val errors: List<String>
)

@Singleton
class CsvImporter @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val billRepository: BillRepository,
    private val accountRepository: AccountRepository
) {

    suspend fun import(uri: Uri, context: Context): ImportResult {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ImportResult(0, 0, listOf("Cannot open file"))

        // Strip UTF-8 BOM if present
        val text = if (raw.size >= 3
            && raw[0] == 0xEF.toByte()
            && raw[1] == 0xBB.toByte()
            && raw[2] == 0xBF.toByte()
        ) {
            String(raw, 3, raw.size - 3, Charsets.UTF_8)
        } else {
            String(raw, Charsets.UTF_8)
        }

        val lines = text.lines()
        if (lines.isEmpty()) return ImportResult(0, 0, listOf("Empty file"))

        // Build accountName→id map for resolving the Konto column
        val accountsByName = accountRepository.getAllAccountsList().associateBy { it.name }
        val defaultAccountId = accountRepository.getDefaultAccountId()

        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        // Skip header row (index 0)
        for ((idx, line) in lines.drop(1).withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            try {
                val cols = parseCsvLine(trimmed)
                if (cols.size < 8) {
                    skipped++
                    errors.add("Line ${idx + 2}: too few columns (${cols.size})")
                    continue
                }

                val typeStr   = cols[0].trim()
                val amount    = cols[1].trim().replace(',', '.').toDoubleOrNull()
                val currency  = cols[2].trim().ifEmpty { "PLN" }
                val category  = cols[3].trim()
                val merchant  = cols[4].trim()
                val date      = cols[5].trim().ifEmpty { LocalDate.now().toString() }
                val notes     = cols[6].trim()
                val status    = cols[7].trim()
                val acctName  = if (cols.size > 8) cols[8].trim() else ""

                if (amount == null) {
                    skipped++
                    errors.add("Line ${idx + 2}: invalid amount '${cols[1]}'")
                    continue
                }

                val accountId = accountsByName[acctName]?.id ?: defaultAccountId

                when (typeStr.lowercase()) {
                    "wydatek", "expense" -> {
                        transactionRepository.insert(
                            TransactionEntity(
                                type         = TransactionType.EXPENSE,
                                amount       = amount,
                                currency     = currency,
                                category     = category,
                                merchantName = merchant.ifEmpty { null },
                                date         = date,
                                notes        = notes,
                                accountId    = accountId
                            )
                        )
                        imported++
                    }

                    "przychód", "income" -> {
                        transactionRepository.insert(
                            TransactionEntity(
                                type      = TransactionType.INCOME,
                                amount    = amount,
                                currency  = currency,
                                category  = category,
                                payee     = merchant.ifEmpty { null },
                                date      = date,
                                notes     = notes,
                                accountId = accountId
                            )
                        )
                        imported++
                    }

                    "rachunek", "bill" -> {
                        val billStatus = when (status.lowercase()) {
                            "zapłacony", "paid"               -> BillStatus.PAID
                            "przeterminowany", "overdue"      -> BillStatus.OVERDUE
                            else                              -> BillStatus.UNPAID
                        }
                        billRepository.insert(
                            BillEntity(
                                vendorName    = merchant.ifEmpty { "Unknown" },
                                invoiceNumber = notes,
                                amount        = amount,
                                currency      = currency,
                                category      = category,
                                issueDate     = date,
                                dueDate       = date,
                                status        = billStatus,
                                accountId     = accountId
                            )
                        )
                        imported++
                    }

                    else -> {
                        skipped++
                        errors.add("Line ${idx + 2}: unknown type '$typeStr'")
                    }
                }
            } catch (e: Exception) {
                skipped++
                errors.add("Line ${idx + 2}: ${e.message}")
            }
        }

        return ImportResult(imported, skipped, errors)
    }

    // ── RFC-4180-compliant CSV line parser ────────────────────────────────────

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0

        while (i < line.length) {
            if (line[i] == '"') {
                i++ // skip opening quote
                while (i < line.length) {
                    if (line[i] == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            sb.append('"')
                            i += 2
                        } else {
                            i++ // skip closing quote
                            break
                        }
                    } else {
                        sb.append(line[i++])
                    }
                }
                result.add(sb.toString())
                sb.clear()
                if (i < line.length && line[i] == ',') i++
            } else {
                while (i < line.length && line[i] != ',') sb.append(line[i++])
                result.add(sb.toString())
                sb.clear()
                if (i < line.length) i++ // skip comma
            }
        }

        return result
    }
}
