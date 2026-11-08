package com.bixbyx.assistant.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bixbyx.assistant.agent.BState
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.agent.Msg

fun runQuick(s: String) { if (s.startsWith("What's on")) BixbyEngine.describeScreen() else BixbyEngine.send(s) }

fun LazyListScope.messageItems(list: List<Msg>, glass: Boolean = false) {
    items(list, key = { it.ts.toString() + it.user + it.text.length }) { m ->
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(shown, enter = fadeIn() + slideInVertically(spring(dampingRatio = .75f, stiffness = 380f)) { it / 2 } + scaleIn(initialScale = .9f)) {
            val bubble = when { m.user -> OneBlue; glass -> Color(0xE61C1C1E); else -> MaterialTheme.colorScheme.surface }
            val fg = when { m.user || glass -> Color.White; else -> MaterialTheme.colorScheme.onSurface }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.user) Arrangement.End else Arrangement.Start) {
                Text(m.text, fontSize = 16.sp, color = fg,
                    modifier = Modifier.widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(22.dp, 22.dp, if (m.user) 6.dp else 22.dp, if (m.user) 22.dp else 6.dp))
                        .background(bubble).padding(horizontal = 16.dp, vertical = 11.dp))
            }
        }
    }
}

@Composable
fun QuickChips(onPick: (String) -> Unit = ::runQuick) {
    val chips = listOf("What's on my screen?", "Turn on flashlight", "Open YouTube", "Set an alarm for 7:00", "Turn off Wi-Fi", "Set volume to 40%")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        itemsIndexed(chips) { i, c ->
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(i * 60L); shown = true }
            AnimatedVisibility(shown, enter = fadeIn() + slideInHorizontally { it / 2 } + scaleIn(initialScale = .8f)) {
                Text(c, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                        .clickable { onPick(c) }.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

/** Bars that dance while Bixby listens; follows your real voice level when available. */
@Composable
fun Waveform(active: Boolean, modifier: Modifier = Modifier) {
    val lv by BixbyEngine.level.collectAsState()
    val t = rememberInfiniteTransition(label = "wave")
    val durations = listOf(520, 780, 430, 690, 560)
    val hs = durations.mapIndexed { i, d ->
        t.animateFloat(.25f, 1f, infiniteRepeatable(tween(d, easing = FastOutSlowInEasing), RepeatMode.Reverse,
            initialStartOffset = StartOffset(i * 90)), label = "w$i")
    }
    Canvas(modifier.size(46.dp, 32.dp)) {
        val bw = 5.dp.toPx()
        val gap = (size.width - 5 * bw) / 4
        val brush = Brush.verticalGradient(listOf(OneCyan, OneBlue, OnePurple))
        hs.forEachIndexed { i, s ->
            val f = if (active) (.2f + .8f * s.value * (if (lv > .03f) lv.coerceIn(.25f, 1f) else 1f)) else .2f + .1f * s.value
            val bh = size.height * f
            drawRoundRect(brush, Offset(i * (bw + gap), (size.height - bh) / 2), Size(bw, bh), CornerRadius(bw / 2))
        }
    }
}

/** The Bixby bar: dark pill, glowing rim, orb, live text, waveform, grab handle. */
@Composable
fun GlowBar(state: BState, status: String, partial: String, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    val notice by BixbyEngine.notice.collectAsState()
    val active = state != BState.Idle
    var text by remember { mutableStateOf("") }
    Box(modifier.fillMaxWidth().height(66.dp).glowRim(active)) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(30.dp, 3.dp).clip(CircleShape).background(Color.White.copy(.65f)))
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BixbyOrb(state, 42.dp, hole = BarFill)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                when (state) {
                    BState.Idle -> BasicTextField(text, { text = it }, Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.White, fontSize = 17.sp), cursorBrush = SolidColor(OneBlue), singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { onSend(text); text = "" } }),
                        decorationBox = { inner ->
                            if (text.isEmpty()) Text(notice.ifBlank { "Talk or type to Bixby" }, maxLines = 2, fontSize = if (notice.isBlank()) 17.sp else 13.sp,
                                color = if (notice.isBlank()) Color.White.copy(.5f) else OneOrange)
                            inner()
                        })
                    BState.Listening -> Text(partial.ifBlank { "Listening..." }, color = Color.White, fontSize = 17.sp, maxLines = 1)
                    else -> AnimatedContent(status.ifBlank { "Working..." },
                        transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                        label = "status") { Text(it, color = Color.White, fontSize = 16.sp, maxLines = 2) }
                }
            }
            when {
                state == BState.Idle && text.isNotBlank() ->
                    IconButton({ onSend(text); text = "" }) { Icon(Icons.Default.ArrowUpward, "Send", tint = Color.White) }
                state == BState.Idle ->
                    IconButton({ BixbyEngine.listen() }) { Icon(Icons.Default.Mic, "Talk", tint = Color.White) }
                else -> Waveform(state == BState.Listening, Modifier.clickable(indication = null,
                    interactionSource = remember { MutableInteractionSource() }) { BixbyEngine.stop() })
            }
        }
    }
}

@Composable
fun InputBar(onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by BixbyEngine.state.collectAsState()
    val partial by BixbyEngine.partial.collectAsState()
    val status by BixbyEngine.status.collectAsState()
    GlowBar(state, status, partial, onSend, modifier.padding(horizontal = 18.dp, vertical = 22.dp))
}

/** Assist popup / overlay panel: messages float above the glowing bar. Slides + fades in and out. */
@Composable
fun BixbySheet(visible: Boolean, onDismiss: () -> Unit) {
    val msgs by BixbyEngine.messages.collectAsState()
    val state by BixbyEngine.state.collectAsState()
    val status by BixbyEngine.status.collectAsState()
    val partial by BixbyEngine.partial.collectAsState()
    val steps by BixbyEngine.steps.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.lastIndex) }
    val ts = remember { MutableTransitionState(false) }
    ts.targetState = visible
    val scrim by animateFloatAsState(if (visible) 1f else 0f, tween(300), label = "scrim")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.6f * scrim))))
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(ts,
            enter = slideInVertically(spring(dampingRatio = .75f, stiffness = 320f)) { it / 2 } + fadeIn(tween(220)) + scaleIn(initialScale = .94f),
            exit = slideOutVertically(tween(260)) { it / 2 } + fadeOut(tween(200)) + scaleOut(targetScale = .94f)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (steps.isNotEmpty()) StepsCard(steps, state, Modifier.padding(horizontal = 16.dp))
                if (msgs.isEmpty() && steps.isEmpty()) QuickChips()
                else LazyColumn(Modifier.heightIn(max = 300.dp), state = list, contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) { messageItems(msgs.takeLast(6), glass = true) }
                GlowBar(state, status, partial, { BixbyEngine.send(it) },
                    Modifier.padding(horizontal = 18.dp, vertical = 22.dp).clickable(enabled = false) {})
            }
        }
    }
}
