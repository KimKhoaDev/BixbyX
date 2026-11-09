package com.bixbyx.assistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.bixbyx.assistant.agent.BixbyEngine
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

val BixbyBlue = Color(0xFF3E91FF)
val BixbyPurple = Color(0xFF8E5BFF)
val BixbyPink = Color(0xFFFF5FA2)
val BixbyCyan = Color(0xFF35D0E8)
val GlowColors = listOf(BixbyBlue, BixbyPurple, BixbyPink, BixbyCyan, BixbyBlue)
val Green = Color(0xFF34C759)

val KeyColors: List<Pair<String, Color>> = listOf(
    "Blue" to Color(0xFF3482FF), "Green" to Color(0xFF36D167), "Purple" to Color(0xFF7C4DFF),
    "Yellow" to Color(0xFFFFB21D), "Orange" to Color(0xFFFF5722), "Pink" to Color(0xFFE91E63), "Teal" to Color(0xFF00BCD4),
)

/** Miuix theme driven by the appearance settings. */
@Composable
fun BixbyTheme(content: @Composable () -> Unit) {
    val look by BixbyEngine.look.collectAsState()
    val spec = ThemeColorSpec.entries.getOrNull(look.spec) ?: ThemeColorSpec.Spec2021
    val style = ThemePaletteStyle.entries.getOrNull(look.palette) ?: ThemePaletteStyle.TonalSpot
    val key = if (look.seed > 0) KeyColors.getOrNull(look.seed - 1)?.second else null
    val controller = remember(look.colorMode, key, spec, style) {
        when (look.colorMode) {
            1 -> ThemeController(ColorSchemeMode.Light)
            2 -> ThemeController(ColorSchemeMode.Dark)
            3 -> ThemeController(ColorSchemeMode.MonetSystem, keyColor = key, colorSpec = spec, paletteStyle = style)
            4 -> ThemeController(ColorSchemeMode.MonetLight, keyColor = key, colorSpec = spec, paletteStyle = style)
            5 -> ThemeController(ColorSchemeMode.MonetDark, keyColor = key, colorSpec = spec, paletteStyle = style)
            else -> ThemeController(ColorSchemeMode.System)
        }
    }
    MiuixTheme(controller = controller, content = content)
}

@Composable
fun isDark(): Boolean = MiuixTheme.colorScheme.background.luminance() < 0.5f
