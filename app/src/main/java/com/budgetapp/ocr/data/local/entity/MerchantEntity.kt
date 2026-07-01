package com.budgetapp.ocr.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "merchants", indices = [Index("nip", unique = true)])
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "nip") val nip: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "category") val category: String? = null,
    @ColumnInfo(name = "source") val source: String = "user",
    @ColumnInfo(name = "usage_count") val usageCount: Int = 1,
    @ColumnInfo(name = "last_used") val lastUsed: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
