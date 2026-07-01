package com.budgetapp.ocr.data.repository

import com.budgetapp.ocr.data.local.dao.BudgetDao
import com.budgetapp.ocr.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao
) {
    fun getBudgetsForMonth(yearMonth: String): Flow<List<BudgetEntity>> =
        budgetDao.getBudgetsForMonth(yearMonth)

    fun getBudgetHistoryForCategory(category: String): Flow<List<BudgetEntity>> =
        budgetDao.getBudgetHistoryForCategory(category)

    suspend fun getAvailableMonths(): List<String> =
        budgetDao.getAvailableMonths()

    suspend fun getBudgetForCategoryMonth(category: String, yearMonth: String): BudgetEntity? =
        budgetDao.getBudgetForCategoryMonth(category, yearMonth)

    suspend fun upsert(budget: BudgetEntity) =
        budgetDao.upsert(budget)

    suspend fun delete(budget: BudgetEntity) =
        budgetDao.delete(budget)

    suspend fun deleteBudgetForMonth(category: String, yearMonth: String) =
        budgetDao.deleteBudgetForMonth(category, yearMonth)

    suspend fun deleteAll() = budgetDao.deleteAll()
}
