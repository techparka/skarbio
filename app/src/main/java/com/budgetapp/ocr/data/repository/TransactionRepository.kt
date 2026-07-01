package com.budgetapp.ocr.data.repository

import com.budgetapp.ocr.data.local.dao.TransactionDao
import com.budgetapp.ocr.data.local.entity.TransactionEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for transactions (without Use Cases - direct ViewModel access)
 * According to REALISTIC_PLAN.md
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {

    fun getAllTransactions(): Flow<List<TransactionEntity>> {
        return transactionDao.getAllTransactions()
    }

    suspend fun getTransactionById(id: Long): TransactionEntity? {
        return transactionDao.getTransactionById(id)
    }

    fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByType(type)
    }

    fun getTransactionsByDateRange(startDate: String, endDate: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByDateRange(startDate, endDate)
    }

    fun getTransactionsByCategory(category: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByCategory(category)
    }

    /**
     * Fetches transactions for a given month
     * @param yearMonth Format: "YYYY-MM" (e.g. "2026-02")
     */
    fun getTransactionsForMonth(yearMonth: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsForMonth(yearMonth)
    }

    /**
     * Fetches the total amount for a given transaction type in a month
     * @param type EXPENSE or INCOME
     * @param yearMonth Format: "YYYY-MM"
     * @return Total amount or 0.0 if no transactions exist
     */
    suspend fun getTotalForMonth(type: TransactionType, yearMonth: String): Double {
        return transactionDao.getTotalForMonth(type, yearMonth) ?: 0.0
    }

    suspend fun insert(transaction: TransactionEntity): Long {
        return transactionDao.insert(transaction)
    }

    suspend fun insertAll(transactions: List<TransactionEntity>) {
        transactionDao.insertAll(transactions)
    }

    suspend fun update(transaction: TransactionEntity) {
        transactionDao.update(transaction)
    }

    suspend fun delete(transaction: TransactionEntity) {
        transactionDao.delete(transaction)
    }

    suspend fun deleteById(id: Long) {
        transactionDao.deleteById(id)
    }

    suspend fun reassignToAccount(oldAccountId: Long, newAccountId: Long) {
        transactionDao.reassignTransactionsToAccount(oldAccountId, newAccountId)
    }

    suspend fun deleteAll() {
        transactionDao.deleteAll()
    }
}
