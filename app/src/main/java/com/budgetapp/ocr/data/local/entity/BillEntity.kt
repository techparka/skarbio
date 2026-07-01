package com.budgetapp.ocr.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for bills/invoices (electricity, water, internet, phone)
 */
@Entity(
    tableName = "bills",
    indices = [
        Index("dueDate"),
        Index("status"),
        Index("account_id")
    ]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendorName: String,
    val invoiceNumber: String,
    val amount: Double,
    val currency: String = "PLN",
    val category: String,
    val issueDate: String,
    val dueDate: String,
    val status: BillStatus = BillStatus.UNPAID,
    val paymentDate: String? = null,
    val bankAccount: String? = null,
    val recurrence: BillRecurrence = BillRecurrence.NONE,
    val notes: String = "",
    val rawOcrText: String = "",
    val receiptImagePath: String? = null,
    val recurringGroupId: String? = null,
    val reminderEnabled: Boolean = false,
    val reminderDaysBefore: Int = 3,
    @ColumnInfo(name = "account_id") val accountId: Long = 1,
    val createdAt: Long = System.currentTimeMillis()
)

enum class BillStatus { UNPAID, PAID, OVERDUE }
enum class BillRecurrence { NONE, MONTHLY, BIMONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL }

fun BillRecurrence.toMonths(): Int = when (this) {
    BillRecurrence.MONTHLY -> 1
    BillRecurrence.BIMONTHLY -> 2
    BillRecurrence.QUARTERLY -> 3
    BillRecurrence.SEMIANNUAL -> 6
    BillRecurrence.ANNUAL -> 12
    BillRecurrence.NONE -> 0
}
