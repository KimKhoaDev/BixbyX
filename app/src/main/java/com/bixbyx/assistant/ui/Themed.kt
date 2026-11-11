package com.bixbyx.assistant.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bixbyx.assistant.glass.LiquidButton
import com.bixbyx.assistant.glass.LiquidToggle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A switch row: the Miuix one in the Miuix design, a liquid glass toggle in the Liquid glass design. */
@Composable
fun ThemedSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, title: String, summary: String? = null) {
    val wall = LocalWall.current
    val c = MiuixTheme.colorScheme
    if (wall == null) {
        SwitchPreference(checked, onCheckedChange, title, summary = summary)
    } else {
        Row(Modifier.fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MiuixTheme.textStyles.body1)
                if (summary != null) Text(summary, color = c.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
            }
            Spacer(Modifier.width(12.dp))
            LiquidToggle({ checked }, onCheckedChange, wall)
        }
    }
}

/** A button: Miuix in the Miuix design, a liquid glass button (tinted when primary) in the Liquid glass design. */
@Composable
fun ThemedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val wall = LocalWall.current
    val c = MiuixTheme.colorScheme
    if (wall == null) {
        if (primary) Button(onClick, modifier, colors = ButtonDefaults.buttonColorsPrimary()) { Text(text, color = c.onPrimary) }
        else Button(onClick, modifier) { Text(text) }
    } else {
        LiquidButton(onClick, wall, modifier, tint = if (primary) c.primary else Color.Unspecified,
            surfaceColor = if (primary) Color.Unspecified else c.surfaceContainer.copy(alpha = .35f)) {
            Text(text, color = if (primary) c.onPrimary else c.onSurface, style = MiuixTheme.textStyles.button)
        }
    }
}
