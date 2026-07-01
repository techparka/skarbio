package com.budgetapp.ocr.ui.shared

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Activity-scoped ViewModel for passing a pending filter from Dashboard
 * to TransactionsFragment without adding to the back stack.
 */
@HiltViewModel
class SharedFilterViewModel @Inject constructor() : ViewModel() {

    private val _pendingFilter = MutableStateFlow<PendingFilter?>(null)
    val pendingFilter: StateFlow<PendingFilter?> = _pendingFilter.asStateFlow()

    fun setPendingFilter(filter: PendingFilter) {
        _pendingFilter.value = filter
    }

    /** Call after reading the filter to clear it so it isn't re-applied. */
    fun consume() {
        _pendingFilter.value = null
    }

    private val _navigateToBudgets = MutableStateFlow(false)
    val navigateToBudgets: StateFlow<Boolean> = _navigateToBudgets.asStateFlow()

    fun triggerBudgetsNavigation() {
        _navigateToBudgets.value = true
    }

    fun consumeBudgetsNavigation() {
        _navigateToBudgets.value = false
    }
}

/**
 * @param category  CategoryKey.name to filter by, or null for all
 * @param yearMonth "yyyy-MM" string for the specific month filter, or null
 * @param targetTab 0 = Transactions tab, 1 = Bills tab
 */
data class PendingFilter(
    val category: String? = null,
    val yearMonth: String? = null,
    val targetTab: Int = 0,
    val type: String? = null
)
