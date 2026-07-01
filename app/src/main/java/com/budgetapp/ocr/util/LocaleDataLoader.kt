package com.budgetapp.ocr.util

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Loads locale-specific data (merchant keywords, OCR hints, invoice vendors)
 * from JSON asset files under assets/data/{type}/{locale}.json.
 *
 * Locale resolution order: exact match (e.g. pl_PL) → language only (e.g. pl) → default (en).
 */
object LocaleDataLoader {

    private val categoryKeywordsCache = mutableMapOf<String, Map<String, List<String>>>()
    private val ocrKeywordsCache = mutableMapOf<String, Map<String, String>>()
    private val invoiceKeywordsCache = mutableMapOf<String, Map<String, String>>()

    /** Returns category → keyword list map for the current locale. */
    fun loadCategoryKeywords(context: Context): Map<String, List<String>> {
        val locale = currentLocaleTag()
        return categoryKeywordsCache.getOrPut(locale) {
            val json = readAsset(context, "data/category_keywords", locale) ?: return@getOrPut emptyMap()
            parseCategoryKeywords(json)
        }
    }

    /** Returns keyword → display name map for OCR store detection. */
    fun loadOcrKeywords(context: Context): Map<String, String> {
        val locale = currentLocaleTag()
        return ocrKeywordsCache.getOrPut(locale) {
            val json = readAsset(context, "data/ocr_keywords", locale) ?: return@getOrPut emptyMap()
            parseStringMap(json)
        }
    }

    /** Returns keyword → vendor display name map for invoice vendor detection. */
    fun loadInvoiceKeywords(context: Context): Map<String, String> {
        val locale = currentLocaleTag()
        return invoiceKeywordsCache.getOrPut(locale) {
            val json = readAsset(context, "data/invoice_keywords", locale) ?: return@getOrPut emptyMap()
            parseStringMap(json)
        }
    }

    /** Clears all caches (call on locale change if needed). */
    fun clearCaches() {
        categoryKeywordsCache.clear()
        ocrKeywordsCache.clear()
        invoiceKeywordsCache.clear()
    }

    // -- Private helpers --

    private fun currentLocaleTag(): String = Locale.getDefault().language

    /**
     * Resolves and reads a JSON asset file.
     * Tries: {dir}/{lang}.json → {dir}/pl.json → {dir}/en.json
     *
     * OCR merchant data is tied to physical receipts (always Polish),
     * not to the app UI language. Polish data is always the primary fallback.
     */
    private fun readAsset(context: Context, dir: String, locale: String): JSONObject? {
        val candidates = listOf("$dir/$locale.json", "$dir/pl.json", "$dir/en.json").distinct()
        for (path in candidates) {
            try {
                val text = context.assets.open(path).bufferedReader().use { it.readText() }
                return JSONObject(text)
            } catch (_: Exception) { }
        }
        return null
    }

    private fun parseCategoryKeywords(json: JSONObject): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        for (key in json.keys()) {
            val arr = json.getJSONArray(key)
            val list = (0 until arr.length()).map { arr.getString(it) }
            // Sort by keyword length descending so longer keywords match first
            result[key] = list.sortedByDescending { it.length }
        }
        return result
    }

    private fun parseStringMap(json: JSONObject): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (key in json.keys()) {
            result[key] = json.getString(key)
        }
        // Sort by key length descending so longer (more specific) keywords match first
        return result.entries
            .sortedByDescending { it.key.length }
            .associate { it.key to it.value }
    }
}
