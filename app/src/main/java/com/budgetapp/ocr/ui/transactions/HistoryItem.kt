package com.budgetapp.ocr.ui.transactions

import com.budgetapp.ocr.data.local.entity.BillEntity
import com.budgetapp.ocr.data.local.entity.TransactionEntity

sealed class HistoryItem {
    abstract val sortDate: String   // YYYY-MM-DD — for chronological sorting
    abstract val itemId: Long
    abstract val itemType: ItemType

    enum class ItemType { TRANSACTION, BILL }

    data class TransactionItem(val transaction: TransactionEntity) : HistoryItem() {
        override val sortDate = transaction.date
        override val itemId = transaction.id
        override val itemType = ItemType.TRANSACTION
    }

    data class BillItem(val bill: BillEntity) : HistoryItem() {
        override val sortDate = bill.dueDate
        override val itemId = bill.id
        override val itemType = ItemType.BILL
    }
}
