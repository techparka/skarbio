package com.budgetapp.ocr.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for settings (key-value store)
 * According to REALISTIC_PLAN.md - Phase 1 schema
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey
    val key: String,

    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
