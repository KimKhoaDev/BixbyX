package com.bixbyx.assistant.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bixbyx.assistant.agent.BState
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.agent.Msg

fun LazyListScope.messageItems(list: List<Msg>) {
    items(list, key = { it.ts.toString() + it.user }) { m ->
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { it / 3 } + scaleIn(initialScale = .96f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.user) Arrangement.End else Arrangement.Start) {
                Text(m.text, fontSize = 16.sp,
                    color = if (m.user) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(22.dp, 22.dp, if (m.user) 6.dp else 22.dp, if (m.user) 22.dp else 6.dp))
                        .background(if (m.user) OneBlue else MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 11.dp))
            }
        }
    }
}

@Composable
fun QuickChips(onPick: (String) -> Unit) {
    val chips = listOf("Turn on flashlight", "Open YouTube", "Set an alarm for 7:00", "Turn off Wi-Fi", "What's on my screen?", "Set volume to 40%")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(chips) { c ->
            Text(c, fontSize = 14.sp, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                .clickable { onPick(c) }.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
fun InputBar(onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by BixbyEngine.state.collectAsState()
    val partial by BixbyEngine.partial.collectAsState()
    var text by remember { mutableStateOf("") }
    Row(modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.weight(1f), shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            TextField(if (state == BState.Listening && partial.isNotEmpty()) partial else text, { text = it },
                Modifier.fillMaxWidth(), placeholder = { Text(if (state == BState.Listening) "Listening…" else "Talk or type to Bixby") },
                maxLines = 3, colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
        }
        Spacer(Modifier.width(10.dp))
        val busy = state == BState.Working || state == BState.Thinking
        FilledIconButton({ when {
            busy -> BixbyEngine.stop()
            text.isNotBlank() -> { onSend(text); text = "" }
            else -> BixbyEngine.listen() } }, Modifier.size(52.dp)) {
            Icon(when { busy -> Icons.Default.Stop; text.isNotBlank() -> Icons.Default.ArrowUpward; else -> Icons.Default.Mic }, null)
        }
    }
}

/** Bottom sheet used by the overlay (and the assist popup): glassy rounded panel with chat + input. */
@Composable
fun BixbySheet(onDismiss: () -> Unit) {
    val msgs by BixbyEngine.messages.collectAsState()
    val state by BixbyEngine.state.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.lastIndex) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)).clickable(
        indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible, enter = slideInVertically(spring(dampingRatio = .8f, stiffness = 300f)) { it } + fadeIn()) {
            Surface(Modifier.fillMaxWidth().heightIn(max = 560.dp).clickable(enabled = false) {},
                shape = RoundedCornerShape(32.dp, 32.dp, 0.dp, 0.dp), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.navigationBarsPadding()) {
                    Box(Modifier.fillMaxWidth().padding(top = 10.dp), Alignment.Center) {
                        Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(Color.Gray.copy(.4f)))
                    }
                    Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BixbyOrb(state, 44.dp); Spacer(Modifier.width(12.dp))
                        Text(if (state == BState.Listening) "Listening…" else "Hi, how can I help?", fontSize = 18.sp)
                    }
                    if (msgs.isEmpty()) QuickChips { BixbyEngine.send(it) }
                    LazyColumn(Modifier.weight(1f, fill = false), state = list, contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) { messageItems(msgs.takeLast(12)) }
                    InputBar({ BixbyEngine.send(it) })
                }
            }
        }
    }
}
