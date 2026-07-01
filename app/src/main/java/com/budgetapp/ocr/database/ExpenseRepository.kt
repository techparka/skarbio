package com.budgetapp.ocr.database

import com.budgetapp.ocr.ExpenseCategory
import kotlinx.coroutines.flow.Flow

/**
 * Repository for expense operations
 * Abstraction layer between ViewModel and the database
 */
class ExpenseRepository(private val expenseDao: ExpenseDao) {

    /**
     * Get all expenses
     */
    fun getAllExpenses(): Flow<List<Expense>> {
        return expenseDao.getAllExpenses()
    }

    /**
     * Get expenses for a specific month
     * @param yearMonth Format: "YYYY-MM" (e.g. "2024-01")
     */
    fun getExpensesByMonth(yearMonth: String): Flow<List<Expense>> {
        return expenseDao.getExpensesByMonth(yearMonth)
    }

    /**
     * Get expenses for a specific category
     */
    fun getExpensesByCategory(category: ExpenseCategory): Flow<List<Expense>> {
        return expenseDao.getExpensesByCategory(category)
    }

    /**
     * Insert a new expense
     * @return ID of the newly inserted expense
     */
    suspend fun insert(expense: Expense): Long {
        return expenseDao.insert(expense)
    }

    /**
     * Update an expense
     */
    suspend fun update(expense: Expense) {
        expenseDao.update(expense)
    }

    /**
     * Delete an expense
     */
    suspend fun delete(expense: Expense) {
        expenseDao.delete(expense)
    }

    /**
     * Get an expense by ID
     */
    suspend fun getExpenseById(id: Long): Expense? {
        return expenseDao.getExpenseById(id)
    }

    /**
     * Calculate the total expenses for a given month
     * @param yearMonth Format: "YYYY-MM" (e.g. "2024-01")
     */
    suspend fun getTotalForMonth(yearMonth: String): Double {
        return expenseDao.getTotalForMonth(yearMonth) ?: 0.0
    }

    /**
     * Get the total expenses per category in a given month
     * @param yearMonth Format: "YYYY-MM" (e.g. "2024-01")
     */
    suspend fun getCategoryTotalsForMonth(yearMonth: String): List<CategoryTotal> {
        return expenseDao.getCategoryTotalsForMonth(yearMonth)
    }

    /**
     * Delete all expenses (for testing only!)
     */
    suspend fun deleteAll() {
        expenseDao.deleteAll()
    }
}
