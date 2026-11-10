package com.bixbyx.assistant.ui

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Blue to purple to pink, mirrored so it can drift sideways without a seam. It never rotates. */
private fun edgeBrush(w: Float, drift: Float): Brush {
    val shift = drift * w
    return Brush.linearGradient(listOf(BixbyBlue, BixbyPurple, BixbyPink, BixbyPurple, BixbyBlue),
        start = Offset(-shift, 0f), end = Offset(w - shift, 0f), tileMode = TileMode.Mirror)
}

/** The area between two rounded rects (positive grows outward, negative insets), filled with the brush. */
private fun DrawScope.band(w: Float, h: Float, outer: Float, inner: Float, brush: Brush, alpha: Float) {
    val r = h / 2
    val p = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(RoundRect(-outer, -outer, w + outer, h + outer, CornerRadius(r + outer)))
        addRoundRect(RoundRect(-inner, -inner, w + inner, h + inner, CornerRadius((r + inner).coerceAtLeast(0f))))
    }
    clipPath(p) { drawRect(brush, Offset(-outer - 1f, -outer - 1f), Size(w + 2 * outer + 2f, h + 2 * outer + 2f), alpha = alpha) }
}

private fun DrawScope.aura(w: Float, h: Float, brush: Brush, k: Float) {
    val g = density
    band(w, h, 10 * g, 6 * g, brush, .05f * k); band(w, h, 6 * g, 3 * g, brush, .10f * k); band(w, h, 3 * g, 0f, brush, .22f * k)
}

@Composable
private fun rememberEdge(active: Boolean): Pair<State<Float>, State<Float>>? {
    if (!active) return null
    val t = rememberInfiniteTransition(label = "edge")
    val drift = t.animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "drift")
    val k = t.animateFloat(.55f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k")
    return drift to k
}

/** Soft glow around the pill. Drawn behind the glass. Only while running. */
fun Modifier.glowAura(active: Boolean): Modifier = composed {
    val e = rememberEdge(active)
    if (e == null) Modifier else Modifier.drawBehind { aura(size.width, size.height, edgeBrush(size.width, e.first.value), e.second.value) }
}

/** Thin edge drawn over the glass: a hairline when idle, the gradient while running. */
fun Modifier.glowRim(active: Boolean): Modifier = composed {
    val line = MiuixTheme.colorScheme.onSurface.copy(alpha = .12f)
    val e = rememberEdge(active)
    Modifier.drawWithContent {
        drawContent()
        if (e == null) drawRoundRect(line, cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.dp.toPx()))
        else band(size.width, size.height, 0f, -1.6f * density, edgeBrush(size.width, e.first.value), 1f)
    }
}

/** Flat pill with the same edge, for places that have no backdrop to refract (overlay windows). */
fun Modifier.glowBar(active: Boolean, fill: Color): Modifier = composed {
    val line = MiuixTheme.colorScheme.onSurface.copy(alpha = .12f)
    val e = rememberEdge(active)
    Modifier.drawBehind {
        val r = CornerRadius(size.height / 2)
        if (e != null) aura(size.width, size.height, edgeBrush(size.width, e.first.value), e.second.value)
        drawRoundRect(fill, cornerRadius = r)
        if (e == null) drawRoundRect(line, cornerRadius = r, style = Stroke(1.dp.toPx()))
        else band(size.width, size.height, 0f, -1.6f * density, edgeBrush(size.width, e.first.value), 1f)
    }
}
