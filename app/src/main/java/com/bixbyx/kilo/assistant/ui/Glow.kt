package com.bixbyx.kilo.assistant.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bixbyx.kilo.assistant.agent.BState
import com.bixbyx.kilo.assistant.agent.Step
import com.bixbyx.kilo.assistant.agent.StepState

val BarFill = Color(0xF2090912)
val GlowColors = listOf(OneBlue, OnePurple, OnePink, OneCyan, OneBlue)

/** Dark pill body + rotating blue/purple/pink glowing rim that breathes. Shared by the bar, the top step bar, etc. */
fun Modifier.glowRim(active: Boolean, fill: Color = BarFill): Modifier = composed {
    val t = rememberInfiniteTransition(label = "glow")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 2200 else 7000, easing = LinearEasing)), label = "rot")
    val breathe by t.animateFloat(.6f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br")
    drawBehind {
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
        val k = if (active) breathe else .75f
        band(30 * g, 20 * g, .05f * k); band(20 * g, 12 * g, .09f * k)
        band(12 * g, 6 * g, .17f * k); band(6 * g, 0f, .38f * k)
        drawRoundRect(fill, cornerRadius = CornerRadius(r), size = size)
        band(0f, -2.5f * g, 1f)
    }
}

@Composable
private fun StepRow(s: Step) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { it / 2 } + expandVertically()) {
        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp), Alignment.Center) {
                Crossfade(s.state, label = "stepIcon") { st ->
                    when (st) {
                        StepState.Running -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        StepState.Done -> Icon(Icons.Default.Check, null, tint = OneGreen, modifier = Modifier.size(19.dp))
                        StepState.Failed -> Icon(Icons.Default.Close, null, tint = OneRed, modifier = Modifier.size(19.dp))
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(s.text, fontSize = 15.sp, maxLines = 2)
        }
    }
}

/** Step-by-step card: shows every action Bixby takes, live, with the current one spinning. */
@Composable
fun StepsCard(steps: List<Step>, state: BState, modifier: Modifier = Modifier) {
    val busy = state != BState.Idle
    var expanded by remember { mutableStateOf(true) }
    LaunchedEffect(busy) { expanded = busy }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, spring(dampingRatio = .6f), label = "arrow")
    MiuixCard(modifier.animateContentSize(spring(dampingRatio = .8f, stiffness = 400f))) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Check, null, tint = OneGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (busy) "Working Â· step ${steps.size.coerceAtLeast(1)}" else "${steps.size} steps completed",
                Modifier.weight(1f), fontSize = 16.sp)
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.rotate(arrow))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp).clip(CircleShape))
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 8.dp)) { steps.forEach { key(it.n) { StepRow(it) } } }
        }
    }
}

