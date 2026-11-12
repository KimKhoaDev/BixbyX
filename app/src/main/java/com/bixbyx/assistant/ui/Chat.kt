package com.bixbyx.assistant.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bixbyx.assistant.agent.BState
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.agent.Ctl
import com.bixbyx.assistant.agent.Msg
import com.bixbyx.assistant.agent.Tools
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Share
import com.bixbyx.assistant.glass.LiquidSlider
import com.bixbyx.assistant.glass.LiquidToggle
import com.kyant.backdrop.backdrops.layerBackdrop as glassLayer
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberGlassBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.foundation.shape.CircleShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun runQuick(s: String) { if (s.startsWith("What's on")) BixbyEngine.describeScreen() else BixbyEngine.send(s) }

@Composable
private fun ControlCard(ctl: Ctl) {
    val c = MiuixTheme.colorScheme
    var v by remember(ctl) { mutableIntStateOf(ctl.value) }
    val surface = c.surfaceContainer
    val bd = rememberGlassBackdrop { drawRect(surface); drawContent() }
    // apply a moment after the last change, so dragging a slider does not spam the system
    LaunchedEffect(v) { if (v != ctl.value) { kotlinx.coroutines.delay(250); BixbyEngine.applyControl(ctl.type, v) } }
    Card(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp)) {
        Box {
            Box(Modifier.matchParentSize().glassLayer(bd))
            if (ctl.isSlider) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(ctl.title, Modifier.weight(1f), style = MiuixTheme.textStyles.body1)
                        Text("$v%", color = c.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
                    }
                    Spacer(Modifier.height(10.dp))
                    LiquidSlider({ v.toFloat() }, { v = it.toInt() }, 0f..100f, 1f, bd)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ctl.title, Modifier.weight(1f), style = MiuixTheme.textStyles.body1)
                    LiquidToggle({ v == 1 }, { v = if (it) 1 else 0 }, bd)
                }
            }
        }
    }
}

@Composable
private fun MessageView(m: Msg, last: Boolean) {
    val c = MiuixTheme.colorScheme
    val ctx = LocalContext.current
    if (m.user) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
            Text(m.text, Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                .background(c.primary).padding(horizontal = 14.dp, vertical = 10.dp), color = c.onPrimary, style = MiuixTheme.textStyles.body1)
        }
    } else {
        Column {
            ToolSteps(m.steps, running = false, defaultOpen = false)
            Panel(Modifier.padding(horizontal = 12.dp)) { Markdown(m.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
            m.controls.forEach { ControlCard(it) }
            Row(Modifier.padding(start = 12.dp, top = 2.dp)) {
                IconButton({ Tools.clipboardSet(ctx, m.text); BixbyEngine.notify("Copied") }) { Icon(MiuixIcons.Copy, "Copy", tint = c.onSurfaceVariantActions) }
                IconButton({ Tools.share(ctx, m.text) }) { Icon(MiuixIcons.Share, "Share", tint = c.onSurfaceVariantActions) }
                if (last) IconButton({ BixbyEngine.regenerate() }) { Icon(MiuixIcons.Refresh, "Try again", tint = c.onSurfaceVariantActions) }
            }
        }
    }
}

fun LazyListScope.messageItems(list: List<Msg>) {
    itemsIndexed(list, key = { _, m -> m.ts.toString() + m.user + m.text.length }) { i, m ->
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(shown, enter = fadeIn() + slideInVertically(spring(dampingRatio = .8f, stiffness = 380f)) { it / 2 } + scaleIn(initialScale = .92f)) {
            Box(Modifier.padding(bottom = 8.dp)) { MessageView(m, i == list.lastIndex) }
        }
    }
}

@Composable
fun QuickChips(onPick: (String) -> Unit = ::runQuick) {
    val hour = remember { java.time.LocalTime.now().hour }
    val timed = when (hour) {
        in 5..11 -> listOf("Summarize my notifications", "What's on my calendar today?")
        in 12..17 -> listOf("What's on my calendar today?", "Summarize my notifications")
        else -> listOf("Turn on dark mode", "Set an alarm for 7:00", "Set brightness to 30%")
    }
    val chips = (timed + listOf("What's on my screen?", "Turn on flashlight", "Open YouTube", "Turn off Wi-Fi", "Set volume to 40%")).distinct()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
        itemsIndexed(chips) { i, text ->
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(i * 50L); shown = true }
            AnimatedVisibility(shown, enter = fadeIn() + slideInHorizontally { it / 2 } + scaleIn(initialScale = .85f)) { Pill(text) { onPick(text) } }
        }
    }
}

/** Bars that move while Bixby listens, following your voice level. */
@Composable
fun Waveform(active: Boolean, modifier: Modifier = Modifier) {
    val lv by BixbyEngine.level.collectAsState()
    val t = rememberInfiniteTransition(label = "wave")
    val hs = listOf(520, 780, 430, 690, 560).mapIndexed { i, d ->
        t.animateFloat(.25f, 1f, infiniteRepeatable(tween(d, easing = FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = StartOffset(i * 90)), label = "w$i")
    }
    Canvas(modifier.size(40.dp, 26.dp)) {
        val bw = 4.dp.toPx(); val gap = (size.width - 5 * bw) / 4
        val brush = Brush.verticalGradient(listOf(BixbyCyan, BixbyBlue, BixbyPurple))
        hs.forEachIndexed { i, s ->
            val f = if (active) (.2f + .8f * s.value * (if (lv > .03f) lv.coerceIn(.25f, 1f) else 1f)) else .2f + .1f * s.value
            val bh = size.height * f
            drawRoundRect(brush, Offset(i * (bw + gap), (size.height - bh) / 2), Size(bw, bh), CornerRadius(bw / 2))
        }
    }
}

/** The Bixby bar. It only glows while Bixby is listening or working. */
@Composable
fun GlowBar(state: BState, status: String, partial: String, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = MiuixTheme.colorScheme
    val notice by BixbyEngine.notice.collectAsState()
    val active = state != BState.Idle
    var text by remember { mutableStateOf("") }
    val body = MiuixTheme.textStyles.body1.copy(color = c.onSurface)
    val glass = LocalGlass.current
    val shell: Modifier = if (glass != null) Modifier.glowAura(active)
        .drawBackdrop(glass, { CircleShape }, effects = { vibrancy(); blur(6.dp.toPx()); lens(14.dp.toPx(), 14.dp.toPx()) },
            onDrawSurface = { drawRect(c.surfaceContainer.copy(alpha = .55f)) })
        .glowRim(active)
    else Modifier.glowBar(active, c.surfaceContainer)
    Row(modifier.fillMaxWidth().height(52.dp).then(shell).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BixbyOrb(state, 30.dp, hole = c.surfaceContainer)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            when (state) {
                BState.Idle -> BasicTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = body, cursorBrush = SolidColor(c.primary), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { onSend(text); text = "" } }),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text(notice.ifBlank { "Ask Bixby" }, maxLines = 2, style = if (notice.isBlank()) body else MiuixTheme.textStyles.body2,
                            color = if (notice.isBlank()) c.onSurfaceVariantSummary else c.primary)
                        inner()
                    })
                BState.Listening -> Text(partial.ifBlank { "Listening..." }, maxLines = 1, style = body)
                else -> AnimatedContent(status.ifBlank { "Working" }, transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                    label = "status") { Text(it, maxLines = 1, style = body) }
            }
        }
        when {
            state == BState.Idle && text.isNotBlank() -> IconButton({ onSend(text); text = "" }) { Icon(MiuixIcons.Send, "Send", tint = c.primary) }
            state == BState.Idle -> IconButton({ BixbyEngine.listen() }) { Icon(MiuixIcons.Mic, "Talk", tint = c.primary) }
            state == BState.Listening -> Box(Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { BixbyEngine.stop() }.padding(8.dp)) { Waveform(true) }
            else -> IconButton({ BixbyEngine.stop() }) { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c.onSurface)) }
        }
    }
}

@Composable
fun InputBar(modifier: Modifier = Modifier) {
    val state by BixbyEngine.state.collectAsState()
    val partial by BixbyEngine.partial.collectAsState()
    val status by BixbyEngine.status.collectAsState()
    Box(modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        GlowBar(state, status, partial, { BixbyEngine.send(it) }, Modifier.widthIn(max = 420.dp))
    }
}

/** Text picked in another app, with the usual things to do with it. */
@Composable
fun SelectedText() {
    val text by BixbyEngine.pendingText.collectAsState()
    val t = text ?: return
    val actions = listOf("Summarize", "Explain", "Translate to English", "Reply to this", "Fix grammar", "List the tasks")
    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(t.take(400), maxLines = 5, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(actions) { a -> Pill(a) { BixbyEngine.askAbout(a) } } }
        }
    }
}

/** Assist popup and overlay panel: the timeline and replies sit above the bar. */
@Composable
fun BixbySheet(visible: Boolean, onDismiss: () -> Unit) {
    val msgs by BixbyEngine.messages.collectAsState()
    val state by BixbyEngine.state.collectAsState()
    val steps by BixbyEngine.steps.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size, steps.size) { val n = list.layoutInfo.totalItemsCount; if (n > 0) list.animateScrollToItem(n - 1) }
    val ts = remember { MutableTransitionState(false) }
    ts.targetState = visible
    val scrim by animateFloatAsState(if (visible) 1f else 0f, tween(300), label = "scrim")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f * scrim))))
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(ts,
            enter = slideInVertically(spring(dampingRatio = .78f, stiffness = 320f)) { it / 2 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(260)) { it / 2 } + fadeOut(tween(200))) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().clickable(enabled = false) {}) {
                SelectedText()
                if (msgs.isEmpty() && steps.isEmpty()) QuickChips()
                else LazyColumn(Modifier.heightIn(max = 340.dp), state = list) {
                    messageItems(msgs.takeLast(4))
                    if (steps.isNotEmpty()) item { ToolSteps(steps, running = state != BState.Idle, defaultOpen = true) }
                }
                InputBar()
            }
        }
    }
}
