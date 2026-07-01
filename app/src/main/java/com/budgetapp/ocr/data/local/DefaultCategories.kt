package com.budgetapp.ocr.data.local

import com.budgetapp.ocr.data.local.entity.CategoryEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.model.CategoryKey

/**
 * Default categories for the app.
 * Uses CategoryKey enum names as DB values for locale-independent storage.
 */
object DefaultCategories {

    fun getDefaultExpenseCategories(): List<CategoryEntity> {
        return CategoryKey.expenseCategories().map { key ->
            CategoryEntity(
                name = key.name,
                icon = key.emoji,
                color = key.hexColor,
                type = TransactionType.EXPENSE,
                isSystem = true
            )
        }
    }

    fun getDefaultIncomeCategories(): List<CategoryEntity> {
        return CategoryKey.incomeCategories().map { key ->
            CategoryEntity(
                name = key.name,
                icon = key.emoji,
                color = key.hexColor,
                type = TransactionType.INCOME,
                isSystem = true
            )
        }
    }

    fun getDefaultBillCategories(): List<CategoryEntity> {
        return CategoryKey.billCategories().map { key ->
            CategoryEntity(
                name = key.name,
                icon = key.emoji,
                color = key.hexColor,
                type = TransactionType.BILL,
                isSystem = true
            )
        }
    }

    fun getAllDefaultCategories(): List<CategoryEntity> {
        return getDefaultExpenseCategories() + getDefaultIncomeCategories() + getDefaultBillCategories()
    }
}
