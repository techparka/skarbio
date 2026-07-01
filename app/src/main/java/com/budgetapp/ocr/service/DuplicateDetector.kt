package com.budgetapp.ocr.service

import android.graphics.Bitmap
import com.budgetapp.ocr.data.local.dao.TransactionDao
import com.budgetapp.ocr.data.local.entity.TransactionEntity
import com.budgetapp.ocr.util.ImageHasher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for detecting duplicate receipts
 */
@Singleton
class DuplicateDetector @Inject constructor(
    private val transactionDao: TransactionDao
) {

    /**
     * Checks whether a receipt is likely a duplicate
     * @return List of potential duplicates (empty if none)
     */
    suspend fun checkForDuplicates(
        bitmap: Bitmap,
        merchantName: String?,
        amount: Double,
        timestamp: Long = System.currentTimeMillis()
    ): DuplicateCheckResult {
        val duplicates = mutableListOf<TransactionEntity>()
        val reasons = mutableListOf<String>()

        // 1. Check image hash (most reliable method)
        val imageHash = ImageHasher.calculateHash(bitmap)
        val yesterday = timestamp - 86400000 // 24h ago
        val recentWithHash = transactionDao.getRecentTransactionsWithHash(yesterday)

        for (transaction in recentWithHash) {
            transaction.imageHash?.let { existingHash ->
                if (ImageHasher.areSimilar(imageHash, existingHash, threshold = 5)) {
                    duplicates.add(transaction)
                    reasons.add("Identical receipt photo")
                    return DuplicateCheckResult(
                        isDuplicate = true,
                        duplicates = duplicates,
                        confidence = 95,
                        reason = "Identical photo of this receipt found",
                        imageHash = imageHash
                    )
                }
            }
        }

        // 2. Check merchant + amount + time (less reliable)
        if (merchantName != null && amount > 0) {
            val potentialDuplicates = transactionDao.findPotentialDuplicates(
                merchantName = merchantName,
                amount = amount,
                timestamp = timestamp
            )

            if (potentialDuplicates.isNotEmpty()) {
                duplicates.addAll(potentialDuplicates)
                val minutesAgo = (timestamp - potentialDuplicates.first().createdAt) / 60000

                return DuplicateCheckResult(
                    isDuplicate = true,
                    duplicates = potentialDuplicates,
                    confidence = if (minutesAgo < 5) 80 else 60,
                    reason = "Similar transaction found from $merchantName for ${String.format("%.2f", amount)} PLN ($minutesAgo min ago)",
                    imageHash = imageHash
                )
            }
        }

        // No duplicates found
        return DuplicateCheckResult(
            isDuplicate = false,
            duplicates = emptyList(),
            confidence = 0,
            reason = null,
            imageHash = imageHash
        )
    }
}

/**
 * Result of duplicate check
 */
data class DuplicateCheckResult(
    val isDuplicate: Boolean,
    val duplicates: List<TransactionEntity>,
    val confidence: Int, // 0-100%
    val reason: String?,
    val imageHash: String
)
