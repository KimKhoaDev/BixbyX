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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bixbyx.assistant.agent.BState
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.agent.Msg

private val BarFill = Color(0xF2090912)
private val GlowColors = listOf(OneBlue, OnePurple, OnePink, OneCyan, OneBlue)

fun LazyListScope.messageItems(list: List<Msg>, glass: Boolean = false) {
    items(list, key = { it.ts.toString() + it.user }) { m ->
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { it / 3 } + scaleIn(initialScale = .96f)) {
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
fun QuickChips(onPick: (String) -> Unit) {
    val chips = listOf("Turn on flashlight", "Open YouTube", "Set an alarm for 7:00", "Turn off Wi-Fi", "What's on my screen?", "Set volume to 40%")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(chips) { c ->
            Text(c, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                    .clickable { onPick(c) }.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

/** Five thin bars that dance while Bixby listens (right side of the bar). */
@Composable
fun Waveform(active: Boolean, modifier: Modifier = Modifier) {
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
            val f = if (active) s.value else .2f + .1f * s.value
            val bh = size.height * f
            drawRoundRect(brush, Offset(i * (bw + gap), (size.height - bh) / 2), Size(bw, bh), CornerRadius(bw / 2))
        }
    }
}

/**
 * The Bixby listening bar: dark pill, rotating blue/purple/pink glowing edge that breathes,
 * orb on the left, live text in the middle, waveform on the right, grab handle on top.
 */
@Composable
fun GlowBar(state: BState, status: String, partial: String, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "glow")
    val active = state != BState.Idle
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 2200 else 7000, easing = LinearEasing)), label = "rot")
    val breathe by t.animateFloat(.6f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br")
    var text by remember { mutableStateOf("") }

    Box(modifier.fillMaxWidth().height(66.dp).drawBehind {
        val w = size.width; val h = size.height; val r = h / 2; val c = center
        val sweep = Brush.sweepGradient(GlowColors, c)
        val reach = maxOf(w, h)
        // a band = the area between two rounded rects (positive = grown outwards, negative = inset)
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
        band(12 * g, 6 * g, .17f * k); band(6 * g, 0f, .38f * k)          // outer glow
        drawRoundRect(BarFill, cornerRadius = CornerRadius(r), size = size) // body
        band(0f, -2.5f * g, 1f)                                              // crisp rim
    }) {
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
                            if (text.isEmpty()) Text("Talk or type to Bixby", color = Color.White.copy(.5f), fontSize = 17.sp)
                            inner()
                        })
                    BState.Listening -> Text(partial.ifBlank { "Listening..." }, color = Color.White, fontSize = 17.sp, maxLines = 1)
                    else -> Text(status.ifBlank { "Working..." }, color = Color.White, fontSize = 17.sp, maxLines = 1)
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

/** Same bar, wired to the engine, for in-app screens. */
@Composable
fun InputBar(onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by BixbyEngine.state.collectAsState()
    val partial by BixbyEngine.partial.collectAsState()
    val status by BixbyEngine.status.collectAsState()
    GlowBar(state, status, partial, onSend, modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 22.dp))
}

/** Assist popup: no heavy sheet, just messages floating above the glowing bar, like One UI Bixby. */
@Composable
fun BixbySheet(onDismiss: () -> Unit) {
    val msgs by BixbyEngine.messages.collectAsState()
    val state by BixbyEngine.state.collectAsState()
    val status by BixbyEngine.status.collectAsState()
    val partial by BixbyEngine.partial.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.lastIndex) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.6f))))
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible, enter = slideInVertically(spring(dampingRatio = .78f, stiffness = 320f)) { it / 2 } + fadeIn(tween(200))) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (msgs.isEmpty()) QuickChips { BixbyEngine.send(it) }
                else LazyColumn(Modifier.heightIn(max = 320.dp), state = list, contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) { messageItems(msgs.takeLast(6), glass = true) }
                GlowBar(state, status, partial, { BixbyEngine.send(it) },
                    Modifier.padding(horizontal = 18.dp, vertical = 22.dp).clickable(enabled = false) {})
            }
        }
    }
}
