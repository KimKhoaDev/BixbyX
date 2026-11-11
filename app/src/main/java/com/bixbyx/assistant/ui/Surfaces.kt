package com.bixbyx.assistant.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Backdrop that holds only the wallpaper, so glass panels can refract it without sampling themselves. Set in the Liquid glass design. */
val LocalWall = staticCompositionLocalOf<Backdrop?> { null }

/** Soft, slowly drifting colour behind the Liquid glass design. */
@Composable
fun GlassWallpaper(modifier: Modifier = Modifier) {
    val c = MiuixTheme.colorScheme
    val dark = isDark()
    val t = rememberInfiniteTransition(label = "wall")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(28000, easing = LinearEasing)), label = "a")
    Canvas(modifier.fillMaxSize()) {
        drawRect(c.background)
        val w = size.width; val h = size.height
        val ang = a * 2f * PI.toFloat()
        fun blob(color: Color, cx: Float, cy: Float, r: Float) =
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = if (dark) .40f else .30f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        blob(BixbyBlue, w * (.2f + .15f * cos(ang)), h * (.18f + .08f * sin(ang)), w * .85f)
        blob(BixbyPurple, w * (.85f + .10f * sin(ang)), h * (.50f + .10f * cos(ang)), w * .90f)
        blob(BixbyPink, w * (.30f + .12f * cos(ang + 2f)), h * (.90f + .05f * sin(ang)), w * .80f)
    }
}

/** A card. Miuix design: the Miuix Card. Liquid glass design: a glass panel over the wallpaper. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val wall = LocalWall.current
    val c = MiuixTheme.colorScheme
    if (wall == null) {
        if (onClick != null) Card(modifier, showIndication = true, onClick = onClick, onLongPress = onLongPress, content = content)
        else Card(modifier, content = content)
    } else {
        val shape = RoundedCornerShape(22.dp)
        val base = modifier.drawBackdrop(wall, { shape },
            effects = { vibrancy(); blur(10.dp.toPx()); lens(10.dp.toPx(), 18.dp.toPx()) },
            onDrawSurface = { drawRect(c.surfaceContainer.copy(alpha = .5f)) })
        val tap = if (onClick != null) base.clip(shape).pointerInput(onClick, onLongPress) {
            detectTapGestures(onLongPress = { onLongPress?.invoke() }, onTap = { onClick() })
        } else base
        Column(tap, content = content)
    }
}
