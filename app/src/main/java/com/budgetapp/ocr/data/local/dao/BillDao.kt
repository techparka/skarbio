package com.budgetapp.ocr.data.local.dao

import androidx.room.*
import com.budgetapp.ocr.data.local.entity.BillEntity
import com.budgetapp.ocr.data.local.entity.BillStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    @Query("SELECT * FROM bills ORDER BY dueDate ASC")
    fun getAllBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE status = :status ORDER BY dueDate ASC")
    fun getBillsByStatus(status: BillStatus): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE status = 'UNPAID' ORDER BY dueDate ASC")
    fun getUnpaidBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE status = 'OVERDUE' ORDER BY dueDate ASC")
    fun getOverdueBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE vendorName = :vendorName ORDER BY dueDate DESC")
    fun getBillsByVendor(vendorName: String): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getBillById(id: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE status = 'UNPAID' ORDER BY dueDate ASC LIMIT :limit")
    fun getUpcomingUnpaidBills(limit: Int = 3): Flow<List<BillEntity>>

    /**
     * Fetches bills due in the current month + overdue bills
     * @param currentYearMonth Format: "yyyy-MM" (e.g. "2026-02")
     * @param today Format: "yyyy-MM-dd" (e.g. "2026-02-16")
     */
    @Query("""
        SELECT * FROM bills
        WHERE (status = 'UNPAID' OR status = 'OVERDUE')
        AND (dueDate LIKE :currentYearMonth || '%' OR dueDate < :today)
        ORDER BY dueDate ASC
    """)
    fun getCurrentMonthAndOverdueBills(currentYearMonth: String, today: String): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE reminderEnabled = 1 AND status = 'UNPAID'")
    suspend fun getBillsWithReminders(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE dueDate LIKE :yearMonth || '%'")
    fun getBillsForMonth(yearMonth: String): Flow<List<BillEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM bills WHERE dueDate LIKE :yearMonth || '%' AND status = 'PAID'")
    suspend fun getPaidBillsTotalForMonth(yearMonth: String): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: BillEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bills: List<BillEntity>)

    @Update
    suspend fun update(bill: BillEntity)

    @Delete
    suspend fun delete(bill: BillEntity)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bills WHERE recurringGroupId = :groupId")
    suspend fun deleteByRecurringGroupId(groupId: String)

    @Query("SELECT COUNT(*) FROM bills WHERE recurringGroupId = :groupId")
    suspend fun countByRecurringGroupId(groupId: String): Int

    @Query("SELECT * FROM bills WHERE recurringGroupId = :groupId ORDER BY dueDate ASC")
    suspend fun getByRecurringGroupId(groupId: String): List<BillEntity>

    @Query("UPDATE bills SET account_id = :newAccountId WHERE account_id = :oldAccountId")
    suspend fun reassignBillsToAccount(oldAccountId: Long, newAccountId: Long)

    @Query("UPDATE bills SET status = 'OVERDUE' WHERE status = 'UNPAID' AND dueDate < :today")
    suspend fun markOverdueBillsBeforeDate(today: String)

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}
