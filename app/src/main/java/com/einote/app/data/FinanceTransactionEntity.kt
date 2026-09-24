package com.einote.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "finance_transactions")
data class FinanceTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val amountToman: Long = 0,
    val type: String = TYPE_EXPENSE,
    val category: String = "سایر",
    val transactionAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_INCOME = "INCOME"
        const val TYPE_EXPENSE = "EXPENSE"
    }
}
