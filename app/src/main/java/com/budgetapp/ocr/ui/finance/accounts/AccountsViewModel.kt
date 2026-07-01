package com.budgetapp.ocr.ui.finance.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.ocr.data.local.entity.AccountEntity
import com.budgetapp.ocr.data.local.entity.AccountType
import com.budgetapp.ocr.data.local.entity.TransactionEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.repository.AccountRepository
import com.budgetapp.ocr.data.repository.BillRepository
import com.budgetapp.ocr.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AccountWithBalance(
    val account: AccountEntity,
    val balance: Double
)

data class AccountsState(
    val accounts: List<AccountWithBalance> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AccountsState())
    val state: StateFlow<AccountsState> = _state.asStateFlow()

    private val _event = MutableSharedFlow<AccountEvent>()
    val event: SharedFlow<AccountEvent> = _event.asSharedFlow()

    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1)

    init {
        viewModelScope.launch {
            // Combine accounts Flow with refresh trigger to reload balances
            combine(
                accountRepository.getAllAccounts(),
                refreshTrigger.onStart { emit(Unit) }
            ) { accounts, _ -> accounts }
                .collect { accounts ->
                    val withBalances = accounts.map { account ->
                        AccountWithBalance(
                            account = account,
                            balance = accountRepository.getAccountBalance(account.id)
                        )
                    }
                    _state.value = AccountsState(accounts = withBalances, isLoading = false)
                }
        }
    }

    fun refreshBalances() {
        viewModelScope.launch { refreshTrigger.emit(Unit) }
    }

    fun saveAccount(
        existingId: Long?,
        name: String,
        type: AccountType,
        icon: String,
        color: String,
        isDefault: Boolean
    ) {
        viewModelScope.launch {
            if (isDefault) {
                accountRepository.setDefaultAccount(0) // clear all defaults first
            }

            val account = AccountEntity(
                id = existingId ?: 0,
                name = name,
                type = type,
                icon = icon,
                color = color,
                isDefault = isDefault,
                sortOrder = if (existingId != null) {
                    accountRepository.getAccountById(existingId)?.sortOrder ?: 0
                } else {
                    accountRepository.getCount()
                }
            )

            if (existingId != null) {
                accountRepository.update(account)
            } else {
                val newId = accountRepository.insert(account)
                if (isDefault) {
                    accountRepository.setDefaultAccount(newId)
                }
            }

            if (isDefault && (existingId ?: 0) > 0) {
                accountRepository.setDefaultAccount(existingId!!)
            }

            _event.emit(AccountEvent.AccountSaved)
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch {
            if (account.isDefault) {
                _event.emit(AccountEvent.Error("cannot_delete_default"))
                return@launch
            }
            val count = accountRepository.getCount()
            if (count <= 1) {
                _event.emit(AccountEvent.Error("cannot_delete_last"))
                return@launch
            }

            val defaultId = accountRepository.getDefaultAccountId()
            transactionRepository.reassignToAccount(account.id, defaultId)
            billRepository.reassignToAccount(account.id, defaultId)
            accountRepository.delete(account)
            _event.emit(AccountEvent.AccountDeleted)
        }
    }

    fun transfer(fromAccountId: Long, toAccountId: Long, amount: Double, notes: String) {
        viewModelScope.launch {
            if (fromAccountId == toAccountId) {
                _event.emit(AccountEvent.Error("same_account"))
                return@launch
            }
            if (amount <= 0) {
                _event.emit(AccountEvent.Error("invalid_amount"))
                return@launch
            }

            val transaction = TransactionEntity(
                type = TransactionType.TRANSFER,
                amount = amount,
                category = "TRANSFER",
                merchantName = null,
                date = LocalDate.now().toString(),
                notes = notes,
                accountId = fromAccountId,
                targetAccountId = toAccountId
            )
            transactionRepository.insert(transaction)
            _event.emit(AccountEvent.TransferCompleted)
            refreshBalances()
        }
    }

    fun adjustBalance(accountId: Long, targetBalance: Double) {
        viewModelScope.launch {
            val currentBalance = accountRepository.getAccountBalance(accountId)
            val diff = targetBalance - currentBalance
            if (diff == 0.0) {
                _event.emit(AccountEvent.BalanceNoChange)
                return@launch
            }

            val transaction = TransactionEntity(
                type = if (diff > 0) TransactionType.INCOME else TransactionType.EXPENSE,
                amount = kotlin.math.abs(diff),
                category = "BALANCE_ADJUSTMENT",
                merchantName = null,
                date = LocalDate.now().toString(),
                notes = "",
                accountId = accountId
            )
            transactionRepository.insert(transaction)
            _event.emit(AccountEvent.BalanceAdjusted)
            refreshBalances()
        }
    }

    sealed class AccountEvent {
        object AccountSaved : AccountEvent()
        object AccountDeleted : AccountEvent()
        object TransferCompleted : AccountEvent()
        object BalanceAdjusted : AccountEvent()
        object BalanceNoChange : AccountEvent()
        data class Error(val type: String) : AccountEvent()
    }
}
