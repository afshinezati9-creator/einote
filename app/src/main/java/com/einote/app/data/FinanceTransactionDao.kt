package com.einote.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceTransactionDao {
    @Query("SELECT * FROM finance_transactions ORDER BY transactionAt DESC, id DESC")
    fun observeAll(): Flow<List<FinanceTransactionEntity>>

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountToman ELSE 0 END), 0) FROM finance_transactions")
    fun observeIncome(): Flow<Long>

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountToman ELSE 0 END), 0) FROM finance_transactions")
    fun observeExpense(): Flow<Long>

    @Insert
    suspend fun insert(transaction: FinanceTransactionEntity): Long

    @Delete
    suspend fun delete(transaction: FinanceTransactionEntity)
}
