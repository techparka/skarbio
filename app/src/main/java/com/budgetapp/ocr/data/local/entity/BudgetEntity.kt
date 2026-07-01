package com.budgetapp.ocr.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One budget record per (category, yearMonth) pair.
 * This allows the user to have different limits in different months
 * and compare their spending history accurately.
 */
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["category", "yearMonth"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,        // CategoryKey.name (e.g. "GROCERIES")
    val yearMonth: String,       // "yyyy-MM"
    val limitAmount: Double,
    val createdAt: Long = System.currentTimeMillis()
)
