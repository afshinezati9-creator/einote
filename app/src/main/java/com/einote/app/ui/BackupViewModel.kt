package com.einote.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.einote.app.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = BackupManager(application)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun export(uri: Uri) = run("پشتیبان با موفقیت ساخته شد.") {
        manager.exportTo(uri)
    }

    fun import(uri: Uri) = run("بازیابی با موفقیت انجام شد.") {
        manager.importFrom(uri)
    }

    private fun run(success: String, action: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            try {
                action()
                _message.value = success
            } catch (e: BackupManager.BackupException) {
                _message.value = e.message ?: "عملیات پشتیبان ناموفق بود."
            } catch (_: Exception) {
                _message.value = "عملیات انجام نشد. لطفاً دوباره تلاش کن."
            } finally {
                _busy.value = false
            }
        }
    }
}
