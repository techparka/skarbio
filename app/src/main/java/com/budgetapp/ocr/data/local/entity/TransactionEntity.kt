package com.budgetapp.ocr.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity dla transakcji (expenses + income)
 * Zgodnie z REALISTIC_PLAN.md - Phase 1 schema
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["date"], orders = [Index.Order.DESC]),
        Index(value = ["type"]),
        Index(value = ["account_id"]),
        Index(value = ["category"]),
        Index(value = ["merchant_name"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "type")
    val type: TransactionType,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "currency")
    val currency: String = "PLN",

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "merchant_name")
    val merchantName: String? = null,

    @ColumnInfo(name = "payee")
    val payee: String? = null,

    @ColumnInfo(name = "date")
    val date: String, // YYYY-MM-DD format

    @ColumnInfo(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "receipt_image_path")
    val receiptImagePath: String? = null,

    @ColumnInfo(name = "raw_ocr_text")
    val rawOcrText: String = "",

    @ColumnInfo(name = "image_hash")
    val imageHash: String? = null,

    @ColumnInfo(name = "account_id")
    val accountId: Long = 1,

    @ColumnInfo(name = "target_account_id")
    val targetAccountId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Typ transakcji
 */
enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
    BILL
}
