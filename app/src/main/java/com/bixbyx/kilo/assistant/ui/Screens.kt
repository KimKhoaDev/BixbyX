package com.bixbyx.kilo.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bixbyx.kilo.assistant.agent.*
import com.bixbyx.kilo.assistant.ai.LlmClient
import com.bixbyx.kilo.assistant.ai.Provider
import com.bixbyx.kilo.assistant.service.AgentAccessibilityService
import com.bixbyx.kilo.assistant.service.OverlayService
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val msgs by BixbyEngine.messages.collectAsState()
    val state by BixbyEngine.state.collectAsState()
    val steps by BixbyEngine.steps.collectAsState()
    val status by BixbyEngine.status.collectAsState()
    val chats by BixbyEngine.chats.collectAsState()
    val cur by BixbyEngine.currentId.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size, steps.size) { list.animateScrollToItem((msgs.size + steps.size + 3).coerceAtLeast(0)) }
    val title = chats.firstOrNull { it.id == cur }?.title?.takeIf { it != "New chat" } ?: "Bixby"
    OneUiPage(title, bottomBar = { InputBar({ BixbyEngine.send(it) }) }, state = list, actions = {
        IconButton({ BixbyEngine.newChat() }) { Icon(Icons.Default.Add, "New chat") }
    }) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BixbyOrb(state, 110.dp)
                Spacer(Modifier.height(8.dp))
                AnimatedContent(when (state) { BState.Listening -> "Listeningâ€¦"; BState.Thinking -> status.ifBlank { "Thinkingâ€¦" }
                    BState.Working -> status.ifBlank { "Workingâ€¦" }; else -> "Hi, how can I help?" },
                    transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                    label = "greet") { Text(it, fontSize = 20.sp) }
            }
        }
        if (steps.isNotEmpty()) item { StepsCard(steps, state) }
        if (msgs.isEmpty() && steps.isEmpty()) item { QuickChips() }
        messageItems(msgs.takeLast(40))
    }
}

// ---------------------------------------------------------------- Diagnostics
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var h by remember { mutableStateOf(Diagnostics.read(ctx)) }
    var report by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(5000); h = Diagnostics.read(ctx) } }
    OneUiPage("Diagnostics", onBack) {
        item { Meter("Battery", "${h.battery}%${if (h.charging) " Â· charging" else ""}", h.battery / 100f, h.battery < 20 && !h.charging) }
        item { Meter("Battery temperature", "${"%.1f".format(h.tempC)} Â°C", (h.tempC / 60f).coerceIn(0f, 1f), h.tempC > 42) }
        item { Meter("Storage", "${(h.storageUsed * 100).toInt()}% used Â· ${"%.1f".format(h.storageFreeGb)} GB free", h.storageUsed, h.storageUsed > .9f) }
        item { Meter("Memory", "${(h.ramUsed * 100).toInt()}% used Â· ${"%.1f".format(h.ramFreeGb)} GB free", h.ramUsed, h.ramUsed > .9f) }
        item { Meter("Uptime", "${"%.1f".format(h.uptimeH)} h since restart", (h.uptimeH / 168f).coerceIn(0f, 1f), h.uptimeH > 168) }
        item { Button({ busy = true; scope.launch { report = runCatching { BixbyEngine.diagnose(h) }.getOrElse { "âš  ${it.message}" }; busy = false } },
            Modifier.fillMaxWidth().height(52.dp)) { Text(if (busy) "Analyzingâ€¦" else "Ask Bixby to analyze") } }
        if (report.isNotEmpty()) item { OneCard { Text(report, fontSize = 15.sp) } }
    }
}

@Composable
private fun Meter(title: String, value: String, frac: Float, warn: Boolean) {
    val anim by androidx.compose.animation.core.animateFloatAsState(frac, androidx.compose.animation.core.tween(900), label = "m")
    OneCard {
        Row { Text(title, Modifier.weight(1f), fontSize = 16.sp); Text(value, fontSize = 14.sp, color = if (warn) androidx.compose.ui.graphics.Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurface.copy(.6f)) }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator({ anim }, Modifier.fillMaxWidth().height(8.dp), color = if (warn) androidx.compose.ui.graphics.Color(0xFFFF3B30) else OneBlue,
            trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- Interpreter
@Composable
fun InterpreterScreen(onBack: () -> Unit) {
    val prefs = BixbyEngine.prefs
    var input by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf(prefs.targetLang) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val langs = listOf("English", "Vietnamese", "Chinese", "Japanese", "Korean", "Spanish", "French", "German", "Hindi", "Arabic", "Russian")
    fun go() { if (input.isBlank()) return; busy = true; scope.launch {
        out = runCatching { BixbyEngine.translate(input, lang) }.getOrElse { "âš  ${it.message}" }; busy = false } }
    OneUiPage("Interpreter", onBack) {
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(langs) { l ->
            FilterChip(lang == l, { lang = l; prefs.targetLang = l }, { Text(l) }) } } }
        item { OneCard {
            OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(), label = { Text("Speak or type") }, minLines = 3)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ BixbyEngine.listenInto { input = it; go() } }) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text("Speak") }
                Button(::go) { Text(if (busy) "Translatingâ€¦" else "Translate to $lang") }
            }
        } }
        if (out.isNotEmpty()) item { OneCard {
            Text(out, fontSize = 20.sp)
            TextButton({ BixbyEngine.speak(out) }) { Icon(Icons.Default.VolumeUp, null); Spacer(Modifier.width(6.dp)); Text("Play") }
        } }
    }
}

