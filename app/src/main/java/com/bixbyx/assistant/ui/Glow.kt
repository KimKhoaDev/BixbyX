package com.bixbyx.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bixbyx.assistant.agent.BState
import com.bixbyx.assistant.agent.Step
import com.bixbyx.assistant.agent.StepState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Pill background. Idle: flat with a hairline outline. Running: a thin gradient rim
 * that rotates, with a soft glow just outside it.
 */
fun Modifier.glowBar(active: Boolean, fill: Color): Modifier = composed {
    val line = MiuixTheme.colorScheme.onSurface.copy(alpha = .12f)
    if (!active) {
        Modifier.drawBehind {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(fill, cornerRadius = r)
            drawRoundRect(line, cornerRadius = r, style = Stroke(1.dp.toPx()))
        }
    } else {
        val t = rememberInfiniteTransition(label = "rim")
        val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "rot")
        val k by t.animateFloat(.55f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k")
        Modifier.drawBehind {
            val w = size.width; val h = size.height; val r = h / 2; val c = center
            val sweep = Brush.sweepGradient(GlowColors, c)
            val reach = maxOf(w, h)
            fun band(outer: Float, inner: Float, alpha: Float) {
                val p = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRoundRect(RoundRect(-outer, -outer, w + outer, h + outer, CornerRadius(r + outer)))
                    addRoundRect(RoundRect(-inner, -inner, w + inner, h + inner, CornerRadius((r + inner).coerceAtLeast(0f))))
                }
                clipPath(p) { rotate(rot, c) { drawCircle(sweep, reach, c, alpha = alpha) } }
            }
            val g = density
            band(10 * g, 6 * g, .05f * k); band(6 * g, 3 * g, .10f * k); band(3 * g, 0f, .22f * k)
            drawRoundRect(fill, cornerRadius = CornerRadius(r))
            band(0f, -1.6f * g, 1f)
        }
    }
}

