package com.einote.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.einote.app.data.FinanceTransactionEntity
import com.einote.app.data.NoteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = NoteDatabase.get(application).financeTransactionDao()

    val transactions: Flow<List<FinanceTransactionEntity>> = dao.observeAll()
    val income: Flow<Long> = dao.observeIncome()
    val expense: Flow<Long> = dao.observeExpense()

    fun addTransaction(title: String, amount: Long, type: String, category: String, transactionAt: Long) =
        viewModelScope.launch {
            if (amount > 0) dao.insert(
                FinanceTransactionEntity(
                    title = title.trim(),
                    amountToman = amount,
                    type = type,
                    category = category,
                    transactionAt = transactionAt
                )
            )
        }

    fun delete(transaction: FinanceTransactionEntity) = viewModelScope.launch { dao.delete(transaction) }
}
