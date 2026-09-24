package com.einote.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.einote.app.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsManager(application)

    private val _darkMode = MutableStateFlow(settings.isDarkMode())
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()
    private val _accent = MutableStateFlow(settings.accent())
    val accent: StateFlow<String> = _accent.asStateFlow()
    private val _textScale = MutableStateFlow(settings.textScale())
    val textScale: StateFlow<Float> = _textScale.asStateFlow()
    private val _pinnedFirst = MutableStateFlow(settings.showPinnedFirst())
    val pinnedFirst: StateFlow<Boolean> = _pinnedFirst.asStateFlow()
    private val _showArchived = MutableStateFlow(settings.showArchived())
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()
    private val _notifications = MutableStateFlow(settings.notificationsEnabled())
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()
    private val _compactBlocks = MutableStateFlow(settings.compactBlocks())
    val compactBlocks: StateFlow<Boolean> = _compactBlocks.asStateFlow()

    fun setDarkMode(v: Boolean) { settings.setDarkMode(v); _darkMode.value = v }
    fun setAccent(v: String) { settings.setAccent(v); _accent.value = v }
    fun setTextScale(v: Float) { settings.setTextScale(v); _textScale.value = v.coerceIn(0.9f, 1.2f) }
    fun setPinnedFirst(v: Boolean) { settings.setShowPinnedFirst(v); _pinnedFirst.value = v }
    fun setShowArchived(v: Boolean) { settings.setShowArchived(v); _showArchived.value = v }
    fun setNotifications(v: Boolean) { settings.setNotificationsEnabled(v); _notifications.value = v }
    fun setCompactBlocks(v: Boolean) { settings.setCompactBlocks(v); _compactBlocks.value = v }
}
