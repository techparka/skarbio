package com.budgetapp.ocr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.budgetapp.ocr.data.local.AppDatabase
import com.budgetapp.ocr.data.local.entity.AccountEntity
import com.budgetapp.ocr.data.local.entity.AccountType
import com.budgetapp.ocr.data.repository.AccountRepository
import com.budgetapp.ocr.data.repository.BillRepository
import com.budgetapp.ocr.data.repository.BudgetRepository
import com.budgetapp.ocr.data.repository.SettingsRepository
import com.budgetapp.ocr.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val billRepository: BillRepository,
    private val budgetRepository: BudgetRepository,
    private val accountRepository: AccountRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val currency = settingsRepository.getValueByKey(
                SettingsRepository.Keys.DEFAULT_CURRENCY, "PLN"
            )
            val theme = settingsRepository.getValueByKey(
                SettingsRepository.Keys.THEME, "system"
            )
            val reminders = settingsRepository.getValueByKey(
                SettingsRepository.Keys.BILL_REMINDERS_ENABLED, "true"
            ) == "true"
            val storeLookup = settingsRepository.getValueByKey(
                SettingsRepository.Keys.API_STORE_LOOKUP_ENABLED, "false"
            ) == "true"
            val appLock = settingsRepository.getValueByKey(
                SettingsRepository.Keys.APP_LOCK_ENABLED, "false"
            ) == "true"

            _state.update {
                it.copy(
                    defaultCurrency = currency,
                    theme = theme,
                    billRemindersEnabled = reminders,
                    storeLookupEnabled = storeLookup,
                    appLockEnabled = appLock,
                    isLoading = false
                )
            }
        }
    }

    fun setDefaultCurrency(currency: String) {
        viewModelScope.launch {
            settingsRepository.setSetting(SettingsRepository.Keys.DEFAULT_CURRENCY, currency)
            _state.update { it.copy(defaultCurrency = currency) }
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setSetting(SettingsRepository.Keys.THEME, theme)
            _state.update { it.copy(theme = theme) }
        }
    }

    fun setStoreLookupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSetting(
                SettingsRepository.Keys.API_STORE_LOOKUP_ENABLED,
                enabled.toString()
            )
            _state.update { it.copy(storeLookupEnabled = enabled) }
        }
    }

    fun setBillRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSetting(
                SettingsRepository.Keys.BILL_REMINDERS_ENABLED,
                enabled.toString()
            )
            _state.update { it.copy(billRemindersEnabled = enabled) }
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSetting(SettingsRepository.Keys.APP_LOCK_ENABLED, enabled.toString())
            _state.update { it.copy(appLockEnabled = enabled) }
        }
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            database.withTransaction {
                transactionRepository.deleteAll()
                billRepository.deleteAll()
                budgetRepository.deleteAll()
                accountRepository.deleteAll()
                accountRepository.insert(
                    AccountEntity(
                        id = 1,
                        name = "Główne",
                        type = AccountType.CHECKING,
                        icon = "\uD83D\uDCB3",
                        color = "#6366F1",
                        isDefault = true
                    )
                )
            }
            onComplete()
        }
    }
}

data class SettingsState(
    val defaultCurrency: String = "PLN",
    val theme: String = "system",
    val billRemindersEnabled: Boolean = true,
    val storeLookupEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val isLoading: Boolean = true
)
