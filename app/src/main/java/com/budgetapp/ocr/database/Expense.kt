package com.budgetapp.ocr.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.budgetapp.ocr.ExpenseCategory

/**
 * Entity representing an expense in the database
 */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val amount: Double,
    val currency: String = "PLN",
    val storeName: String,
    val category: ExpenseCategory,

    // Transaction date (from receipt)
    val date: String?,

    // User notes
    val notes: String = "",

    // Raw OCR text (for debugging)
    val rawOcrText: String = "",

    // Timestamp when added to the database
    val createdAt: Long = System.currentTimeMillis(),

    // Whether the expense was edited by the user
    val isEdited: Boolean = false
)
