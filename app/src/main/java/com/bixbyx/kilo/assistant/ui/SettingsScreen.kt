package com.bixbyx.kilo.assistant.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon as GIcon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bixbyx.kilo.assistant.R
import com.bixbyx.kilo.assistant.agent.BixbyEngine
import com.bixbyx.kilo.assistant.agent.ShizukuShell
import com.bixbyx.kilo.assistant.ai.LlmClient
import com.bixbyx.kilo.assistant.ai.Provider
import com.bixbyx.kilo.assistant.service.AgentAccessibilityService
import com.bixbyx.kilo.assistant.service.BixbyTile
import com.bixbyx.kilo.assistant.service.OverlayService
import kotlinx.coroutines.launch

private fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
private fun go(ctx: Context, i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

private fun isDefaultAssistant(ctx: Context): Boolean {
    val cr = ctx.contentResolver
    val a = runCatching { Settings.Secure.getString(cr, "assistant") }.getOrNull().orEmpty()
    val v = runCatching { Settings.Secure.getString(cr, "voice_interaction_service") }.getOrNull().orEmpty()
    return a.startsWith(ctx.packageName) || v.startsWith(ctx.packageName)
}

private fun openXiaomi(ctx: Context) {
    val tries = listOf(
        Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        Intent("miui.intent.action.APP_PERM_EDITOR").setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity").putExtra("extra_pkgname", ctx.packageName))
    for (i in tries) if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    ctx.startActivity(appDetails(ctx))
}

private fun addTile(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 33) {
        runCatching {
            ctx.getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(ctx, BixbyTile::class.java), "Bixby", GIcon.createWithResource(ctx, R.drawable.ic_stat_bixby), ctx.mainExecutor) { }
        }
    } else Toast.makeText(ctx, "Swipe down twice, tap the pencil and drag the Bixby tile in", Toast.LENGTH_LONG).show()
}

@Composable
private fun Choice(label: String, options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    Text(label, Modifier.padding(top = 10.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (k, l) -> FilterChip(value == k, { onPick(k) }, { Text(l) }) }
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = BixbyEngine.prefs
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val scope = rememberCoroutineScope()

    var provider by remember { mutableStateOf(prefs.provider) }
    var apiKey by remember(provider) { mutableStateOf(prefs.key(provider)) }
    var model by remember(provider) { mutableStateOf(prefs.model(provider)) }
    var url by remember { mutableStateOf(prefs.customUrl) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var testMsg by remember { mutableStateOf("") }

    var fishKey by remember { mutableStateOf(prefs.fishKey) }
    var fishVoice by remember { mutableStateOf(prefs.fishVoice) }
    var tts by remember { mutableStateOf(prefs.ttsEngine) }
    var stt by remember { mutableStateOf(prefs.sttEngine) }

    var orb by remember { mutableStateOf(prefs.orbEnabled) }
    var gesture by remember { mutableStateOf(prefs.gestureWake) }
    var speak by remember { mutableStateOf(prefs.speak) }
    var narrate by remember { mutableStateOf(prefs.narrate) }
    var describe by remember { mutableStateOf(prefs.autoDescribe) }
    var vol by remember { mutableStateOf(prefs.volumeCombo) }
    var darkMode by remember { mutableStateOf(prefs.darkMode) }

    fun saveAi() { prefs.provider = provider; prefs.setKey(provider, apiKey); prefs.setModel(provider, model); prefs.customUrl = url }
    fun saveVoice() { prefs.fishKey = fishKey; prefs.fishVoice = fishVoice; prefs.ttsEngine = tts; prefs.sttEngine = stt }
    fun saveTheme() { prefs.darkMode = darkMode }

    OneUITheme(darkMode = prefs.darkMode) {
        OneUiPage("Settings") {
        // ------------------------------------------------ AI provider
        item { Stagger(0) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("AI provider")
            OneCard {
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
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ saveAi(); scope.launch {
                        models = runCatching { LlmClient.models(prefs) }.getOrDefault(emptyList())
                        testMsg = if (models.isEmpty()) "Couldn't list models (check key / URL)" else "${models.size} models found" } }) { Text("Fetch models") }
                    Button({ saveAi(); testMsg = "Testingâ€¦"; scope.launch { testMsg = runCatching {
                        "âœ“ " + LlmClient.chat(prefs, listOf("user" to "Reply with the word OK")).take(40) }.getOrElse { "âœ— ${it.message}" } } }) { Text("Save & test") }
                }
                if (testMsg.isNotEmpty()) Text(testMsg, Modifier.padding(top = 6.dp), fontSize = 13.sp)
                Text("Kilo AI: key from app.kilo.ai Â· OpenRouter: openrouter.ai/keys (free models end in :free). Slow free models? Pick a faster one or raise the timeout.",
                    Modifier.padding(top = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
            }
        } } }

        // ------------------------------------------------ voice
        item { Stagger(1) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Voice Â· Fish Audio")
            OneCard {
                Text("Natural speech and reliable voice recognition. Without a key Bixby uses your phone's own engines, which many Xiaomi phones don't have.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                OutlinedTextField(fishKey, { fishKey = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Fish Audio API key") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(fishVoice, { fishVoice = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Voice model ID (optional)") }, singleLine = true)
                val opts = listOf("auto" to "Auto", "fish" to "Fish Audio", "system" to "Phone")
                Choice("Voice (text to speech)", opts, tts) { tts = it }
                Choice("Speech recognition", opts, stt) { stt = it }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ saveVoice(); BixbyEngine.notify("Saved") }) { Text("Save") }
                    OutlinedButton({ saveVoice(); BixbyEngine.testVoice() }) { Text("Test voice") }
                }
                Text("Get a key and voice IDs at fish.audio", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
            }
        } } }

        // ------------------------------------------------ assistant
        item { Stagger(2) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Summon Bixby")
            key(tick) { OneGroup {
                OneRow("Default digital assistant", if (isDefaultAssistant(ctx)) "Bixby is your assistant" else "Tap to choose Bixby", Icons.Default.AutoAwesome, OneBlue,
                    { go(ctx, Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }) { StatusText(isDefaultAssistant(ctx)) }
                OneDivider()
                OneRow("Power button long-press", "HyperOS may send it to Xiao AI. Open Settings â†’ Additional settings â†’ Button shortcuts and pick the assistant.",
                    Icons.Default.PowerSettingsNew, OnePink, {
                        Toast.makeText(ctx, "Additional settings â†’ Button shortcuts â†’ long-press power â†’ Assistant", Toast.LENGTH_LONG).show()
                        go(ctx, Intent(Settings.ACTION_SETTINGS)) }) { Chevron() }
                OneDivider()
                OneRow("Quick Settings tile", "Add a Bixby tile â€” works on every phone", Icons.Default.Apps, OneCyan, { addTile(ctx) }) { Chevron() }
                OneDivider()
                SwitchRow("Volume keys shortcut", "Press Volume Up + Down together (needs screen control)", Icons.Default.VolumeUp, OneOrange, vol) { vol = it; prefs.volumeCombo = it }
            } }
        } } }

        // ------------------------------------------------ permissions
        item { Stagger(3) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Permissions")
            key(tick) { OneGroup {
                OneRow("Screen control", "Accessibility service: tap, type, scroll, read the screen", Icons.Default.Accessibility, OneBlue,
                    { go(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { StatusText(AgentAccessibilityService.running()) }
                OneDivider()
                OneRow("Display over other apps", "Floating icon, step bar and panel", Icons.Default.Layers, OnePurple,
                    { go(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }) { StatusText(Settings.canDrawOverlays(ctx)) }
                OneDivider()
                OneRow("Microphone", "Voice commands", Icons.Default.Mic, OneRed, { go(ctx, appDetails(ctx)) }) {
                    StatusText(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
                OneDivider()
                OneRow("Shizuku", if (ShizukuShell.running()) "Running Â· tap to grant access" else "Install & start Shizuku for system commands", Icons.Default.Terminal, OneGreen, {
                    if (ShizukuShell.running()) { ShizukuShell.request(); tick++ }
                    else go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) }) { StatusText(ShizukuShell.granted()) }
                OneDivider()
                OneRow("Modify system settings", "Change brightness without Shizuku", Icons.Default.Tune, OneCyan,
                    { go(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }) { StatusText(Settings.System.canWrite(ctx)) }
                OneDivider()
                OneRow("Xiaomi: Autostart & battery", "Autostart ON, battery saver â€œNo restrictionsâ€, allow pop-ups", Icons.Default.BatteryChargingFull, OneOrange,
                    { openXiaomi(ctx) }) { Chevron() }
            } }
        } } }

        // ------------------------------------------------ features
        item { Stagger(4) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Bixby features")
            OneGroup {
                SwitchRow("Floating Bixby icon", "Animated icon over every app", Icons.Default.BubbleChart, OneBlue, orb) {
                    orb = it; prefs.orbEnabled = it; if (it) OverlayService.start(ctx) else OverlayService.stop(ctx) }
                OneDivider()
                SwitchRow("Gesture wake-up (experimental)", "Raise the phone to your mouth to talk. Needs the icon on.", Icons.Default.PanTool, OnePink, gesture) {
                    gesture = it; prefs.gestureWake = it; if (orb) { OverlayService.stop(ctx); OverlayService.start(ctx) } }
                OneDivider()
                SwitchRow("Describe the screen", "Say what's on screen when you tap the icon or summon Bixby", Icons.Default.RecordVoiceOver, OnePurple, describe) { describe = it; prefs.autoDescribe = it }
                OneDivider()
                SwitchRow("Speak replies", "Bixby reads answers aloud", Icons.Default.VolumeUp, OneGreen, speak) { speak = it; prefs.speak = it }
                OneDivider()
                SwitchRow("Narrate steps", "Say each step out loud while Bixby works", Icons.Default.GraphicEq, OneOrange, narrate) { narrate = it; prefs.narrate = it }
            }
        } } }
        // ------------------------------------------------ theme
        item { Stagger(5) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Theme")
            OneCard {
                Choice("Dark / light mode", listOf("system" to "System", "dark" to "Dark", "light" to "Light"), darkMode) { darkMode = it; saveTheme() }
            }
        } } }
    }
}


