package com.budgetapp.ocr.data.model

import com.budgetapp.ocr.R

/**
 * Locale-independent category keys stored in DB.
 * Each entry maps to a string resource for display, an icon drawable, and colors.
 */
enum class CategoryKey(
    val stringResId: Int,
    val iconResId: Int,
    val colorResId: Int,
    val bgColorResId: Int,
    val emoji: String,
    val hexColor: String
) {
    // Expense categories
    GROCERIES(R.string.cat_groceries, R.drawable.ic_cat_groceries, R.color.cat_groceries, R.color.cat_groceries_bg, "\uD83D\uDED2", "#4CAF50"),
    FUEL(R.string.cat_fuel, R.drawable.ic_cat_fuel, R.color.cat_fuel, R.color.cat_fuel_bg, "\u26FD", "#FF9800"),
    HEALTH(R.string.cat_health, R.drawable.ic_cat_health, R.color.cat_health, R.color.cat_health_bg, "\uD83D\uDC8A", "#F44336"),
    RESTAURANT(R.string.cat_restaurant, R.drawable.ic_cat_restaurant, R.color.cat_restaurant, R.color.cat_restaurant_bg, "\uD83C\uDF55", "#E91E63"),
    CLOTHING(R.string.cat_clothing, R.drawable.ic_cat_clothing, R.color.cat_clothing, R.color.cat_clothing_bg, "\uD83D\uDC55", "#9C27B0"),
    ELECTRONICS(R.string.cat_electronics, R.drawable.ic_cat_electronics, R.color.cat_electronics, R.color.cat_electronics_bg, "\uD83D\uDCBB", "#2196F3"),
    ENTERTAINMENT(R.string.cat_entertainment, R.drawable.ic_cat_entertainment, R.color.cat_entertainment, R.color.cat_entertainment_bg, "\uD83C\uDFAC", "#00BCD4"),
    TRANSPORT(R.string.cat_transport, R.drawable.ic_cat_transport, R.color.cat_transport, R.color.cat_transport_bg, "\uD83D\uDE8C", "#795548"),
    BILLS(R.string.cat_bills, R.drawable.ic_cat_bills, R.color.cat_bills, R.color.cat_bills_bg, "\uD83D\uDCC4", "#607D8B"),
    BEAUTY(R.string.cat_beauty, R.drawable.ic_cat_other, R.color.cat_other, R.color.cat_other_bg, "\uD83D\uDC84", "#E91E63"),
    HOME(R.string.cat_home, R.drawable.ic_cat_home, R.color.cat_home, R.color.cat_home_bg, "\uD83C\uDFE0", "#8BC34A"),
    PETS(R.string.cat_pets, R.drawable.ic_cat_other, R.color.cat_other, R.color.cat_other_bg, "\uD83D\uDC3E", "#FF5722"),
    EDUCATION(R.string.cat_education, R.drawable.ic_cat_other, R.color.cat_other, R.color.cat_other_bg, "\uD83D\uDCDA", "#3F51B5"),
    GIFTS(R.string.cat_gifts, R.drawable.ic_cat_other, R.color.cat_other, R.color.cat_other_bg, "\uD83C\uDF81", "#E91E63"),
    OTHER_EXPENSE(R.string.cat_other_expense, R.drawable.ic_cat_other, R.color.cat_other, R.color.cat_other_bg, "\uD83D\uDCE6", "#9E9E9E"),
    BALANCE_ADJUSTMENT(R.string.cat_balance_adjustment, R.drawable.ic_cat_other, R.color.cat_other, R.color.cat_other_bg, "\u2696\uFE0F", "#78909C"),

    // Income categories
    SALARY(R.string.cat_salary, R.drawable.ic_cat_income, R.color.cat_groceries, R.color.cat_groceries_bg, "\uD83D\uDCB0", "#4CAF50"),
    FREELANCE(R.string.cat_freelance, R.drawable.ic_cat_income, R.color.cat_electronics, R.color.cat_electronics_bg, "\uD83D\uDCBC", "#2196F3"),
    GIFT_INCOME(R.string.cat_gift_income, R.drawable.ic_cat_income, R.color.cat_restaurant, R.color.cat_restaurant_bg, "\uD83C\uDF81", "#E91E63"),
    OTHER_INCOME(R.string.cat_other_income, R.drawable.ic_cat_income, R.color.cat_other, R.color.cat_other_bg, "\uD83D\uDCB5", "#9E9E9E"),

    // Bill-specific categories
    TELECOM(R.string.cat_telecom, R.drawable.ic_cat_bills, R.color.cat_bills, R.color.cat_bills_bg, "\uD83D\uDCF1", "#607D8B"),
    ENERGY(R.string.cat_energy, R.drawable.ic_cat_bills, R.color.cat_fuel, R.color.cat_fuel_bg, "\u26A1", "#FF9800"),
    WATER(R.string.cat_water, R.drawable.ic_cat_bills, R.color.cat_electronics, R.color.cat_electronics_bg, "\uD83D\uDCA7", "#2196F3"),
    GAS(R.string.cat_gas, R.drawable.ic_cat_bills, R.color.cat_fuel, R.color.cat_fuel_bg, "\uD83D\uDD25", "#FF9800"),
    INTERNET(R.string.cat_internet, R.drawable.ic_cat_bills, R.color.cat_entertainment, R.color.cat_entertainment_bg, "\uD83C\uDF10", "#00BCD4"),
    OTHER_BILL(R.string.cat_other_bill, R.drawable.ic_cat_bills, R.color.cat_other, R.color.cat_other_bg, "\uD83D\uDCC4", "#9E9E9E");

    companion object {
        /** Returns localized display name for system categories, raw name for custom ones. */
        fun localizedName(context: android.content.Context, name: String): String {
            return try {
                context.getString(valueOf(name).stringResId)
            } catch (_: IllegalArgumentException) {
                polishNameMap[name]?.let { context.getString(it.stringResId) } ?: name
            }
        }

        /** Map from old Polish display name to CategoryKey for DB migration */
        private val polishNameMap = mapOf(
            // Expense categories
            "Zakupy spożywcze" to GROCERIES,
            "Paliwo" to FUEL,
            "Zdrowie" to HEALTH,
            "Restauracje" to RESTAURANT,
            "Odzież" to CLOTHING,
            "Elektronika" to ELECTRONICS,
            "Rozrywka" to ENTERTAINMENT,
            "Transport" to TRANSPORT,
            "Rachunki" to BILLS,
            "Uroda" to BEAUTY,
            "Dom i ogród" to HOME,
            "Zwierzęta" to PETS,
            "Edukacja" to EDUCATION,
            "Prezenty" to GIFTS,
            "Inne wydatki" to OTHER_EXPENSE,
            "Inne" to OTHER_EXPENSE,
            // Income categories
            "Wynagrodzenie" to SALARY,
            "Freelance" to FREELANCE,
            "Prezent" to GIFT_INCOME,
            "Inne przychody" to OTHER_INCOME,
            // Bill categories
            "Telekomunikacja" to TELECOM,
            "Energia" to ENERGY,
            "Woda" to WATER,
            "Gaz" to GAS,
            "Internet" to INTERNET,
            "Inne rachunki" to OTHER_BILL,
            // Balance adjustment
            "Korekta salda" to BALANCE_ADJUSTMENT,
            // Legacy fallbacks
            "OTHER" to OTHER_EXPENSE
        )

        fun fromPolishName(name: String): CategoryKey {
            return polishNameMap[name] ?: OTHER_EXPENSE
        }

        fun fromName(name: String): CategoryKey {
            return try {
                valueOf(name)
            } catch (_: IllegalArgumentException) {
                polishNameMap[name] ?: OTHER_EXPENSE
            }
        }

        /** Like [fromName] but defaults to [OTHER_BILL] for bill context. */
        fun fromNameForBill(name: String): CategoryKey {
            return try {
                valueOf(name)
            } catch (_: IllegalArgumentException) {
                polishNameMap[name] ?: OTHER_BILL
            }
        }

        fun expenseCategories(): List<CategoryKey> = listOf(
            GROCERIES, FUEL, HEALTH, RESTAURANT, CLOTHING,
            ELECTRONICS, ENTERTAINMENT, TRANSPORT, BILLS,
            BEAUTY, HOME, PETS, EDUCATION, GIFTS, OTHER_EXPENSE
        )

        fun incomeCategories(): List<CategoryKey> = listOf(
            SALARY, FREELANCE, GIFT_INCOME, OTHER_INCOME
        )

        fun billCategories(): List<CategoryKey> = listOf(
            TELECOM, ENERGY, WATER, GAS, INTERNET, OTHER_BILL
        )
    }
}
