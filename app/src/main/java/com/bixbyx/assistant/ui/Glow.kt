package com.bixbyx.assistant.ui

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint as FrameworkPaint
import android.graphics.Shader
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val EdgeColors = listOf(BixbyBlue, BixbyPurple, BixbyPink)

/** A slow breathing value, only while the bar is working. No spinning and no drifting colours. */
@Composable
private fun rememberBreath(active: Boolean): State<Float>? {
    if (!active) return null
    val t = rememberInfiniteTransition(label = "breath")
    return t.animateFloat(.45f, .95f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k")
}

/** One smooth blurred glow along the edge of the pill. */
private fun DrawScope.softGlow(k: Float) {
    val w = size.width
    val h = size.height
    drawIntoCanvas { canvas ->
        val p = FrameworkPaint().apply {
            isAntiAlias = true
            strokeWidth = 3.dp.toPx()
            alpha = (k * 255).toInt()
            shader = LinearGradient(
                0f, 0f, w, 0f,
                EdgeColors.map { c ->
                    android.graphics.Color.argb(
                        (c.alpha * 255).toInt(),
                        (c.red * 255).toInt(),
                        (c.green * 255).toInt(),
                        (c.blue * 255).toInt()
                    )
                }.toTypedArray(),
                null,
                Shader.TileMode.CLAMP
            )
        }
        p.maskFilter = BlurMaskFilter(9.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
        canvas.nativeCanvas.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, p)
    }
}

private fun DrawScope.edge() {
    drawRoundRect(Brush.horizontalGradient(EdgeColors), cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.5.dp.toPx()))
}

/** Glow behind the pill. Only while Bixby is thinking or doing something. */
fun Modifier.glowAura(active: Boolean): Modifier = composed {
    val k = rememberBreath(active)
    if (k == null) Modifier else Modifier.drawBehind { softGlow(k.value) }
}

/** The edge over the glass: a faint hairline normally, the colour gradient while working. */
fun Modifier.glowRim(active: Boolean): Modifier = composed {
    val line = MiuixTheme.colorScheme.onSurface.copy(alpha = .12f)
    Modifier.drawWithContent {
        drawContent()
        if (active) edge() else drawRoundRect(line, cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.dp.toPx()))
    }
}

/** Flat pill with the same behaviour, for places that have no backdrop to refract (overlay windows). */
fun Modifier.glowBar(active: Boolean, fill: Color): Modifier = composed {
    val line = MiuixTheme.colorScheme.onSurface.copy(alpha = .12f)
    val k = rememberBreath(active)
    Modifier.drawBehind {
        if (k != null) softGlow(k.value)
        drawRoundRect(fill, cornerRadius = CornerRadius(size.height / 2))
        if (k != null) edge() else drawRoundRect(line, cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.dp.toPx()))
    }
}
