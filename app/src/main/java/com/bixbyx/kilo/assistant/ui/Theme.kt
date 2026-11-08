package com.bixbyx.kilo.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val OneBlue = Color(0xFF3E91FF)
val OnePurple = Color(0xFF8E5BFF)
val OnePink = Color(0xFFFF5FA2)
val OneCyan = Color(0xFF35D0E8)

/** "system" | "dark" | "light" */
fun isDark(mode: String): Boolean = when (mode) {
    "dark" -> true
    "light" -> false
    else -> androidx.compose.foundation.isSystemInDarkTheme()
}

@Composable
fun OneUITheme(content: @Composable () -> Unit, darkMode: String = "system") {
    val dark = isDark(darkMode)
    val cs = if (dark) darkColorScheme(primary = OneBlue, background = Color(0xFF000000), surface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFF2C2C2E), onBackground = Color.White, onSurface = Color.White)
    else lightColorScheme(primary = OneBlue, background = Color(0xFFF6F6F6), surface = Color.White,
        surfaceVariant = Color(0xFFEDEDED), onBackground = Color(0xFF111111), onSurface = Color(0xFF111111))
    MaterialTheme(colorScheme = cs, shapes = Shapes(
        small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(26.dp)), content = content)
}

