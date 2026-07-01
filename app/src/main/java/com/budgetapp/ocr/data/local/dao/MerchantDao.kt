package com.budgetapp.ocr.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.budgetapp.ocr.data.local.entity.MerchantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantDao {
    @Query("SELECT * FROM merchants WHERE nip = :nip LIMIT 1")
    suspend fun findByNip(nip: String): MerchantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(merchant: MerchantEntity)

    @Query("UPDATE merchants SET usage_count = usage_count + 1, last_used = :now WHERE nip = :nip")
    suspend fun incrementUsage(nip: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM merchants ORDER BY usage_count DESC")
    fun getAll(): Flow<List<MerchantEntity>>
}
