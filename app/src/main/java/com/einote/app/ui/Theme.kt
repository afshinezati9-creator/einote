package com.einote.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

private fun accentColor(name: String): Color = when (name) {
    "purple" -> Color(0xFF7C4DFF)
    "green" -> Color(0xFF2E7D32)
    "orange" -> Color(0xFFEF6C00)
    else -> Color(0xFF1565C0)
}

@Composable
fun EiNoteTheme(
    darkTheme: Boolean,
    accent: String,
    textScale: Float,
    content: @Composable () -> Unit
) {
    val primary = accentColor(accent)
    val colors = if (darkTheme) {
        darkColorScheme(primary = primary, secondary = primary, tertiary = primary)
    } else {
        lightColorScheme(primary = primary, secondary = primary, tertiary = primary)
    }
    val base = Typography()
    val s = textScale.coerceIn(0.9f, 1.2f)
    val typography = base.copy(
        displayLarge = base.displayLarge.copy(fontSize = base.displayLarge.fontSize * s),
        displayMedium = base.displayMedium.copy(fontSize = base.displayMedium.fontSize * s),
        displaySmall = base.displaySmall.copy(fontSize = base.displaySmall.fontSize * s),
        headlineLarge = base.headlineLarge.copy(fontSize = base.headlineLarge.fontSize * s),
        headlineMedium = base.headlineMedium.copy(fontSize = base.headlineMedium.fontSize * s),
        headlineSmall = base.headlineSmall.copy(fontSize = base.headlineSmall.fontSize * s),
        titleLarge = base.titleLarge.copy(fontSize = base.titleLarge.fontSize * s),
        titleMedium = base.titleMedium.copy(fontSize = base.titleMedium.fontSize * s),
        titleSmall = base.titleSmall.copy(fontSize = base.titleSmall.fontSize * s),
        bodyLarge = base.bodyLarge.copy(fontSize = base.bodyLarge.fontSize * s),
        bodyMedium = base.bodyMedium.copy(fontSize = base.bodyMedium.fontSize * s),
        bodySmall = base.bodySmall.copy(fontSize = base.bodySmall.fontSize * s),
        labelLarge = base.labelLarge.copy(fontSize = base.labelLarge.fontSize * s),
        labelMedium = base.labelMedium.copy(fontSize = base.labelMedium.fontSize * s),
        labelSmall = base.labelSmall.copy(fontSize = base.labelSmall.fontSize * s)
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
