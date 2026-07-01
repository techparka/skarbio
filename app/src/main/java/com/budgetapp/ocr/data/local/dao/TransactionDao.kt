package com.budgetapp.ocr.data.local.dao

import androidx.room.*
import com.budgetapp.ocr.data.local.entity.TransactionEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * DAO for transactions
 */
@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY date DESC, created_at DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC")
    fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTransactionsByDateRange(startDate: String, endDate: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE category = :category ORDER BY date DESC")
    fun getTransactionsByCategory(category: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date LIKE :yearMonth || '%' ORDER BY date DESC")
    fun getTransactionsForMonth(yearMonth: String): Flow<List<TransactionEntity>>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type AND date LIKE :yearMonth || '%'")
    suspend fun getTotalForMonth(type: TransactionType, yearMonth: String): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE transactions SET account_id = :newAccountId WHERE account_id = :oldAccountId")
    suspend fun reassignTransactionsToAccount(oldAccountId: Long, newAccountId: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    /**
     * Searches for potential duplicates based on merchant, amount and date
     * (within the last 30 minutes)
     */
    @Query("""
        SELECT * FROM transactions
        WHERE merchant_name = :merchantName
        AND amount = :amount
        AND ABS(created_at - :timestamp) < 1800000
        ORDER BY created_at DESC
        LIMIT 5
    """)
    suspend fun findPotentialDuplicates(
        merchantName: String,
        amount: Double,
        timestamp: Long
    ): List<TransactionEntity>

    /**
     * Fetches transactions with image_hash within the last 24h
     */
    @Query("""
        SELECT * FROM transactions
        WHERE image_hash IS NOT NULL
        AND created_at > :timestamp
        ORDER BY created_at DESC
    """)
    suspend fun getRecentTransactionsWithHash(timestamp: Long): List<TransactionEntity>
}
