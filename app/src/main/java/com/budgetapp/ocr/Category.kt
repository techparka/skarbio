package com.budgetapp.ocr

import com.budgetapp.ocr.data.model.CategoryKey

/**
 * Expense categories used by receipt scanner and category manager.
 * Each entry maps to a CategoryKey for localized display.
 */
enum class ExpenseCategory(val categoryKey: CategoryKey) {
    GROCERIES(CategoryKey.GROCERIES),
    FUEL(CategoryKey.FUEL),
    HEALTH(CategoryKey.HEALTH),
    RESTAURANT(CategoryKey.RESTAURANT),
    CLOTHING(CategoryKey.CLOTHING),
    ELECTRONICS(CategoryKey.ELECTRONICS),
    ENTERTAINMENT(CategoryKey.ENTERTAINMENT),
    TRANSPORT(CategoryKey.TRANSPORT),
    BILLS(CategoryKey.BILLS),
    BEAUTY(CategoryKey.BEAUTY),
    HOME(CategoryKey.HOME),
    PETS(CategoryKey.PETS),
    EDUCATION(CategoryKey.EDUCATION),
    GIFTS(CategoryKey.GIFTS),
    OTHER(CategoryKey.OTHER_EXPENSE);

    /** Returns the DB-safe key name (e.g. "GROCERIES") */
    val keyName: String get() = categoryKey.name

    companion object {
        fun fromCategoryKey(key: CategoryKey): ExpenseCategory {
            return entries.find { it.categoryKey == key } ?: OTHER
        }

        fun fromKeyName(name: String): ExpenseCategory {
            return entries.find { it.categoryKey.name == name } ?: OTHER
        }
    }
}
