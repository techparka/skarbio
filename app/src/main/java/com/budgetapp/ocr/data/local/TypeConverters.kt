package com.budgetapp.ocr.data.local

import androidx.room.TypeConverter
import com.budgetapp.ocr.data.local.entity.AccountType
import com.budgetapp.ocr.data.local.entity.BillRecurrence
import com.budgetapp.ocr.data.local.entity.BillStatus
import com.budgetapp.ocr.data.local.entity.TransactionType

/**
 * Type converters dla Room Database
 */
class TypeConverters {

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String {
        return type.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return try {
            TransactionType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            TransactionType.EXPENSE // Default fallback
        }
    }

    @TypeConverter
    fun fromBillStatus(status: BillStatus): String {
        return status.name
    }

    @TypeConverter
    fun toBillStatus(value: String): BillStatus {
        return try {
            BillStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            BillStatus.UNPAID
        }
    }

    @TypeConverter
    fun fromAccountType(type: AccountType): String {
        return type.name
    }

    @TypeConverter
    fun toAccountType(value: String): AccountType {
        return try {
            AccountType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            AccountType.CHECKING
        }
    }

    @TypeConverter
    fun fromBillRecurrence(recurrence: BillRecurrence): String {
        return recurrence.name
    }

    @TypeConverter
    fun toBillRecurrence(value: String): BillRecurrence {
        return try {
            BillRecurrence.valueOf(value)
        } catch (e: IllegalArgumentException) {
            BillRecurrence.NONE
        }
    }
}
