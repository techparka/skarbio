package com.budgetapp.ocr.utils

object CurrencyFormatter {

    fun symbolFor(currency: String): String = when (currency) {
        "EUR" -> "€"
        "USD" -> "$"
        else  -> "zł"
    }

    /** e.g. "123.45 zł" */
    fun format(amount: Double, currency: String): String =
        String.format("%.2f %s", amount, symbolFor(currency))

    /** e.g. "123 zł" (no decimals) */
    fun formatRounded(amount: Double, currency: String): String =
        String.format("%.0f %s", amount, symbolFor(currency))
}
