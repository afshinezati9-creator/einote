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

    private val _info = MutableStateFlow<BackupManager.BackupInfo?>(null)
    val info: StateFlow<BackupManager.BackupInfo?> = _info.asStateFlow()

    fun inspect(uri: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            try {
                _info.value = manager.inspect(uri)
            } catch (e: BackupManager.BackupException) {
                _info.value = null
                _message.value = e.message ?: "فایل پشتیبان معتبر نیست."
            } catch (_: Exception) {
                _info.value = null
                _message.value = "فایل پشتیبان قابل بررسی نیست."
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearInfo() { _info.value = null }

    fun export(uri: Uri) = run("پشتیبان با موفقیت ساخته شد.") {
        manager.exportTo(uri)
    }

    fun exportEncrypted(uri: Uri, password: CharArray) = run("پشتیبان رمزگذاری‌شده با موفقیت ساخته شد.") {\n        try { manager.exportEncryptedTo(uri, password) } finally { password.fill(Char(0)) }\n    }\n\n    fun import(uri: Uri) = run("بازیابی با موفقیت انجام شد.") {
        manager.importFrom(uri)
    }

    fun importEncrypted(uri: Uri, password: CharArray) = run("بازیابی پشتیبان رمزگذاری‌شده با موفقیت انجام شد.") {\n        try { manager.importEncryptedFrom(uri, password) } finally { password.fill(Char(0)) }\n    }\n\n    private fun run(success: String, action: suspend () -> Unit) {
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
