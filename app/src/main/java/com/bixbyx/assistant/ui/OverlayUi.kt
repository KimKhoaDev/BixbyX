package com.bixbyx.assistant.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bixbyx.assistant.R
import com.bixbyx.assistant.agent.BState
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.agent.StepState

/**
 * Floating Bixby icon.
 * Idle: floats gently with a soft pulsing halo.  Listening: ripples that follow your voice.
 * Thinking / working: a spinning gradient ring.  Finishing a task: a little "pop".
 */
@Composable
fun FloatingBubble(state: BState) {
    val level by BixbyEngine.level.collectAsState()
    val t = rememberInfiniteTransition(label = "bub")
    val bob by t.animateFloat(-3f, 3f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val halo by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "halo")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin")
    val pop = remember { Animatable(1f) }
    var prev by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        if (prev != BState.Idle && state == BState.Idle) {
            pop.animateTo(1.3f, spring(dampingRatio = .35f, stiffness = 500f))
            pop.animateTo(1f, spring(dampingRatio = .5f))
        }
        prev = state
    }
    val logo = if (state == BState.Listening) 1f + level * .25f else 1f
    Box(Modifier.padding(8.dp).size(84.dp).graphicsLayer {
        translationY = if (state == BState.Idle) bob * density else 0f
        scaleX = pop.value; scaleY = pop.value
    }, Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            when (state) {
                BState.Idle -> drawCircle(OneBlue.copy(alpha = .28f * (1f - halo)), r * (.62f + .38f * halo), center)
                BState.Listening -> for (i in 0..2) {
                    val p = (halo + i / 3f) % 1f
                    drawCircle(OneBlue.copy(alpha = .5f * (1f - p)), r * (.55f + .45f * p), center, style = Stroke(3.dp.toPx()))
                }
                else -> rotate(spin, center) {
                    drawCircle(Brush.sweepGradient(GlowColors, center), r * .62f, center, style = Stroke(4.dp.toPx()))
                }
            }
        }
        Image(painterResource(R.drawable.ic_bixby_logo), "Bixby", Modifier.size(52.dp).graphicsLayer { scaleX = logo; scaleY = logo })
    }
}

/** Little dots: finished steps solid, the running one stretches and pulses. */
@Composable
private fun StepDots() {
    val steps by BixbyEngine.steps.collectAsState()
    val t = rememberInfiniteTransition(label = "dots")
    val pulse by t.animateFloat(.5f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "p")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.takeLast(8).forEach { s ->
            val w by animateDpAsState(if (s.state == StepState.Running) 16.dp else 6.dp, spring(dampingRatio = .6f), label = "w")
            Box(Modifier.size(w, 5.dp).clip(CircleShape).background(when (s.state) {
                StepState.Running -> OneBlue.copy(alpha = pulse); StepState.Done -> Color.White.copy(.9f); StepState.Failed -> OneRed }))
        }
    }
}

/** "Dynamic island" style bar at the top of the screen while Bixby listens or works inside other apps. */
@Composable
fun TopStepBar(visible: Boolean, onOpen: () -> Unit) {
    val state by BixbyEngine.state.collectAsState()
    val status by BixbyEngine.status.collectAsState()
    val partial by BixbyEngine.partial.collectAsState()
    val ts = remember { MutableTransitionState(false) }
    ts.targetState = visible
    AnimatedVisibility(ts,
        enter = slideInVertically(spring(dampingRatio = .65f, stiffness = 380f)) { -it } + fadeIn() + scaleIn(initialScale = .8f),
        exit = slideOutVertically(tween(280)) { -it } + fadeOut(tween(220)) + scaleOut(targetScale = .85f)) {
        Box(Modifier.padding(22.dp)) {
            Row(Modifier.widthIn(max = 330.dp).height(58.dp).glowRim(true).clickable { onOpen() }.padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BixbyOrb(state, 36.dp, hole = BarFill)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state == BState.Listening) Text(partial.ifBlank { "Listening..." }, color = Color.White, fontSize = 15.sp, maxLines = 1)
                    else AnimatedContent(status.ifBlank { "Working..." },
                        transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                        label = "bar") { Text(it, color = Color.White, fontSize = 15.sp, maxLines = 1) }
                    if (state != BState.Listening) StepDots()
                }
                if (state == BState.Listening) Waveform(true) else CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                IconButton({ BixbyEngine.stop() }) { Icon(Icons.Default.Close, "Stop", tint = Color.White) }
            }
        }
    }
}
