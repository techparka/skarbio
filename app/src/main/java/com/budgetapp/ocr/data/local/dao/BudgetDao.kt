package com.budgetapp.ocr.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.budgetapp.ocr.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth ORDER BY category ASC")
    fun getBudgetsForMonth(yearMonth: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE category = :category ORDER BY yearMonth DESC")
    fun getBudgetHistoryForCategory(category: String): Flow<List<BudgetEntity>>

    @Query("SELECT DISTINCT yearMonth FROM budgets ORDER BY yearMonth DESC")
    suspend fun getAvailableMonths(): List<String>

    @Query("SELECT * FROM budgets WHERE category = :category AND yearMonth = :yearMonth LIMIT 1")
    suspend fun getBudgetForCategoryMonth(category: String, yearMonth: String): BudgetEntity?

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category AND yearMonth = :yearMonth")
    suspend fun deleteBudgetForMonth(category: String, yearMonth: String)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
