package com.budgetapp.ocr.data.repository

import com.budgetapp.ocr.data.local.dao.BillDao
import com.budgetapp.ocr.data.local.entity.BillEntity
import com.budgetapp.ocr.data.local.entity.BillStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val billDao: BillDao
) {

    fun getAllBills(): Flow<List<BillEntity>> = billDao.getAllBills()

    fun getUnpaidBills(): Flow<List<BillEntity>> = billDao.getUnpaidBills()

    fun getOverdueBills(): Flow<List<BillEntity>> = billDao.getOverdueBills()

    fun getBillsByVendor(vendorName: String): Flow<List<BillEntity>> =
        billDao.getBillsByVendor(vendorName)

    fun getUpcomingUnpaidBills(limit: Int = 3): Flow<List<BillEntity>> =
        billDao.getUpcomingUnpaidBills(limit)

    fun getCurrentMonthAndOverdueBills(): Flow<List<BillEntity>> {
        val today = LocalDate.now()
        val currentYearMonth = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
        val todayString = today.toString()  // Format: yyyy-MM-dd
        return billDao.getCurrentMonthAndOverdueBills(currentYearMonth, todayString)
    }

    suspend fun getBillById(id: Long): BillEntity? = billDao.getBillById(id)

    suspend fun insert(bill: BillEntity): Long = billDao.insert(bill)

    suspend fun insertAll(bills: List<BillEntity>) = billDao.insertAll(bills)

    suspend fun update(bill: BillEntity) = billDao.update(bill)

    suspend fun delete(bill: BillEntity) = billDao.delete(bill)

    suspend fun markAsPaid(bill: BillEntity) {
        val updated = bill.copy(
            status = BillStatus.PAID,
            paymentDate = LocalDate.now().toString()
        )
        billDao.update(updated)
    }

    suspend fun markAsUnpaid(bill: BillEntity) {
        val today = LocalDate.now().toString()
        val newStatus = if (bill.dueDate < today) BillStatus.OVERDUE else BillStatus.UNPAID
        val updated = bill.copy(
            status = newStatus,
            paymentDate = null
        )
        billDao.update(updated)
    }

    suspend fun markOverdueBills() {
        billDao.markOverdueBillsBeforeDate(LocalDate.now().toString())
    }

    suspend fun getBillsWithReminders(): List<BillEntity> = billDao.getBillsWithReminders()

    fun getBillsForMonth(yearMonth: String): Flow<List<BillEntity>> =
        billDao.getBillsForMonth(yearMonth)

    suspend fun getPaidBillsTotalForMonth(yearMonth: String): Double =
        billDao.getPaidBillsTotalForMonth(yearMonth)

    suspend fun reassignToAccount(oldAccountId: Long, newAccountId: Long) {
        billDao.reassignBillsToAccount(oldAccountId, newAccountId)
    }

    suspend fun deleteAll() = billDao.deleteAll()

    suspend fun deleteRecurringGroup(groupId: String) = billDao.deleteByRecurringGroupId(groupId)

    suspend fun countInRecurringGroup(groupId: String): Int = billDao.countByRecurringGroupId(groupId)

    suspend fun getRecurringGroup(groupId: String): List<BillEntity> = billDao.getByRecurringGroupId(groupId)

    suspend fun generateAndInsertRecurringBills(
        template: BillEntity,
        frequencyMonths: Int,
        durationMonths: Int,
        paymentDay: Int,
        startMonth: LocalDate
    ): Int {
        val groupId = UUID.randomUUID().toString()
        val bills = mutableListOf<BillEntity>()
        var current = startMonth.withDayOfMonth(paymentDay.coerceAtMost(startMonth.lengthOfMonth()))
        val end = startMonth.plusMonths(durationMonths.toLong())

        while (!current.isAfter(end)) {
            bills.add(
                template.copy(
                    id = 0,
                    dueDate = current.toString(),
                    status = BillStatus.UNPAID,
                    recurringGroupId = groupId
                )
            )
            val next = current.plusMonths(frequencyMonths.toLong())
            current = next.withDayOfMonth(paymentDay.coerceAtMost(next.lengthOfMonth()))
        }

        insertAll(bills)
        return bills.size
    }
}
