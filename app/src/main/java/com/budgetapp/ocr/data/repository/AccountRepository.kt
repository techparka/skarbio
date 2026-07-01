package com.budgetapp.ocr.data.repository

import com.budgetapp.ocr.data.local.dao.AccountDao
import com.budgetapp.ocr.data.local.entity.AccountEntity
import com.budgetapp.ocr.data.local.entity.AccountType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao
) {

    fun getAllAccounts(): Flow<List<AccountEntity>> {
        return accountDao.getAllAccounts()
    }

    suspend fun getAllAccountsList(): List<AccountEntity> {
        return accountDao.getAllAccountsList()
    }

    suspend fun getAccountById(id: Long): AccountEntity? {
        return accountDao.getAccountById(id)
    }

    suspend fun getDefaultAccount(): AccountEntity? {
        return accountDao.getDefaultAccount()
    }

    suspend fun getDefaultAccountId(): Long {
        return accountDao.getDefaultAccount()?.id ?: 1L
    }

    suspend fun getAccountBalance(accountId: Long): Double {
        return accountDao.getAccountBalance(accountId)
    }

    suspend fun insert(account: AccountEntity): Long {
        return accountDao.insert(account)
    }

    suspend fun update(account: AccountEntity) {
        accountDao.update(account)
    }

    suspend fun delete(account: AccountEntity) {
        accountDao.delete(account)
    }

    suspend fun deleteById(id: Long) {
        accountDao.deleteById(id)
    }

    suspend fun setDefaultAccount(accountId: Long) {
        accountDao.setDefaultAccount(accountId)
    }

    suspend fun getCount(): Int {
        return accountDao.getCount()
    }

    suspend fun deleteAll() {
        accountDao.deleteAll()
    }
}
