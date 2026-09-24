package com.einote.app.settings

import android.content.Context

class SettingsManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("einote_settings", Context.MODE_PRIVATE)

    fun themeMode(): String {
        val stored = prefs.getString(KEY_THEME_MODE, null)
        if (stored != null) return stored
        return if (prefs.getBoolean(KEY_DARK_MODE, false)) THEME_DARK else THEME_LIGHT
    }

    fun setThemeMode(value: String) {
        val safe = value.takeIf { it in THEME_MODES } ?: THEME_SYSTEM
        prefs.edit()
            .putString(KEY_THEME_MODE, safe)
            .putBoolean(KEY_DARK_MODE, safe == THEME_DARK)
            .apply()
    }

    fun isDarkMode(): Boolean = themeMode() == THEME_DARK
    fun setDarkMode(value: Boolean) = setThemeMode(if (value) THEME_DARK else THEME_LIGHT)

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

    fun dashboardDensity(): String = prefs.getString(KEY_DASHBOARD_DENSITY, DASHBOARD_BALANCED)\n        ?.takeIf { it in DASHBOARD_DENSITIES } ?: DASHBOARD_BALANCED\n\n    fun setDashboardDensity(value: String) {\n        val safe = value.takeIf { it in DASHBOARD_DENSITIES } ?: DASHBOARD_BALANCED\n        prefs.edit().putString(KEY_DASHBOARD_DENSITY, safe).apply()\n    }\n\n    fun animationsEnabled(): Boolean = prefs.getBoolean(KEY_ANIMATIONS, true)\n    fun setAnimationsEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_ANIMATIONS, value).apply()\n\n    fun editorMode(): String {
        val value = prefs.getString(KEY_EDITOR_MODE, EDITOR_STANDARD) ?: EDITOR_STANDARD
        return value.takeIf { it in EDITOR_MODES } ?: EDITOR_STANDARD
    }

    fun setEditorMode(value: String) {
        val safe = value.takeIf { it in EDITOR_MODES } ?: EDITOR_STANDARD
        prefs.edit().putString(KEY_EDITOR_MODE, safe).apply()
    }

    companion object {
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"
        val THEME_MODES = setOf(THEME_LIGHT, THEME_DARK, THEME_SYSTEM)

        const val ACCENT_BLUE = "blue"
        const val ACCENT_PURPLE = "purple"
        const val ACCENT_GREEN = "green"
        const val ACCENT_ORANGE = "orange"
        const val ACCENT_PINK = "pink"

        const val EDITOR_STANDARD = "standard"
        const val EDITOR_COMPACT = "compact"
        const val EDITOR_FOCUS = "focus"
        val EDITOR_MODES = setOf(EDITOR_STANDARD, EDITOR_COMPACT, EDITOR_FOCUS)\n\n        const val DASHBOARD_AIRY = "airy"\n        const val DASHBOARD_BALANCED = "balanced"\n        const val DASHBOARD_COMPACT = "compact"\n        val DASHBOARD_DENSITIES = setOf(DASHBOARD_AIRY, DASHBOARD_BALANCED, DASHBOARD_COMPACT)

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_ACCENT = "accent"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_PINNED_FIRST = "pinned_first"
        private const val KEY_SHOW_ARCHIVED = "show_archived"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_COMPACT_BLOCKS = "compact_blocks"
        private const val KEY_EDITOR_MODE = "editor_mode"\n        private const val KEY_DASHBOARD_DENSITY = "dashboard_density"\n        private const val KEY_ANIMATIONS = "animations"
    }
}
