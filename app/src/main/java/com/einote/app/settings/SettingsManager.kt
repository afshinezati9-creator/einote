package com.einote.app.settings

import android.content.Context

class SettingsManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("einote_settings", Context.MODE_PRIVATE)

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)
    fun setDarkMode(value: Boolean) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    fun accent(): String = prefs.getString(KEY_ACCENT, ACCENT_BLUE) ?: ACCENT_BLUE
    fun setAccent(value: String) = prefs.edit().putString(KEY_ACCENT, value).apply()

    fun textScale(): Float = prefs.getFloat(KEY_TEXT_SCALE, 1f).coerceIn(0.9f, 1.2f)
    fun setTextScale(value: Float) = prefs.edit().putFloat(KEY_TEXT_SCALE, value.coerceIn(0.9f, 1.2f)).apply()

    fun showPinnedFirst(): Boolean = prefs.getBoolean(KEY_PINNED_FIRST, true)
    fun setShowPinnedFirst(value: Boolean) = prefs.edit().putBoolean(KEY_PINNED_FIRST, value).apply()

    fun showArchived(): Boolean = prefs.getBoolean(KEY_SHOW_ARCHIVED, false)
    fun setShowArchived(value: Boolean) = prefs.edit().putBoolean(KEY_SHOW_ARCHIVED, value).apply()

    fun notificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS, true)
    fun setNotificationsEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    fun compactBlocks(): Boolean = prefs.getBoolean(KEY_COMPACT_BLOCKS, false)
    fun setCompactBlocks(value: Boolean) = prefs.edit().putBoolean(KEY_COMPACT_BLOCKS, value).apply()

    companion object {
        const val ACCENT_BLUE = "blue"
        const val ACCENT_PURPLE = "purple"
        const val ACCENT_GREEN = "green"
        const val ACCENT_ORANGE = "orange"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_ACCENT = "accent"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_PINNED_FIRST = "pinned_first"
        private const val KEY_SHOW_ARCHIVED = "show_archived"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_COMPACT_BLOCKS = "compact_blocks"
    }
}
