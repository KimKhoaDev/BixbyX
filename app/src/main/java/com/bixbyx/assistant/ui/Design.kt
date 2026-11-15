package com.bixbyx.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.bixbyx.assistant.glass.LiquidButton
import com.bixbyx.assistant.glass.LiquidToggle
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Three separate looks. Each one has its own pages, cards, rows, dropdowns, dialogs, fields, switches and buttons.
 * The design is chosen once and the app restarts to apply it.
 */
enum class Design { Miuix, Glass, One }

val LocalDesign = staticCompositionLocalOf { Design.Miuix }

// ------------------------------------------------------------------ section title
@Composable
fun Section(text: String) {
    val c = MiuixTheme.colorScheme
    when (LocalDesign.current) {
        Design.Miuix -> SmallTitle(text)
        Design.Glass -> Text(text.uppercase(), Modifier.padding(start = 28.dp, top = 14.dp, bottom = 8.dp), fontSize = 12.sp, letterSpacing = 1.2.sp, color = c.onSurfaceVariantSummary)
        Design.One -> Text(text, Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
    }
}

// ------------------------------------------------------------------ rows
@Composable
private fun RowBase(title: String, summary: String?, onClick: (() -> Unit)?, end: (@Composable RowScope.() -> Unit)?,
                    hPad: Dp, vPad: Dp, titleSize: TextUnit, round: Dp) {
    val c = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(round)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = hPad, vertical = vPad), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (title.isNotEmpty()) Text(title, fontSize = titleSize, color = c.onSurface)
            if (!summary.isNullOrEmpty()) Text(summary, fontSize = 13.sp, color = c.onSurfaceVariantSummary)
        }
        end?.invoke(this)
        if (onClick != null) Icon(MiuixIcons.ChevronForward, null, Modifier.size(18.dp), tint = c.onSurfaceVariantActions)
    }
}

/** A list row, with a chevron when it can be tapped. */
@Composable
fun Pref(title: String = "", summary: String? = null, onClick: (() -> Unit)? = null, endActions: (@Composable RowScope.() -> Unit)? = null) {
    when (LocalDesign.current) {
        Design.Miuix -> when {
            onClick != null && endActions != null -> ArrowPreference(title, summary = summary, onClick = onClick, endActions = endActions)
            onClick != null -> ArrowPreference(title, summary = summary, onClick = onClick)
            endActions != null -> BasicComponent(title = title.takeIf { it.isNotEmpty() }, summary = summary, endActions = endActions)
            else -> BasicComponent(title = title.takeIf { it.isNotEmpty() }, summary = summary)
        }
        Design.Glass -> RowBase(title, summary, onClick, endActions, 20.dp, 14.dp, 16.sp, 18.dp)
        Design.One -> RowBase(title, summary, onClick, endActions, 24.dp, 16.dp, 17.sp, 0.dp)
    }
}

// ------------------------------------------------------------------ dropdown
private class BelowAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width - 16).coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
        val y = if (anchorBounds.bottom + popupContentSize.height > windowSize.height - 16) (anchorBounds.top - popupContentSize.height).coerceAtLeast(8) else anchorBounds.bottom
        return IntOffset(x, y)
    }
}

/** A row that opens a menu. Miuix uses its own popup; the other designs draw their own. */
@Composable
fun PrefDropdown(items: List<String>, selectedIndex: Int, title: String, onSelectedIndexChange: (Int) -> Unit) {
    val design = LocalDesign.current
    if (design == Design.Miuix) { OverlayDropdownPreference(items, selectedIndex, title, onSelectedIndexChange = onSelectedIndexChange); return }
    val c = MiuixTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val glass = design == Design.Glass
    Box {
        RowBase(title, null, { open = true }, { Text(items.getOrElse(selectedIndex) { "" }, Modifier.padding(end = 4.dp), color = c.primary, fontSize = 15.sp) },
            if (glass) 20.dp else 24.dp, if (glass) 14.dp else 16.dp, if (glass) 16.sp else 17.sp, if (glass) 18.dp else 0.dp)
        if (open) Popup(BelowAnchor(), onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
            val shape = RoundedCornerShape(if (glass) 24.dp else 16.dp)
            Column(Modifier.widthIn(min = 180.dp, max = 280.dp).heightIn(max = 360.dp).shadow(if (glass) 18.dp else 10.dp, shape)
                .clip(shape).background(c.surfaceContainer.copy(alpha = if (glass) .92f else 1f))
                .then(if (glass) Modifier.border(1.dp, Color.White.copy(alpha = .22f), shape) else Modifier)
                .verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                items.forEachIndexed { i, s ->
                    Text(s, Modifier.fillMaxWidth().clickable { onSelectedIndexChange(i); open = false }.padding(horizontal = 20.dp, vertical = 13.dp),
                        color = if (i == selectedIndex) c.primary else c.onSurface, fontWeight = if (i == selectedIndex) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ text field
@Composable
fun Input(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = "", singleLine: Boolean = false,
          minLines: Int = 1, enabled: Boolean = true, secret: Boolean = false) {
    val c = MiuixTheme.colorScheme
    val vt = if (secret) PasswordVisualTransformation() else VisualTransformation.None
    val style = MiuixTheme.textStyles.body1.copy(color = c.onSurface)
    when (LocalDesign.current) {
        Design.Miuix -> TextField(value, onValueChange, modifier, label = label, singleLine = singleLine, minLines = minLines, enabled = enabled, visualTransformation = vt)
        Design.Glass -> {
            val shape = RoundedCornerShape(24.dp)
            BasicTextField(value, onValueChange, modifier.clip(shape).background(c.onSurface.copy(alpha = .07f)).border(1.dp, Color.White.copy(alpha = .18f), shape)
                .padding(horizontal = 18.dp, vertical = 14.dp), enabled = enabled, singleLine = singleLine, minLines = minLines, textStyle = style,
                cursorBrush = SolidColor(c.primary), visualTransformation = vt,
                decorationBox = { inner -> if (value.isEmpty()) Text(label, color = c.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body1); inner() })
        }
        Design.One -> Column(modifier) {
            if (label.isNotEmpty()) Text(label, fontSize = 12.sp, color = c.primary)
            BasicTextField(value, onValueChange, Modifier.fillMaxWidth().padding(vertical = 6.dp), enabled = enabled, singleLine = singleLine, minLines = minLines,
                textStyle = style, cursorBrush = SolidColor(c.primary), visualTransformation = vt)
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(c.primary.copy(alpha = .55f)))
        }
    }
}

// ------------------------------------------------------------------ dialog
@Composable
fun AppDialog(show: Boolean, title: String? = null, summary: String? = null, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val design = LocalDesign.current
    if (design == Design.Miuix) { OverlayDialog(show = show, title = title, summary = summary, onDismissRequest = onDismissRequest) { Column { content() } }; return }
    if (!show) return
    val c = MiuixTheme.colorScheme
    val glass = design == Design.Glass
    Dialog(onDismissRequest = onDismissRequest) {
        val shape = RoundedCornerShape(if (glass) 32.dp else 28.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(c.surfaceContainer.copy(alpha = if (glass) .94f else 1f))
            .then(if (glass) Modifier.border(1.dp, Color.White.copy(alpha = .22f), shape) else Modifier).padding(24.dp)) {
            if (title != null) Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
            if (summary != null) Text(summary, Modifier.padding(top = 6.dp), color = c.onSurfaceVariantSummary)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** A button inside a dialog or row. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val c = MiuixTheme.colorScheme
    when (LocalDesign.current) {
        Design.Miuix -> TextButton(text, onClick, modifier, colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors())
        Design.Glass -> Box(modifier.clip(CircleShape).background(if (primary) c.primary.copy(alpha = .9f) else c.onSurface.copy(alpha = .10f)).clickable(onClick = onClick).padding(vertical = 12.dp),
            contentAlignment = Alignment.Center) { Text(text, color = if (primary) c.onPrimary else c.onSurface) }
        Design.One -> Box(modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center) { Text(text, color = c.primary, fontWeight = FontWeight.SemiBold) }
    }
}

// ------------------------------------------------------------------ switch and button
@Composable
fun OneSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MiuixTheme.colorScheme
    val track by animateColorAsState(if (checked) c.primary else c.onSurface.copy(alpha = .28f), tween(220), label = "track")
    val x by animateDpAsState(if (checked) 23.dp else 3.dp, spring(dampingRatio = .7f, stiffness = 500f), label = "thumb")
    Box(Modifier.size(52.dp, 30.dp).clip(CircleShape).background(track)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }) {
        Box(Modifier.offset(x = x).align(Alignment.CenterStart).size(24.dp).clip(CircleShape).background(Color.White))
    }
}

/** A switch row: Miuix switch, liquid glass toggle, or One UI switch. */
@Composable
fun ThemedSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, title: String, summary: String? = null) {
    val design = LocalDesign.current
    val c = MiuixTheme.colorScheme
    if (design == Design.Miuix) { SwitchPreference(checked, onCheckedChange, title, summary = summary); return }
    val wall = LocalWall.current
    Row(Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCheckedChange(!checked) }
        .padding(horizontal = if (design == Design.Glass) 20.dp else 24.dp, vertical = if (design == Design.Glass) 14.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = if (design == Design.Glass) 16.sp else 17.sp, color = c.onSurface)
            if (summary != null) Text(summary, fontSize = 13.sp, color = c.onSurfaceVariantSummary)
        }
        Spacer(Modifier.width(12.dp))
        if (design == Design.Glass && wall != null) LiquidToggle({ checked }, onCheckedChange, wall) else OneSwitch(checked, onCheckedChange)
    }
}

/** A button: Miuix button, liquid glass button, or One UI pill. */
@Composable
fun ThemedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val design = LocalDesign.current
    val c = MiuixTheme.colorScheme
    val wall = LocalWall.current
    when {
        design == Design.Miuix -> if (primary) Button(onClick, modifier, colors = ButtonDefaults.buttonColorsPrimary()) { Text(text, color = c.onPrimary) } else Button(onClick, modifier) { Text(text) }
        design == Design.Glass && wall != null -> LiquidButton(onClick, wall, modifier, tint = if (primary) c.primary else Color.Unspecified,
            surfaceColor = if (primary) Color.Unspecified else c.surfaceContainer.copy(alpha = .35f)) {
            Text(text, color = if (primary) c.onPrimary else c.onSurface, style = MiuixTheme.textStyles.button)
        }
        else -> {
            var pressed by remember { mutableStateOf(false) }
            val s by animateFloatAsState(if (pressed) .96f else 1f, spring(dampingRatio = .6f, stiffness = 500f), label = "press")
            Box(modifier.height(48.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(RoundedCornerShape(50))
                .background(if (primary) c.primary else c.onSurface.copy(alpha = .08f))
                .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() }) },
                contentAlignment = Alignment.Center) { Text(text, color = if (primary) c.onPrimary else c.primary, fontWeight = FontWeight.SemiBold) }
        }
    }
}
