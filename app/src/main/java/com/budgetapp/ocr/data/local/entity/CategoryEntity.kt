package com.budgetapp.ocr.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity dla kategorii (system + custom)
 * Zgodnie z REALISTIC_PLAN.md - Phase 1 schema
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "icon")
    val icon: String, // Emoji

    @ColumnInfo(name = "color")
    val color: String, // Hex color

    @ColumnInfo(name = "type")
    val type: TransactionType,

    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
