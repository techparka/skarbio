package com.budgetapp.ocr.database

import androidx.room.*
import com.budgetapp.ocr.ExpenseCategory
import kotlinx.coroutines.flow.Flow

/**
 * DAO for expense operations
 */
@Dao
interface ExpenseDao {

    /**
     * Insert a new expense
     */
    @Insert
    suspend fun insert(expense: Expense): Long

    /**
     * Update an expense
     */
    @Update
    suspend fun update(expense: Expense)

    /**
     * Delete an expense
     */
    @Delete
    suspend fun delete(expense: Expense)

    /**
     * Get all expenses (newest first)
     */
    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    /**
     * Get an expense by ID
     */
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Long): Expense?

    /**
     * Get expenses for a specific month and year
     */
    @Query("SELECT * FROM expenses WHERE strftime('%Y-%m', datetime(createdAt/1000, 'unixepoch')) = :yearMonth ORDER BY createdAt DESC")
    fun getExpensesByMonth(yearMonth: String): Flow<List<Expense>>

    /**
     * Get expenses for a specific category
     */
    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY createdAt DESC")
    fun getExpensesByCategory(category: ExpenseCategory): Flow<List<Expense>>

    /**
     * Calculate the total expenses for a given month
     */
    @Query("SELECT SUM(amount) FROM expenses WHERE strftime('%Y-%m', datetime(createdAt/1000, 'unixepoch')) = :yearMonth")
    suspend fun getTotalForMonth(yearMonth: String): Double?

    /**
     * Calculate the total expenses per category in a given month
     */
    @Query("""
        SELECT category, SUM(amount) as total
        FROM expenses
        WHERE strftime('%Y-%m', datetime(createdAt/1000, 'unixepoch')) = :yearMonth
        GROUP BY category
        ORDER BY total DESC
    """)
    suspend fun getCategoryTotalsForMonth(yearMonth: String): List<CategoryTotal>

    /**
     * Delete all expenses (used for testing)
     */
    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}

/**
 * Data class for total expenses per category
 */
data class CategoryTotal(
    val category: ExpenseCategory,
    val total: Double
)
