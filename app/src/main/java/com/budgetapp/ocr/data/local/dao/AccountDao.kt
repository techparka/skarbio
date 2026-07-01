package com.budgetapp.ocr.data.local.dao

import androidx.room.*
import com.budgetapp.ocr.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY sort_order ASC, name ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sort_order ASC, name ASC")
    suspend fun getAllAccountsList(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultAccount(): AccountEntity?

    @Query("""
        SELECT
            COALESCE((SELECT SUM(amount) FROM transactions WHERE account_id = :accountId AND type = 'INCOME'), 0)
            - COALESCE((SELECT SUM(amount) FROM transactions WHERE account_id = :accountId AND type = 'EXPENSE'), 0)
            - COALESCE((SELECT SUM(amount) FROM transactions WHERE account_id = :accountId AND type = 'TRANSFER'), 0)
            + COALESCE((SELECT SUM(amount) FROM transactions WHERE target_account_id = :accountId AND type = 'TRANSFER'), 0)
            - COALESCE((SELECT SUM(amount) FROM bills WHERE account_id = :accountId AND status = 'PAID'), 0)
    """)
    suspend fun getAccountBalance(accountId: Long): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE accounts SET is_default = 0")
    suspend fun clearAllDefaults()

    @Query("UPDATE accounts SET is_default = 1 WHERE id = :accountId")
    suspend fun markAsDefault(accountId: Long)

    @androidx.room.Transaction
    suspend fun setDefaultAccount(accountId: Long) {
        clearAllDefaults()
        markAsDefault(accountId)
    }

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getCount(): Int

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}
