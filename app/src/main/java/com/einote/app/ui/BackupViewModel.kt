package com.einote.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.einote.app.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = BackupManager(application)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()\n\n    private val _info = MutableStateFlow<BackupManager.BackupInfo?>(null)\n    val info: StateFlow<BackupManager.BackupInfo?> = _info.asStateFlow()\n\n    fun inspect(uri: Uri) {\n        if (_busy.value) return\n        viewModelScope.launch {\n            _busy.value = true\n            _message.value = null\n            try {\n                _info.value = manager.inspect(uri)\n            } catch (e: BackupManager.BackupException) {\n                _info.value = null\n                _message.value = e.message ?: "فایل پشتیبان معتبر نیست."\n            } catch (_: Exception) {\n                _info.value = null\n                _message.value = "فایل پشتیبان قابل بررسی نیست."\n            } finally {\n                _busy.value = false\n            }\n        }\n    }\n\n    fun clearInfo() { _info.value = null }

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
