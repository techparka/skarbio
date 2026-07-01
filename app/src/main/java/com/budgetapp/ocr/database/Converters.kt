package com.budgetapp.ocr.database

import androidx.room.TypeConverter
import com.budgetapp.ocr.ExpenseCategory

/**
 * Type converters dla Room Database
 */
class Converters {
    @TypeConverter
    fun fromExpenseCategory(category: ExpenseCategory): String {
        return category.name
    }

    @TypeConverter
    fun toExpenseCategory(name: String): ExpenseCategory {
        return try {
            ExpenseCategory.valueOf(name)
        } catch (e: IllegalArgumentException) {
            ExpenseCategory.OTHER
        }
    }
}
