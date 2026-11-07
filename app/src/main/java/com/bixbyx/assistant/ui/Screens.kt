package com.bixbyx.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import com.bixbyx.assistant.agent.*
import com.bixbyx.assistant.ai.LlmClient
import com.bixbyx.assistant.ai.Provider
import com.bixbyx.assistant.service.AgentAccessibilityService
import com.bixbyx.assistant.service.OverlayService
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(openSettings: () -> Unit, openDiag: () -> Unit, openInterp: () -> Unit) {
    val msgs by BixbyEngine.messages.collectAsState()
    val state by BixbyEngine.state.collectAsState()
    val status by BixbyEngine.status.collectAsState()
    OneUiPage("Bixby", bottomBar = { Column(Modifier.imePadding()) { InputBar({ BixbyEngine.send(it) }) } }) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BixbyOrb(state, 150.dp)
                Spacer(Modifier.height(8.dp))
                Text(when (state) { BState.Listening -> "Listening…"; BState.Thinking -> "Thinking…"
                    BState.Working -> status.ifBlank { "Working…" }; else -> "Hi, how can I help?" }, fontSize = 20.sp)
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureTile("Diagnostics", Icons.Default.MonitorHeart, Modifier.weight(1f), openDiag)
            FeatureTile("Interpreter", Icons.Default.Translate, Modifier.weight(1f), openInterp)
            FeatureTile("Settings", Icons.Default.Settings, Modifier.weight(1f), openSettings)
        } }
        if (msgs.isEmpty()) item { QuickChips { BixbyEngine.send(it) } }
        messageItems(msgs.takeLast(30))
        if (msgs.isNotEmpty()) item { TextButton({ BixbyEngine.clear() }) { Text("Clear conversation history") } }
    }
}

@Composable
private fun FeatureTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, m: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = m, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = OneBlue); Spacer(Modifier.height(6.dp)); Text(label, fontSize = 13.sp)
        }
    }
}

// ---------------------------------------------------------------- Settings
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = BixbyEngine.prefs
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    var provider by remember { mutableStateOf(prefs.provider) }
    var apiKey by remember(provider) { mutableStateOf(prefs.key(provider)) }
    var model by remember(provider) { mutableStateOf(prefs.model(provider)) }
    var url by remember { mutableStateOf(prefs.customUrl) }
    var orb by remember { mutableStateOf(prefs.orbEnabled) }
    var gesture by remember { mutableStateOf(prefs.gestureWake) }
    var speak by remember { mutableStateOf(prefs.speak) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var testMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun save() { prefs.provider = provider; prefs.setKey(provider, apiKey); prefs.setModel(provider, model); prefs.customUrl = url }

    OneUiPage("Settings", onBack) {
        item { SectionLabel("AI provider") }
        item { OneCard {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Provider.values().forEach { p -> FilterChip(provider == p, { provider = p }, { Text(p.label) }) }
            }
            if (provider == Provider.CUSTOM) OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Base URL (OpenAI-compatible)") }, singleLine = true)
            OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("${provider.label} API key") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true)
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Model") }, singleLine = true)
            if (models.isNotEmpty()) LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(models.take(40)) { m -> AssistChip({ model = m }, { Text(m, maxLines = 1) }) }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ save(); scope.launch { models = runCatching { LlmClient.models(prefs) }.getOrDefault(emptyList())
                    testMsg = if (models.isEmpty()) "Couldn't list models (check key / URL)" else "${models.size} models found" } }) { Text("Fetch models") }
                Button({ save(); testMsg = "Testing…"; scope.launch { testMsg = runCatching {
                    "✓ " + LlmClient.chat(prefs, listOf("user" to "Reply with the word OK")).take(40) }.getOrElse { "✗ ${it.message}" } } }) { Text("Save & test") }
            }
            if (testMsg.isNotEmpty()) Text(testMsg, Modifier.padding(top = 6.dp), fontSize = 13.sp)
            Text("Kilo AI: key from app.kilo.ai · OpenRouter: openrouter.ai/keys (free models end in :free)", Modifier.padding(top = 6.dp),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
        } }

        item { SectionLabel("Permissions") }
        item { key(tick) { OneCard {
            StatusRow("Screen control", "Accessibility service lets Bixby tap, type and scroll", AgentAccessibilityService.running(), "Enable") {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            StatusRow("Display over other apps", "Floating orb and the Bixby panel", Settings.canDrawOverlays(ctx), "Allow") {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            StatusRow("Microphone", "Voice commands", ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED, "Allow") {
                ctx.startActivity(appDetails(ctx)) }
            StatusRow("Shizuku", if (ShizukuShell.running()) "Running · grant BixbyX access" else "Install & start Shizuku (adb or wireless debugging)", ShizukuShell.granted(),
                if (ShizukuShell.running()) "Grant" else "Get") {
                if (ShizukuShell.running()) { ShizukuShell.request(); tick++ }
                else ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            StatusRow("Modify system settings", "Brightness without Shizuku", Settings.System.canWrite(ctx), "Allow") {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            StatusRow("Xiaomi: Autostart & battery", "Set Autostart ON and Battery saver to 'No restrictions' so HyperOS doesn't kill Bixby", false, "Open") { openXiaomi(ctx) }
        } } }

        item { SectionLabel("Bixby features") }
        item { OneCard {
            SwitchRow("Floating Bixby orb", "Always-available orb over every app", orb) { orb = it; prefs.orbEnabled = it
                if (it) OverlayService.start(ctx) else OverlayService.stop(ctx) }
            SwitchRow("Gesture wake-up (experimental)", "Raise the phone to your mouth to talk, no hotword. Needs the orb on.", gesture) {
                gesture = it; prefs.gestureWake = it; if (orb) { OverlayService.stop(ctx); OverlayService.start(ctx) } }
            SwitchRow("Speak replies", "Bixby reads answers aloud", speak) { speak = it; prefs.speak = it }
        } }
        item { SectionLabel("Make Bixby your assistant") }
        item { OneCard {
            Text("Settings → Apps → Default apps → Digital assistant app → Bixby. Then long-press the power button (or use the assist gesture) to summon it. You can also add the Bixby widget from the home screen widgets.", fontSize = 14.sp)
            TextButton({ ctx.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open assistant settings") }
        } }
    }
}

private fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun openXiaomi(ctx: Context) {
    val tries = listOf(
        Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        Intent("miui.intent.action.APP_PERM_EDITOR").setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity").putExtra("extra_pkgname", ctx.packageName))
    for (i in tries) if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    ctx.startActivity(appDetails(ctx))
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
        item { Meter("Battery", "${h.battery}%${if (h.charging) " · charging" else ""}", h.battery / 100f, h.battery < 20 && !h.charging) }
        item { Meter("Battery temperature", "${"%.1f".format(h.tempC)} °C", (h.tempC / 60f).coerceIn(0f, 1f), h.tempC > 42) }
        item { Meter("Storage", "${(h.storageUsed * 100).toInt()}% used · ${"%.1f".format(h.storageFreeGb)} GB free", h.storageUsed, h.storageUsed > .9f) }
        item { Meter("Memory", "${(h.ramUsed * 100).toInt()}% used · ${"%.1f".format(h.ramFreeGb)} GB free", h.ramUsed, h.ramUsed > .9f) }
        item { Meter("Uptime", "${"%.1f".format(h.uptimeH)} h since restart", (h.uptimeH / 168f).coerceIn(0f, 1f), h.uptimeH > 168) }
        item { Button({ busy = true; scope.launch { report = runCatching { BixbyEngine.diagnose(h) }.getOrElse { "⚠ ${it.message}" }; busy = false } },
            Modifier.fillMaxWidth().height(52.dp)) { Text(if (busy) "Analyzing…" else "Ask Bixby to analyze") } }
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
        out = runCatching { BixbyEngine.translate(input, lang) }.getOrElse { "⚠ ${it.message}" }; busy = false } }
    OneUiPage("Interpreter", onBack) {
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(langs) { l ->
            FilterChip(lang == l, { lang = l; prefs.targetLang = l }, { Text(l) }) } } }
        item { OneCard {
            OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(), label = { Text("Speak or type") }, minLines = 3)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ BixbyEngine.listenInto { input = it; go() } }) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text("Speak") }
                Button(::go) { Text(if (busy) "Translating…" else "Translate to $lang") }
            }
        } }
        if (out.isNotEmpty()) item { OneCard {
            Text(out, fontSize = 20.sp)
            TextButton({ BixbyEngine.speak(out) }) { Icon(Icons.Default.VolumeUp, null); Spacer(Modifier.width(6.dp)); Text("Play") }
        } }
    }
}
