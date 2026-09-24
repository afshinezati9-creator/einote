package com.einote.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.einote.app.security.SecurityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecurityViewModel(application: Application) : AndroidViewModel(application) {
    private val security = SecurityManager(application)

    private val _enabled = MutableStateFlow(security.isLockEnabled())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _hasPin = MutableStateFlow(security.hasPin())
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    private val _autoLockMinutes = MutableStateFlow(security.autoLockMinutes())
    val autoLockMinutes: StateFlow<Int> = _autoLockMinutes.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun savePin(pin: String, confirm: String): Boolean {
        if (!pin.all(Char::isDigit) || pin.length !in 4..8) {
            _message.value = "رمز باید فقط عدد و بین ۴ تا ۸ رقم باشد."
            return false
        }
        if (pin != confirm) {
            _message.value = "تکرار رمز با رمز اصلی یکسان نیست."
            return false
        }
        security.setPin(pin)
        security.setLockEnabled(true)
        _enabled.value = true
        _hasPin.value = true
        _message.value = "قفل برنامه فعال شد."
        return true
    }

    fun disable(pin: String): Boolean {
        if (!security.verifyPin(pin)) {
            _message.value = "رمز واردشده درست نیست."
            return false
        }
        security.removePin()
        _enabled.value = false
        _hasPin.value = false
        _message.value = "قفل برنامه غیرفعال شد."
        return true
    }

    fun verify(pin: String): Boolean {
        val ok = security.verifyPin(pin)
        _message.value = if (ok) null else "رمز واردشده درست نیست."
        return ok
    }

    fun setAutoLock(minutes: Int) {
        security.setAutoLockMinutes(minutes)
        _autoLockMinutes.value = minutes.coerceIn(1, 60)
    }

    fun clearMessage() { _message.value = null }
}
