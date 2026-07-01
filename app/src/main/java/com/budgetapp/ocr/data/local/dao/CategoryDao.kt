package com.budgetapp.ocr.data.local.dao

import androidx.room.*
import com.budgetapp.ocr.data.local.entity.CategoryEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * DAO dla kategorii
 */
@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY is_system DESC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY is_system DESC, name ASC")
    fun getCategoriesByType(type: TransactionType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE is_system = 1")
    fun getSystemCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE is_system = 0")
    fun getCustomCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND is_system = 0")
    suspend fun deleteCustomCategoryById(id: Long)

    @Query("DELETE FROM categories WHERE is_system = 0")
    suspend fun deleteAllCustomCategories()
}
