package com.budgetapp.ocr.ui.common

import com.budgetapp.ocr.data.model.CategoryKey

/**
 * Helper for mapping category keys to Material icons and colors.
 * Delegates to CategoryKey enum for all lookups.
 */
object CategoryIcons {

    fun getIconRes(category: String): Int {
        return CategoryKey.fromName(category).iconResId
    }

    fun getColorRes(category: String): Int {
        return CategoryKey.fromName(category).colorResId
    }

    fun getBackgroundColorRes(category: String): Int {
        return CategoryKey.fromName(category).bgColorResId
    }
}
