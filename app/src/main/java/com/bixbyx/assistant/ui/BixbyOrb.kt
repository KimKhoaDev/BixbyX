package com.bixbyx.assistant.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bixbyx.assistant.agent.BState

/** The Bixby ring: rotating gradient, breathes when idle, pulses fast while listening, spins while working. */
@Composable
fun BixbyOrb(state: BState, size: Dp = 140.dp, modifier: Modifier = Modifier,
             hole: Color = MaterialTheme.colorScheme.background) {
    val t = rememberInfiniteTransition(label = "orb")
    val ms = when (state) { BState.Idle -> 7000; BState.Listening -> 3200; else -> 1300 }
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(ms, easing = LinearEasing)), label = "rot")
    val pulse by t.animateFloat(0.93f, 1.07f, infiniteRepeatable(
        tween(if (state == BState.Listening) 550 else 2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val colors = listOf(OneBlue, OnePurple, OnePink, OneCyan, OneBlue)
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = Offset(this.size.width / 2, this.size.height / 2)
        drawCircle(Brush.radialGradient(listOf(OneBlue.copy(alpha = .30f), Color.Transparent), c, r), r * pulse, c)
        rotate(rot, c) { drawCircle(Brush.sweepGradient(colors, c), r * .70f * pulse, c) }
        drawCircle(hole, r * .46f * pulse, c)
        rotate(-rot * 1.6f, c) { drawCircle(Brush.sweepGradient(colors.reversed(), c), r * .20f * pulse, c) }
    }
}
