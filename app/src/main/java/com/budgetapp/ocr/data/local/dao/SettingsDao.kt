package com.budgetapp.ocr.data.local.dao

import androidx.room.*
import com.budgetapp.ocr.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for settings (key-value store)
 */
@Dao
interface SettingsDao {

    @Query("SELECT * FROM settings")
    fun getAllSettings(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings WHERE key = :key")
    suspend fun getSettingByKey(key: String): SettingEntity?

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun getValueByKey(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: SettingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settings: List<SettingEntity>)

    @Update
    suspend fun update(setting: SettingEntity)

    @Delete
    suspend fun delete(setting: SettingEntity)

    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM settings")
    suspend fun deleteAll()
}
