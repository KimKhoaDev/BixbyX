package com.bixbyx.assistant.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bixbyx.assistant.R
import com.bixbyx.assistant.agent.BState
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.agent.StepState
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Floating Bixby icon. Idle: floats gently with a soft halo. Listening: ripples that follow your voice.
 * Thinking or working: a spinning gradient ring. When a task finishes it pops once.
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
                BState.Idle -> drawCircle(BixbyBlue.copy(alpha = .26f * (1f - halo)), r * (.62f + .38f * halo), center)
                BState.Listening -> for (i in 0..2) {
                    val p = (halo + i / 3f) % 1f
                    drawCircle(BixbyBlue.copy(alpha = .5f * (1f - p)), r * (.55f + .45f * p), center, style = Stroke(3.dp.toPx()))
                }
                else -> rotate(spin, center) { drawCircle(Brush.sweepGradient(GlowColors, center), r * .62f, center, style = Stroke(4.dp.toPx())) }
            }
        }
        Image(painterResource(R.drawable.ic_bixby_logo), "Bixby", Modifier.size(52.dp).graphicsLayer { scaleX = logo; scaleY = logo })
    }
}

@Composable
private fun StepDots() {
    val steps by BixbyEngine.steps.collectAsState()
    val t = rememberInfiniteTransition(label = "dots")
    val pulse by t.animateFloat(.5f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "p")
    val c = MiuixTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.takeLast(8).forEach { s ->
            val w by animateDpAsState(if (s.state == StepState.Running) 14.dp else 5.dp, spring(dampingRatio = .6f), label = "w")
            Box(Modifier.size(w, 4.dp).clip(CircleShape).background(when (s.state) {
                StepState.Running -> c.primary.copy(alpha = pulse); StepState.Done -> c.onSurfaceVariantSummary; StepState.Failed -> c.error }))
        }
    }
}

/** Compact bar at the top of the screen while Bixby listens or works inside another app. */
@Composable
fun TopStepBar(visible: Boolean, onOpen: () -> Unit) {
    val c = MiuixTheme.colorScheme
    val state by BixbyEngine.state.collectAsState()
    val status by BixbyEngine.status.collectAsState()
    val partial by BixbyEngine.partial.collectAsState()
    val ts = remember { MutableTransitionState(false) }
    ts.targetState = visible
    AnimatedVisibility(ts,
        enter = slideInVertically(spring(dampingRatio = .7f, stiffness = 380f)) { -it } + fadeIn() + scaleIn(initialScale = .85f),
        exit = slideOutVertically(tween(280)) { -it } + fadeOut(tween(220)) + scaleOut(targetScale = .9f)) {
        Box(Modifier.padding(16.dp)) {
            Row(Modifier.widthIn(max = 320.dp).height(50.dp).glowBar(true, c.surfaceContainer).clickable { onOpen() }.padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BixbyOrb(state, 30.dp, hole = c.surfaceContainer)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (state == BState.Listening) Text(partial.ifBlank { "Listening..." }, maxLines = 1, style = MiuixTheme.textStyles.body2)
                    else AnimatedContent(status.ifBlank { "Working" }, transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                        label = "bar") { Text(it, maxLines = 1, style = MiuixTheme.textStyles.body2) }
                    if (state != BState.Listening) StepDots()
                }
                if (state == BState.Listening) Waveform(true) else CircularProgressIndicator(size = 16.dp, strokeWidth = 2.dp)
                IconButton({ BixbyEngine.stop() }) { Icon(MiuixIcons.Close, "Stop") }
            }
        }
    }
}
