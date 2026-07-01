package com.budgetapp.ocr.data.repository

import com.budgetapp.ocr.data.local.DefaultCategories
import com.budgetapp.ocr.data.local.dao.CategoryDao
import com.budgetapp.ocr.data.local.entity.CategoryEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for categories
 */
@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {

    fun getAllCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getAllCategories()
    }

    fun getCategoriesByType(type: TransactionType): Flow<List<CategoryEntity>> {
        return categoryDao.getCategoriesByType(type)
    }

    suspend fun getCategoryById(id: Long): CategoryEntity? {
        return categoryDao.getCategoryById(id)
    }

    suspend fun getCategoryByName(name: String): CategoryEntity? {
        return categoryDao.getCategoryByName(name)
    }

    fun getSystemCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getSystemCategories()
    }

    fun getCustomCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getCustomCategories()
    }

    suspend fun insert(category: CategoryEntity): Long {
        return categoryDao.insert(category)
    }

    suspend fun insertAll(categories: List<CategoryEntity>) {
        categoryDao.insertAll(categories)
    }

    suspend fun update(category: CategoryEntity) {
        categoryDao.update(category)
    }

    suspend fun delete(category: CategoryEntity) {
        categoryDao.delete(category)
    }

    suspend fun deleteCustomCategoryById(id: Long) {
        categoryDao.deleteCustomCategoryById(id)
    }

    /**
     * Initializes the database with default categories (only once).
     * Also backfills any missing default categories (e.g. BILL categories added in update).
     */
    suspend fun initializeDefaultCategories() {
        val existingCategories = withTimeout(10_000L) { categoryDao.getAllCategories().first() }
        if (existingCategories.isEmpty()) {
            val defaultCategories = DefaultCategories.getAllDefaultCategories()
            categoryDao.insertAll(defaultCategories)
        } else {
            // Backfill missing system categories (e.g. BILL categories added later)
            val existingNames = existingCategories.map { it.name }.toSet()
            val missing = DefaultCategories.getAllDefaultCategories()
                .filter { it.name !in existingNames }
            if (missing.isNotEmpty()) {
                categoryDao.insertAll(missing)
            }
        }
    }
}
