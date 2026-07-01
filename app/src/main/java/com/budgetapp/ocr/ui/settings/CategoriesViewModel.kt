package com.budgetapp.ocr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.ocr.data.local.entity.CategoryEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesState())
    val state: StateFlow<CategoriesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { all ->
                val items = mutableListOf<CategoryListItem>()
                val expenses = all.filter { it.type == TransactionType.EXPENSE }
                val income = all.filter { it.type == TransactionType.INCOME }
                val bills = all.filter { it.type == TransactionType.BILL }

                if (expenses.isNotEmpty()) {
                    items.add(CategoryListItem.Header(TransactionType.EXPENSE))
                    expenses.forEach { items.add(CategoryListItem.Entry(it)) }
                }
                if (income.isNotEmpty()) {
                    items.add(CategoryListItem.Header(TransactionType.INCOME))
                    income.forEach { items.add(CategoryListItem.Entry(it)) }
                }
                if (bills.isNotEmpty()) {
                    items.add(CategoryListItem.Header(TransactionType.BILL))
                    bills.forEach { items.add(CategoryListItem.Entry(it)) }
                }
                _state.update { it.copy(items = items, isLoading = false) }
            }
        }
    }

    fun addCategory(name: String, emoji: String, hexColor: String, type: TransactionType) {
        viewModelScope.launch {
            categoryRepository.insert(
                CategoryEntity(
                    name = name,
                    icon = emoji,
                    color = hexColor,
                    type = type,
                    isSystem = false
                )
            )
        }
    }

    fun updateCategory(entity: CategoryEntity) {
        viewModelScope.launch {
            categoryRepository.update(entity)
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            categoryRepository.deleteCustomCategoryById(id)
        }
    }
}

data class CategoriesState(
    val items: List<CategoryListItem> = emptyList(),
    val isLoading: Boolean = true
)

sealed class CategoryListItem {
    data class Header(val type: TransactionType) : CategoryListItem()
    data class Entry(val entity: CategoryEntity) : CategoryListItem()
}
