package com.budgetapp.ocr

import android.content.Context
import com.budgetapp.ocr.util.LocaleDataLoader

/**
 * Categorizes expenses based on store name using locale-specific keyword matching.
 * Keywords are loaded from assets/data/category_keywords/{locale}.json.
 *
 * Matching order (first match wins):
 *   1. Exact substring match (fast path)
 *   2. Fuzzy match — Levenshtein distance ≤ 2 for keywords longer than 5 chars
 *      applied word-by-word on the search text
 */
object CategoryManager {

    // Levenshtein distance threshold and minimum keyword length for fuzzy matching
    private const val FUZZY_MAX_DISTANCE = 2
    private const val FUZZY_MIN_KEYWORD_LENGTH = 6

    fun categorize(storeName: String, fullText: String = "", context: Context): ExpenseCategory {
        val keywords = LocaleDataLoader.loadCategoryKeywords(context)
        val searchText = "$storeName $fullText".lowercase().trim()

        // Pass 1: exact substring match
        for ((categoryName, categoryKeywords) in keywords) {
            if (categoryKeywords.any { searchText.contains(it) }) {
                return ExpenseCategory.entries.find { it.name == categoryName }
                    ?: ExpenseCategory.OTHER
            }
        }

        // Pass 2: fuzzy match — compare each word in searchText against each keyword
        val words = searchText.split(Regex("\\s+|[,./\\-]")).filter { it.isNotBlank() }
        for ((categoryName, categoryKeywords) in keywords) {
            if (categoryKeywords.any { keyword -> fuzzyMatchesAnyWord(words, keyword) }) {
                return ExpenseCategory.entries.find { it.name == categoryName }
                    ?: ExpenseCategory.OTHER
            }
        }

        return ExpenseCategory.OTHER
    }

    fun getAllCategories(): List<ExpenseCategory> = ExpenseCategory.entries.toList()

    // -- Fuzzy matching helpers --

    /**
     * Returns true if any word in [words] is within [FUZZY_MAX_DISTANCE] of [keyword].
     * Only applied when keyword length >= [FUZZY_MIN_KEYWORD_LENGTH] to avoid
     * false positives on short keywords (e.g. "bp", "nip", "kfc").
     */
    private fun fuzzyMatchesAnyWord(words: List<String>, keyword: String): Boolean {
        if (keyword.length < FUZZY_MIN_KEYWORD_LENGTH) return false
        return words.any { word ->
            // Quick length filter — skip if word is way too short or too long
            val lengthDiff = Math.abs(word.length - keyword.length)
            lengthDiff <= FUZZY_MAX_DISTANCE && levenshtein(word, keyword) <= FUZZY_MAX_DISTANCE
        }
    }

    /**
     * Computes Levenshtein edit distance between two strings.
     * Uses single-row DP for O(min(m,n)) space.
     */
    internal fun levenshtein(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val longer  = if (s1.length >= s2.length) s1 else s2
        val shorter = if (s1.length >= s2.length) s2 else s1

        var row = IntArray(shorter.length + 1) { it }

        for (i in 1..longer.length) {
            var prev = row[0]
            row[0] = i
            for (j in 1..shorter.length) {
                val temp = row[j]
                row[j] = if (longer[i - 1] == shorter[j - 1]) {
                    prev
                } else {
                    1 + minOf(prev, row[j], row[j - 1])
                }
                prev = temp
            }
        }
        return row[shorter.length]
    }
}
